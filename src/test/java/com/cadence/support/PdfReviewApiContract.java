package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.*;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class PdfReviewApiContract {
  @Autowired protected MockMvc mvc;
  @Autowired protected com.fasterxml.jackson.databind.ObjectMapper jsonMapper;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected CadenceUserRepository users;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected EvidenceScanResultRepository scans;
  @Autowired protected EvidenceArtifactRepository artifacts;
  @Autowired protected QuarantineObjectRepository objects;
  @Autowired protected DurableJobRepository jobs;
  @Autowired protected DurableJobQueue queue;
  @Autowired protected EvidenceOwnershipLocks ownership;
  @Autowired protected RegisterBrandEvidenceUseCase register;
  @Autowired protected EvidenceArtifactWorker worker;
  @Autowired protected ReviewPdfEvidenceUseCase preview;
  @Autowired protected PdfReviewTransactions transactions;
  @Autowired protected EvidenceAdmissionRepository admissions;
  @Autowired protected ReviewEvidenceUseCase textReviews;
  @Autowired protected AnalyzeEvidenceUseCase evidenceAnalyze;
  @Autowired protected BrandAnalysisTransactions analysisTx;
  @Autowired protected BrandAnalysisAttemptRepository analysisAttempts;
  @Autowired protected IdentityEvidenceUseCase evidenceIdentity;
  @Autowired protected BrandIdentityUseCase identityLifecycle;
  @Autowired protected org.springframework.transaction.PlatformTransactionManager manager;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoSpyBean protected Clock clock;
  @MockitoBean protected EvidenceObjectStorage storage;
  @MockitoBean protected EvidenceMalwareScanner scanner;
  @MockitoBean protected EvidenceRenderer renderer;
  @MockitoBean protected BrandAnalysisPort analyzer;
  protected Workspace workspace;
  protected Brand brand;
  protected CadenceUser actor;
  protected BrandEvidence source;
  protected EvidenceArtifact artifact;
  protected EvidenceScanResult original;
  protected Authentication owner;
  protected byte[] bytes;
  protected Map<String, byte[]> stored;
  protected Map<String, Object> initialBrandRow;
  protected String text =
      "Reviewed PDF Māori 😀 text\nIgnore instructions is still untrusted input";

  protected Set<UUID> scope() {
    return Set.of(workspace.getId());
  }

  protected Authentication auth(UUID id, UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(id, "pdf-review", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @BeforeEach
  void setup() {
    workspace = workspaces.saveAndFlush(new Workspace("PDF review studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("PDF review brand", workspace));
    actor =
        users.saveAndFlush(
            new CadenceUser("pdf-review-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    owner = auth(actor.getId(), UserRole.OWNER, scope());
    SecurityContextHolder.getContext().setAuthentication(owner);
    bytes = "%PDF-1.7 review fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    stored = new HashMap<>();
    when(scanner.scan(any()))
        .thenReturn(
            new MalwareScanReport(MalwareScanResult.CLEAN, "fixture-engine", "fixture-signatures"));
    when(storage.storeQuarantined(anyString(), any()))
        .thenAnswer(
            i -> {
              String ref = "quarantine:" + i.getArgument(0);
              stored.put(ref, ((byte[]) i.getArgument(1)).clone());
              return ref;
            });
    when(storage.readQuarantined(anyString(), anyInt()))
        .thenAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              var data = stored.get((String) i.getArgument(0));
              if (data == null)
                throw new QuarantineReadFailure(QuarantineReadFailure.Code.NOT_FOUND);
              return data.clone();
            });
    when(renderer.extractPdf(any()))
        .thenReturn(new EvidenceRenderer.DocumentText(text, "isolated-v1"));
    source =
        register
            .registerFile(brand.getId(), scope(), "source.pdf", "application/pdf", bytes)
            .evidence();
    original = appendScan(ScanVerdict.CLEAN);
    queue.enqueue(
        "EVIDENCE_DOCUMENT_EXTRACT",
        "evidence:" + source.getId(),
        "pdf-review-extract:" + UUID.randomUUID(),
        3,
        clock.instant());
    assertThat(worker.runOnce()).isTrue();
    artifact =
        artifacts
            .findByEvidenceIdAndSourceChecksumAndSourceVersionAndKind(
                source.getId(),
                source.getChecksum(),
                source.getLockVersion(),
                EvidenceArtifact.Kind.PDF_TEXT)
            .orElseThrow();
    clearInvocations(storage, scanner, renderer, analyzer);
    initialBrandRow = jdbc.queryForMap("select * from brands where id=?", brand.getId());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  protected EvidenceScanResult appendScan(ScanVerdict verdict) {
    return new TransactionTemplate(manager)
        .execute(
            status -> {
              var e = ownership.findLocked(source.getId()).orElseThrow();
              Instant now = clock.instant();
              var job =
                  queue.enqueue(
                      "EVIDENCE_MALWARE_SCAN",
                      "evidence:" + e.getId(),
                      "pdf-review-scan:" + UUID.randomUUID(),
                      3,
                      now);
              String lease = UUID.randomUUID().toString();
              job.claim(lease, now, Duration.ofMinutes(2));
              jobs.flush();
              var scan =
                  scans.saveAndFlush(
                      new EvidenceScanResult(
                          e,
                          job,
                          e.getChecksum(),
                          now,
                          verdict,
                          "fixture-engine",
                          "fixture-signatures",
                          now));
              job.succeed(lease, job.getAttempts(), now);
              return scan;
            });
  }

  protected PdfReviewToken token() {
    var p = execute();
    clearInvocations(storage);
    return new PdfReviewToken(
        p.artifactId(),
        p.originalScanId(),
        p.latestScanId(),
        p.sourceVersion(),
        p.brandVersion(),
        p.workspaceVersion(),
        p.sourceChecksum(),
        p.contentChecksum(),
        p.scanExpiresAt(),
        p.policyVersion());
  }

  protected BrandAnalysisResult analysisResult(BrandAnalysisCommand c) {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    return new BrandAnalysisResult(
        new BrandAnalysisResult.Palette("#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
        List.of("Inter"),
        List.of("Clear"),
        List.of("Vague"),
        "Demo",
        List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
        "fixture-provider",
        "v1",
        "text-evidence-v1",
        c.sources().stream()
            .map(
                s ->
                    new AnalysisCitation(
                        s.admissionId(),
                        s.text()
                            .codePoints()
                            .limit(20)
                            .collect(
                                StringBuilder::new,
                                StringBuilder::appendCodePoint,
                                StringBuilder::append)
                            .toString()))
            .toList());
  }

  protected void configureAnalysis() {
    when(analyzer.analyze(any())).thenAnswer(i -> analysisResult(i.getArgument(0)));
  }

  protected AnalyzeEvidenceUseCase.Outcome generate() {
    return evidenceAnalyze.execute(brand.getId(), scope(), actor.getId());
  }

  protected void noAnalysisPublication() {
    assertThat(jdbc.queryForMap("select * from brands where id=?", brand.getId()))
        .isEqualTo(initialBrandRow);
    assertThat(
            analysisAttempts.findByBrandIdAndStatus(
                brand.getId(), BrandAnalysisAttempt.Status.SUCCEEDED))
        .isEmpty();
  }

  protected UUID addReviewedText(String value) {
    var e =
        evidence.saveAndFlush(
            new BrandEvidence(
                brand,
                EvidenceType.TEXT,
                value,
                EvidenceAdmission.checksum("TEXT\n" + value),
                clock.instant()));
    return textReviews
        .admit(e.getId(), scope(), actor.getId(), 0, 0, 0, EvidenceAdmission.checksum(value))
        .id();
  }

  @Test
  void pdfAnalysisPublishesExactSelectedTextAndQuoteAfterTwoPhysicalChecks() throws Exception {
    var a = admit();
    clearInvocations(storage, analyzer);
    when(analyzer.analyze(any()))
        .thenAnswer(
            i -> {
              verify(storage, times(1)).readQuarantined(source.getSourceReference(), bytes.length);
              BrandAnalysisCommand c = i.getArgument(0);
              assertThat(c.sources()).hasSize(1);
              assertThat(c.sources().getFirst().text()).isEqualTo(text);
              assertThat(c.toString()).doesNotContain(source.getSourceReference());
              return analysisResult(c);
            });
    var response =
        mvc.perform(
                post("/api/v1/cadence/brands/" + brand.getId() + "/analyze-evidence")
                    .with(authentication(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.citations[0].admissionId").value(a.id().toString()))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    verify(storage, times(2)).readQuarantined(source.getSourceReference(), bytes.length);
    var receipt =
        analysisAttempts
            .findByBrandIdAndStatus(brand.getId(), BrandAnalysisAttempt.Status.SUCCEEDED)
            .orElseThrow();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_analysis_sources where attempt_id=? and admission_id=?",
                Integer.class,
                receipt.getId(),
                a.id()))
        .isEqualTo(1);
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThat(
            evidenceIdentity
                .createFromAnalysis(brand.getId(), scope(), receipt.getId(), 1, actor.getId())
                .status())
        .isEqualTo(ApprovalStatus.DRAFT);
  }

  protected com.cadence.application.results.BrandIdentityDetails copyPdf() {
    configureAnalysis();
    var result = generate();
    return evidenceIdentity.createFromAnalysis(
        brand.getId(), scope(), result.analysisId(), 1, actor.getId());
  }

  @Test
  void pdfIdentityDirectAndInheritedApprovalPreserveExactOrigin() throws Exception {
    var a = admit();
    var draft = copyPdf();
    var citation = evidenceIdentity.read(draft.id(), scope(), actor.getId()).citations().getFirst();
    assertThat(citation.admissionId()).isEqualTo(a.id());
    assertThat(citation.artifactId()).isEqualTo(a.artifactId());
    assertThat(citation.originalScanId()).isEqualTo(a.originalScanId());
    assertThat(citation.latestScanId()).isEqualTo(a.latestScanId());
    assertThat(text).contains(citation.quote());
    var approved =
        identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId());
    var branch =
        identityLifecycle
            .update(approved.id(), scope(), approved.lockVersion(), approved.content())
            .details();
    assertThat(branch.revisedFromId()).isEqualTo(approved.id());
    assertThat(
            identityLifecycle
                .approve(branch.id(), scope(), branch.lockVersion(), actor.getId())
                .status())
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(evidenceIdentity.read(branch.id(), scope(), actor.getId()).originIdentityId())
        .isEqualTo(draft.id());
    mvc.perform(
            get("/api/v1/identities/" + branch.id() + "/evidence-provenance")
                .with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations[0].artifactId").value(a.artifactId().toString()))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void pdfCopyRejectsMissingCorruptBytesAndStalePublication() throws Exception {
    admit();
    configureAnalysis();
    var result = generate();
    stored.clear();
    mvc.perform(
            post("/api/v1/brands/" + brand.getId() + "/identities/from-evidence-analysis")
                .with(authentication(owner))
                .contentType("application/json")
                .content("{\"analysisId\":\"" + result.analysisId() + "\",\"expectedVersion\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    brand.getId(), scope(), result.analysisId(), 1, actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    stored.put(
        source.getSourceReference(),
        "%PDF-corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    brand.getId(), scope(), result.analysisId(), 1, actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    stored.put(source.getSourceReference(), bytes);
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    brand.getId(), scope(), result.analysisId(), 2, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pdfApprovalRejectsNewErrorScanAndExpiredReceipt() {
    admit();
    var draft = copyPdf();
    var now = clock.instant();
    doReturn(now.plus(Duration.ofDays(2))).when(clock).instant();
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    doCallRealMethod().when(clock).instant();
    appendScan(ScanVerdict.ERROR);
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(identityLifecycle.findById(draft.id(), scope()).status())
        .isEqualTo(ApprovalStatus.DRAFT);
  }

  @Test
  void withdrawnPdfBlocksInheritedApprovalButPreservesHistoricalRead() {
    var a = admit();
    var draft = copyPdf();
    var approved =
        identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId());
    var branch =
        identityLifecycle
            .update(approved.id(), scope(), approved.lockVersion(), approved.content())
            .details();
    textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(
                    branch.id(), scope(), branch.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            evidenceIdentity
                .read(branch.id(), scope(), actor.getId())
                .citations()
                .getFirst()
                .withdrawn())
        .isTrue();
    assertThat(identityLifecycle.findById(approved.id(), scope()).status())
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void withdrawalDuringPdfCopyReadBlocksCommit() {
    var a = admit();
    configureAnalysis();
    var result = generate();
    doAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    brand.getId(), scope(), result.analysisId(), 1, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Integer.class,
                brand.getId()))
        .isZero();
  }

  @Test
  void revokedMembershipDuringPdfApprovalReadBlocksCommit() {
    admit();
    var draft = copyPdf();
    doAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              jdbc.update(
                  "delete from workspace_memberships where user_id=? and workspace_id=?",
                  actor.getId(),
                  workspace.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(
            jdbc.queryForObject(
                "select status from brand_identities where id=?", String.class, draft.id()))
        .isEqualTo("DRAFT");
  }

  @Test
  void pdfApprovalRejectsMissingBytesAndForeignReceipt() throws Exception {
    admit();
    var draft = copyPdf();
    stored.clear();
    mvc.perform(
            post("/api/v1/identities/" + draft.id() + "/approval")
                .with(authentication(owner))
                .contentType("application/json")
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isServiceUnavailable())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    var other = brands.saveAndFlush(new Brand("Other", workspace));
    var receipt =
        analysisAttempts
            .findByBrandIdAndStatus(brand.getId(), BrandAnalysisAttempt.Status.SUCCEEDED)
            .orElseThrow();
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    other.getId(), scope(), receipt.getId(), 0, actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
  }

  @Test
  void withdrawalDuringPdfApprovalReadBlocksCommit() {
    var a = admit();
    var draft = copyPdf();
    doAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(identityLifecycle.findById(draft.id(), scope()).status())
        .isEqualTo(ApprovalStatus.DRAFT);
  }

  @Test
  void ledgerChangeDuringPhysicalApprovalBlocksCommit() {
    admit();
    var draft = copyPdf();
    doAnswer(
            i -> {
              jdbc.update(
                  "update quarantine_objects set lock_version=lock_version+1 where evidence_id=?",
                  source.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(
            () ->
                identityLifecycle.approve(draft.id(), scope(), draft.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(identityLifecycle.findById(draft.id(), scope()).status())
        .isEqualTo(ApprovalStatus.DRAFT);
  }

  @Test
  void mixedTextWithdrawalDuringPdfIdentityReadBlocksCopy() {
    admit();
    var textAdmission = addReviewedText("Extra reviewed text");
    configureAnalysis();
    var result = generate();
    var textId = admissions.findById(textAdmission).orElseThrow().getEvidenceId();
    doAnswer(
            i -> {
              textReviews.withdraw(textId, textAdmission, scope(), actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(
            () ->
                evidenceIdentity.createFromAnalysis(
                    brand.getId(), scope(), result.analysisId(), 1, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void mixedReviewedTextAndPdfAreServerSelectedWithinOneBoundedCommand() {
    var pdf = admit();
    var pasted = addReviewedText("Reviewed pasted text");
    configureAnalysis();
    var outcome = generate();
    var links =
        jdbc.queryForList(
            "select admission_id from brand_analysis_sources where attempt_id=?",
            UUID.class,
            outcome.analysisId());
    assertThat(links).containsExactlyInAnyOrder(pdf.id(), pasted);
    assertThat(outcome.result().citations()).hasSize(2);
  }

  @Test
  void unadmittedPdfNeverReachesModel() {
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
    noAnalysisPublication();
  }

  @Test
  void textWithdrawalDuringPdfReadBlocksEntireBatchBeforeModel() {
    admit();
    var textId = addReviewedText("Reviewed companion text");
    var companion = admissions.findById(textId).orElseThrow();
    doAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              textReviews.withdraw(companion.getEvidenceId(), textId, scope(), actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
    noAnalysisPublication();
  }

  @Test
  void latestPdfConsentSelectedWithoutDuplicateEvidenceSources() {
    var first = admit();
    appendScan(ScanVerdict.CLEAN);
    var second = admit();
    configureAnalysis();
    var result = generate();
    assertThat(result.result().citations()).hasSize(1);
    assertThat(result.result().citations().getFirst().admissionId())
        .isEqualTo(second.id())
        .isNotEqualTo(first.id());
  }

  @Test
  void missingBytesBeforeModelFailsAttemptAndCanRetryAfterRepair() {
    admit();
    clearInvocations(analyzer);
    stored.remove(source.getSourceReference());
    assertThatThrownBy(this::generate)
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    verifyNoInteractions(analyzer);
    noAnalysisPublication();
    assertThat(
            analysisAttempts.findByBrandIdAndStatus(
                brand.getId(), BrandAnalysisAttempt.Status.RUNNING))
        .isEmpty();
    stored.put(source.getSourceReference(), bytes.clone());
    configureAnalysis();
    assertThat(generate().analysisId()).isNotNull();
  }

  @Test
  void mutatedBytesDuringModelCannotPublishSignature() {
    admit();
    when(analyzer.analyze(any()))
        .thenAnswer(
            i -> {
              var bad = bytes.clone();
              bad[bad.length - 1] ^= 1;
              stored.put(source.getSourceReference(), bad);
              return analysisResult(i.getArgument(0));
            });
    assertThatThrownBy(this::generate)
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    noAnalysisPublication();
  }

  @Test
  void changedLatestScanDuringModelCannotPublishSignature() {
    admit();
    when(analyzer.analyze(any()))
        .thenAnswer(
            i -> {
              appendScan(ScanVerdict.ERROR);
              return analysisResult(i.getArgument(0));
            });
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    noAnalysisPublication();
  }

  @Test
  void withdrawalDuringModelCannotPublishSignature() {
    var a = admit();
    when(analyzer.analyze(any()))
        .thenAnswer(
            i -> {
              textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
              return analysisResult(i.getArgument(0));
            });
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    noAnalysisPublication();
  }

  @Test
  void actualMembershipRevocationDuringModelCannotPublishSignature() {
    admit();
    when(analyzer.analyze(any()))
        .thenAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
              return analysisResult(i.getArgument(0));
            });
    assertThatThrownBy(this::generate)
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    noAnalysisPublication();
  }

  @Test
  void forgedPdfClassificationCannotSkipAllSourcePreflight() {
    admit();
    var captured = analysisTx.startEvidence(brand.getId(), scope(), actor.getId());
    var forged =
        new BrandAnalysisTransactions.Snapshot(
            captured.brandId(), captured.command(), actor.getId(), Set.of());
    assertThatThrownBy(() -> analysisTx.preflight(forged, scope()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
  }

  @Test
  void mixedUtf8BudgetRejectsBeforeCreatingAttemptOrCallingModel() {
    admit();
    addReviewedText("Reviewed" + "\u754c".repeat(3600));
    addReviewedText("Reviewed other" + "\u754c".repeat(3600));
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
    noAnalysisPublication();
  }

  @Test
  void sixReviewedEvidenceSourcesNeverSilentlyTruncated() {
    admit();
    for (int i = 0; i < 5; i++) addReviewedText("Reviewed extra " + i);
    assertThatThrownBy(this::generate).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
    noAnalysisPublication();
  }

  @Test
  void changedScanAgeRequiresNewExplicitContextWithoutExtendingOldReceipt() throws Exception {
    var oldToken = token();
    var first = preview.admit(source.getId(), scope(), actor.getId(), oldToken);
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(transactions);
    Object oldAge = org.springframework.test.util.ReflectionTestUtils.getField(target, "maxAge");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "maxAge", Duration.ofHours(12));
      assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), oldToken))
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> preview.snapshot(source.getId(), first.id(), scope(), actor.getId()))
          .isInstanceOf(IllegalStateException.class);
      var second = admit();
      assertThat(second.latestScanId()).isEqualTo(first.latestScanId());
      assertThat(second.scanExpiresAt()).isEqualTo(first.scanExpiresAt().minusSeconds(12 * 3600));
      assertThat(second.id()).isNotEqualTo(first.id());
      assertThat(preview.snapshot(source.getId(), second.id(), scope(), actor.getId()).text())
          .isEqualTo(text);
      assertThat(admissions.findById(first.id()).orElseThrow().getScanExpiresAt())
          .isEqualTo(first.scanExpiresAt());
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(target, "maxAge", oldAge);
    }
  }

  protected com.cadence.application.results.PdfAdmissionDetails admit() {
    return preview.admit(source.getId(), scope(), actor.getId(), token());
  }

  @Test
  void explicitPdfAdmissionAndCurrentSnapshotPreserveSelectorsAndServerAudit() throws Exception {
    var t = token();
    var response =
        mvc.perform(
                post(path() + "/admissions")
                    .with(authentication(owner))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(t)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.artifactId").value(t.artifactId().toString()))
            .andExpect(jsonPath("$.reviewedBy").value(actor.getId().toString()))
            .andExpect(jsonPath("$.policyVersion").value("pdf-text-review-v1"))
            .andExpect(jsonPath("$.aiConsumptionEnabled").value(true))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    var id =
        UUID.fromString(
            jsonMapper.readTree(response.getResponse().getContentAsString()).get("id").asText());
    mvc.perform(get(path() + "/admissions/" + id + "/snapshot").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(text))
        .andExpect(jsonPath("$.admission.id").value(id.toString()))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    assertThat(
            admissions.findCurrentText(
                brand.getId(),
                brand.getLockVersion(),
                workspace.getLockVersion(),
                org.springframework.data.domain.PageRequest.of(0, 5)))
        .isEmpty();
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(() -> textReviews.snapshot(source.getId(), id, scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(analyzer);
  }

  @Test
  void replayAndForeignPdfReceiptRejectedWithoutStorageRead() {
    var t = token();
    var a = preview.admit(source.getId(), scope(), actor.getId(), t);
    clearInvocations(storage);
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> preview.snapshot(source.getId(), UUID.randomUUID(), scope(), actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    verifyNoInteractions(storage);
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
  }

  @Test
  void forgedSelectorsFailBeforePhysicalRead() {
    var t = token();
    for (var bad :
        List.of(
            new PdfReviewToken(
                UUID.randomUUID(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion(),
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                UUID.randomUUID(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion(),
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                UUID.randomUUID(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion(),
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion() + 1,
                t.brandVersion(),
                t.workspaceVersion(),
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion() + 1,
                t.workspaceVersion(),
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion() + 1,
                t.sourceChecksum(),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion(),
                "0".repeat(64),
                t.contentChecksum(),
                t.scanExpiresAt(),
                t.policyVersion()),
            new PdfReviewToken(
                t.artifactId(),
                t.originalScanId(),
                t.latestScanId(),
                t.sourceVersion(),
                t.brandVersion(),
                t.workspaceVersion(),
                t.sourceChecksum(),
                "0".repeat(64),
                t.scanExpiresAt(),
                t.policyVersion())))
      assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), bad))
          .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void pdfAdmissionRequiresPrincipalActorAndOwner() throws Exception {
    var t = token();
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), UUID.randomUUID(), t))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    String body = jsonMapper.writeValueAsString(t);
    SecurityContextHolder.clearContext();
    mvc.perform(post(path() + "/admissions").contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(path() + "/admissions")
                .with(authentication(auth(actor.getId(), UserRole.VIEWER, scope())))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    verifyNoInteractions(storage);
  }

  @Test
  void pdfAdmissionRejectsMassAssignmentAndMissingFields() throws Exception {
    var t = token();
    var mapper = jsonMapper;
    var json = mapper.valueToTree(t);
    ((com.fasterxml.jackson.databind.node.ObjectNode) json)
        .put("reviewedBy", UUID.randomUUID().toString());
    mvc.perform(
            post(path() + "/admissions")
                .with(authentication(owner))
                .contentType("application/json")
                .content(mapper.writeValueAsString(json)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path() + "/admissions")
                .with(authentication(owner))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(storage);
  }

  @Test
  void corruptStorageCannotCreateAdmission() {
    var t = token();
    stored.remove(source.getSourceReference());
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void admissionRechecksMembershipAfterIo() {
    var t = token();
    doAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void admissionRejectsScanChangeDuringIo() {
    var t = token();
    doAnswer(
            i -> {
              appendScan(ScanVerdict.CLEAN);
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void admissionRejectsSourceChangeDuringIo() {
    var t = token();
    doAnswer(
            i -> {
              jdbc.update(
                  "update brand_evidence set lock_version=lock_version+1 where id=?",
                  source.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void admissionRejectsArchiveDuringIo() {
    var t = token();
    doAnswer(
            i -> {
              jdbc.update(
                  "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
                  workspace.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    assertThat(admissions.countForEvidence(source.getId())).isZero();
  }

  @Test
  void expiredPdfReceiptCannotReturnSnapshot() {
    var a = admit();
    Instant future = clock.instant().plusSeconds(2 * 86400);
    doReturn(future).when(clock).instant();
    clearInvocations(storage);
    assertThatThrownBy(() -> preview.snapshot(source.getId(), a.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void rescanRequiresNewConsentAndDoesNotRewriteOriginalReceipt() {
    var first = admit();
    var latest = appendScan(ScanVerdict.CLEAN);
    assertThatThrownBy(() -> preview.snapshot(source.getId(), first.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    var second = admit();
    assertThat(second.latestScanId()).isEqualTo(latest.getId()).isNotEqualTo(first.latestScanId());
    assertThat(second.originalScanId()).isEqualTo(first.originalScanId());
    assertThat(admissions.findById(first.id()).orElseThrow().getLatestScanId())
        .isEqualTo(first.latestScanId());
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(2);
  }

  @Test
  void withdrawalBlocksAllPdfContextsButPreservesHistory() {
    var a = admit();
    textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
    assertThatThrownBy(() -> preview.snapshot(source.getId(), a.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    appendScan(ScanVerdict.CLEAN);
    var t = token();
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(admissions.findById(a.id()).orElseThrow().getReviewedText()).isEqualTo(text);
  }

  @Test
  void withdrawalDuringSnapshotReadRejectsText() {
    var a = admit();
    doAnswer(
            i -> {
              textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(() -> preview.snapshot(source.getId(), a.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void archivedPdfCanBeWithdrawnButNotConsumed() {
    var a = admit();
    jdbc.update(
        "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
        workspace.getId());
    assertThatThrownBy(() -> preview.snapshot(source.getId(), a.id(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(textReviews.withdraw(source.getId(), a.id(), scope(), actor.getId()).admissionId())
        .isEqualTo(a.id());
  }

  protected String path() {
    return "/api/v1/evidence/" + source.getId() + "/document-review";
  }

  protected com.cadence.application.results.PdfReviewPreview execute() {
    return preview.execute(source.getId(), scope(), actor.getId());
  }

  @Test
  void verifiedPdfPreviewReturnsOnlyBoundedReviewTextNotAdmission() throws Exception {
    mvc.perform(get(path()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.text").value(text))
        .andExpect(jsonPath("$.artifactId").value(artifact.getId().toString()))
        .andExpect(jsonPath("$.originalScanId").value(original.getId().toString()))
        .andExpect(jsonPath("$.latestScanId").value(original.getId().toString()))
        .andExpect(jsonPath("$.requiresHumanReview").value(true))
        .andExpect(jsonPath("$.admittedToAi").value(false))
        .andExpect(jsonPath("$.storageReference").doesNotExist())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    verify(storage).readQuarantined(source.getSourceReference(), bytes.length);
    verifyNoInteractions(scanner, renderer, analyzer);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from evidence_admissions where evidence_id=?",
                Long.class,
                source.getId()))
        .isZero();
  }

  @Test
  void anonymousViewerAndForgedActorDenied() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(get(path())).andExpect(status().isUnauthorized());
    mvc.perform(get(path()).with(authentication(auth(actor.getId(), UserRole.VIEWER, scope()))))
        .andExpect(status().isForbidden());
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(() -> preview.execute(source.getId(), scope(), UUID.randomUUID()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void foreignEvidenceCannotSelectPrivateObject() throws Exception {
    var foreign = workspaces.saveAndFlush(new Workspace("Foreign", "Creator"));
    var b = brands.saveAndFlush(new Brand("Foreign", foreign));
    var item =
        evidence.saveAndFlush(
            new BrandEvidence(
                b,
                EvidenceType.TEXT,
                "Foreign",
                EvidenceAdmission.checksum("TEXT\nForeign"),
                clock.instant()));
    mvc.perform(
            get("/api/v1/evidence/" + item.getId() + "/document-review")
                .with(authentication(owner)))
        .andExpect(status().isNotFound());
    verifyNoInteractions(storage);
  }

  @Test
  void actualMembershipRequiredBeforePrivateRead() {
    jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
    assertThatThrownBy(this::execute)
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void unsupportedUrlOrTextNeverReachesStorage() {
    for (var type : List.of(EvidenceType.URL, EvidenceType.TEXT)) {
      var value = type == EvidenceType.URL ? "https://example.test/" : "Text";
      var item =
          evidence.saveAndFlush(
              new BrandEvidence(
                  brand,
                  type,
                  value,
                  EvidenceAdmission.checksum(type + "\n" + value),
                  clock.instant()));
      assertThatThrownBy(() -> preview.execute(item.getId(), scope(), actor.getId()))
          .isInstanceOf(IllegalStateException.class);
    }
    verifyNoInteractions(storage);
  }

  @Test
  void missingArtifactAndLegacyStorageRegistrationFailClosed() {
    var legacy =
        evidence.saveAndFlush(
            BrandEvidence.quarantinedFile(
                brand,
                "quarantine:quarantine/" + brand.getId() + "/" + "0".repeat(64) + ".pdf",
                "0".repeat(64),
                clock.instant(),
                "legacy.pdf",
                "application/pdf"));
    assertThatThrownBy(() -> preview.execute(legacy.getId(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void latestNonCleanScanRejectsEarlierCleanArtifact() {
    appendScan(ScanVerdict.ERROR);
    assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void expiredScanRejectedWithoutPrivateRead() {
    Instant later = clock.instant().plus(Duration.ofDays(2));
    doReturn(later).when(clock).instant();
    assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void missingOrMutatedBytesReturnSafeUnavailable() throws Exception {
    stored.remove(source.getSourceReference());
    mvc.perform(get(path()).with(authentication(owner)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("document_review_unavailable"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void freshCleanRescanPreservesOriginalArtifactScan() {
    var latest = appendScan(ScanVerdict.CLEAN);
    var result = execute();
    assertThat(result.originalScanId()).isEqualTo(original.getId());
    assertThat(result.latestScanId()).isEqualTo(latest.getId()).isNotEqualTo(original.getId());
    assertThat(result.text()).isEqualTo(text);
  }

  @Test
  void corruptBytesCannotReturnReviewText() {
    var bad = bytes.clone();
    bad[bad.length - 1] ^= 1;
    stored.put(source.getSourceReference(), bad);
    for (int attempt = 0; attempt < 3; attempt++)
      assertThatThrownBy(this::execute)
          .isInstanceOf(
              com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
    stored.put(source.getSourceReference(), bytes.clone());
    assertThat(execute().text()).isEqualTo(text);
  }

  @Test
  void archivedParentDuringStorageReadRejectsFinalize() {
    doAnswer(
            i -> {
              jdbc.update(
                  "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
                  workspace.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void membershipRevocationDuringStorageReadRejectsFinalize() {
    doAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(this::execute)
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void newScanDuringStorageReadRejectsOriginalPreviewEvenIfClean() {
    doAnswer(
            i -> {
              appendScan(ScanVerdict.CLEAN);
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sourceVersionChangeDuringStorageReadRejectsFinalize() {
    doAnswer(
            i -> {
              jdbc.update(
                  "update brand_evidence set lock_version=lock_version+1 where id=?",
                  source.getId());
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void boundedParallelReadsRejectBusyRequestAndReleaseSlots() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(2);
    var release = new java.util.concurrent.CountDownLatch(1);
    doAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              assertThat(release.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              return bytes.clone();
            })
        .when(storage)
        .readQuarantined(anyString(), anyInt());
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.concurrent.Callable<com.cadence.application.results.PdfReviewPreview> call =
        () -> {
          SecurityContextHolder.getContext().setAuthentication(owner);
          try {
            return execute();
          } finally {
            SecurityContextHolder.clearContext();
          }
        };
    try {
      var first = pool.submit(call);
      var second = pool.submit(call);
      assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(this::execute)
          .isInstanceOf(
              com.cadence.application.exceptions.DocumentReviewUnavailableException.class);
      release.countDown();
      assertThat(first.get(15, java.util.concurrent.TimeUnit.SECONDS).admittedToAi()).isFalse();
      assertThat(second.get(15, java.util.concurrent.TimeUnit.SECONDS).admittedToAi()).isFalse();
      assertThat(execute().requiresHumanReview()).isTrue();
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void unsupportedImageNeverReachesStorage() {
    var image =
        evidence.saveAndFlush(
            BrandEvidence.quarantinedFile(
                brand,
                "quarantine:image",
                "0".repeat(64),
                clock.instant(),
                "image.png",
                "image/png"));
    assertThatThrownBy(() -> preview.execute(image.getId(), scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void lateReadAndForeignCompletionActorRejected() {
    var snapshot = transactions.capture(source.getId(), scope(), actor.getId());
    var late =
        new PdfReviewTransactions.Snapshot(snapshot.input(), clock.instant().minusSeconds(1));
    assertThatThrownBy(() -> transactions.finish(late, scope()))
        .isInstanceOf(IllegalStateException.class);
    SecurityContextHolder.getContext()
        .setAuthentication(auth(UUID.randomUUID(), UserRole.OWNER, scope()));
    assertThatThrownBy(() -> transactions.finish(snapshot, scope()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}

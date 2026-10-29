package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.DurableJobQueue;
import com.cadence.application.usecases.RegisterBrandEvidenceUseCase;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

public abstract class FileUploadTransactionContract {
  @Autowired RegisterBrandEvidenceUseCase registration;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired QuarantineWriteIntentRepository writeIntents;
  @Autowired DurableJobRepository jobs;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean EvidenceObjectStorage storage;
  @MockitoSpyBean DurableJobQueue queue;
  Workspace workspace;
  Brand brand;
  byte[] bytes;
  long beforeEvidence;
  long beforeJobs;

  @BeforeEach
  void prepare() {
    workspace = workspaces.saveAndFlush(new Workspace("Upload studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Upload brand", workspace));
    bytes = "%PDF-1.7 upload fixture".getBytes(StandardCharsets.UTF_8);
    beforeEvidence = evidence.count();
    beforeJobs = jobs.count();
    authenticate();
    when(scanner.scan(any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    when(storage.storeQuarantined(anyString(), any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat((byte[]) call.getArgument(1)).isEqualTo(bytes);
              assertThat(writeIntents.findAll())
                  .anySatisfy(
                      intent -> {
                        assertThat(intent.getBrandId()).isEqualTo(brand.getId());
                        assertThat(intent.getObjectKey()).isEqualTo(call.getArgument(0));
                        assertThat(intent.getRegisteredAt()).isNull();
                      });
              return "quarantine:" + call.getArgument(0);
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new CadencePrincipal(
                    UUID.randomUUID(), "upload-test", UserRole.OWNER, Set.of(workspace.getId())),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
  }

  RegisterBrandEvidenceUseCase.Registration upload() {
    return registration.registerFile(
        brand.getId(), Set.of(workspace.getId()), "source.pdf", "application/pdf", bytes);
  }

  void noRows() {
    assertThat(evidence.count()).isEqualTo(beforeEvidence);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
  }

  @Test
  void ioRunsOutsideTransactionsAndDuplicateKeepsSameJob() {
    var first = upload();
    var second = upload();
    assertThat(second.evidence().getId()).isEqualTo(first.evidence().getId());
    assertThat(second.job().getId()).isEqualTo(first.job().getId());
    assertThat(first.evidence().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(evidence.count()).isEqualTo(beforeEvidence + 1);
    assertThat(jobs.count()).isEqualTo(beforeJobs + 1);
    verify(scanner, times(2)).scan(any());
    verify(storage).storeQuarantined(anyString(), any());
    var intents =
        writeIntents.findAll().stream()
            .filter(intent -> intent.getBrandId().equals(brand.getId()))
            .toList();
    assertThat(intents).hasSize(1);
    assertThat(intents.getFirst().getEvidenceId()).isEqualTo(first.evidence().getId());
    assertThat(intents.getFirst().getRegisteredAt()).isNotNull();
  }

  @Test
  void inheritedTransactionIsRejectedBeforeIo() {
    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> upload()))
        .isInstanceOf(IllegalTransactionStateException.class);
    verifyNoInteractions(scanner, storage);
    noRows();
  }

  @Test
  void scannerCannotSubstituteTheUploadedBytes() {
    when(scanner.scan(any()))
        .thenAnswer(
            call -> {
              ((byte[]) call.getArgument(0))[0] = 0;
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    assertThatThrownBy(this::upload).isInstanceOf(EvidenceScanUnavailableException.class);
    assertThat(bytes[0]).isEqualTo((byte) '%');
    verifyNoInteractions(storage);
    noRows();
  }

  @Test
  void storageErrorsAreSanitizedAndLeaveNoRows() {
    doThrow(new IllegalArgumentException("secret-provider"))
        .when(storage)
        .storeQuarantined(anyString(), any());
    assertThatThrownBy(this::upload)
        .isInstanceOf(EvidenceStorageUnavailableException.class)
        .hasMessage("Evidence file could not be stored; try again later")
        .hasNoCause();
    noRows();
    assertThat(writeIntents.findAll())
        .anySatisfy(
            intent -> {
              assertThat(intent.getBrandId()).isEqualTo(brand.getId());
              assertThat(intent.getRegisteredAt()).isNull();
            });
  }

  @Test
  void failedEnqueueRollsBackEvidenceWithoutDeletingSharedObject() {
    doThrow(new IllegalStateException("enqueue fixture"))
        .when(queue)
        .enqueue(anyString(), anyString(), anyString(), anyInt(), any());
    assertThatThrownBy(this::upload).isInstanceOf(IllegalStateException.class);
    noRows();
    verify(storage).storeQuarantined(anyString(), any());
    var unresolved =
        writeIntents.findUnresolvedWithoutReference(
            java.time.Instant.now().plusSeconds(60),
            org.springframework.data.domain.PageRequest.of(0, 100));
    assertThat(unresolved)
        .anySatisfy(
            intent -> {
              assertThat(intent.getBrandId()).isEqualTo(brand.getId());
              assertThat(intent.getRegisteredAt()).isNull();
              assertThat(intent.getEvidenceId()).isNull();
            });
  }

  @Test
  void inventoryExcludesUnregisteredAttemptWhoseSharedObjectIsReferenced() {
    var registration = upload();
    String key = registration.evidence().getSourceReference().substring("quarantine:".length());
    writeIntents.saveAndFlush(
        new QuarantineWriteIntent(brand.getId(), key, java.time.Instant.now()));
    var unresolved =
        writeIntents.findUnresolvedWithoutReference(
            java.time.Instant.now().plusSeconds(60),
            org.springframework.data.domain.PageRequest.of(0, 100));
    assertThat(unresolved).noneMatch(intent -> intent.getBrandId().equals(brand.getId()));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.NullSource
  @ValueSource(
      strings = {
        "",
        "quarantine:",
        "https://provider.internal/file",
        "quarantine:quarantine/another-tenant/source.pdf",
        "quarantine:quarantine/another-tenant/../source.pdf"
      })
  void invalidStorageReferenceCannotCreateEvidence(String reference) {
    doReturn(reference).when(storage).storeQuarantined(anyString(), any());
    assertThatThrownBy(this::upload).isInstanceOf(EvidenceStorageUnavailableException.class);
    noRows();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void archiveDuringStoragePreventsRegistration(boolean archiveWorkspace) {
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              new TransactionTemplate(manager)
                  .executeWithoutResult(
                      status -> {
                        if (archiveWorkspace)
                          workspaces.findById(workspace.getId()).orElseThrow().archive();
                        else brands.findById(brand.getId()).orElseThrow().archive();
                      });
              return "quarantine:" + call.getArgument(0);
            })
        .when(storage)
        .storeQuarantined(anyString(), any());
    assertThatThrownBy(this::upload)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("archived");
    noRows();
  }

  @Test
  void concurrentUploadsCommitOneEvidenceAndOneJob() throws Exception {
    var reachedStorage = new CountDownLatch(2);
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              reachedStorage.countDown();
              assertThat(reachedStorage.await(10, TimeUnit.SECONDS)).isTrue();
              return "quarantine:" + call.getArgument(0);
            })
        .when(storage)
        .storeQuarantined(anyString(), any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<RegisterBrandEvidenceUseCase.Registration> request =
          () -> {
            authenticate();
            try {
              return upload();
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = executor.submit(request);
      var b = executor.submit(request);
      var first = a.get(20, TimeUnit.SECONDS);
      var second = b.get(20, TimeUnit.SECONDS);
      assertThat(second.evidence().getId()).isEqualTo(first.evidence().getId());
      assertThat(second.job().getId()).isEqualTo(first.job().getId());
    }
    assertThat(evidence.count()).isEqualTo(beforeEvidence + 1);
    assertThat(jobs.count()).isEqualTo(beforeJobs + 1);
  }
}

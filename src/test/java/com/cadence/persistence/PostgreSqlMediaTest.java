package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.MediaUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.domain.*;
import com.cadence.support.MediaApiContract;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlMediaTest extends MediaApiContract {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @MockitoSpyBean PrivateMediaStorage storage;
  @MockitoSpyBean DemoMediaSource sourceBytes;
  @MockitoSpyBean EvidenceMalwareScanner scanner;

  @Test
  void boundedDeliveryCapacityDoesNotBlockPostsWithoutMedia() throws Exception {
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var p = create();
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test did not release");
              return call.callRealMethod();
            })
        .when(storage)
        .readMedia(any(), anyInt());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<?>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  SecurityContextHolder.getContext().setAuthentication(owner);
                  try {
                    return media.delivery(a.id(), scope(), false);
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> media.delivery(a.id(), scope(), false))
            .isInstanceOf(MediaUnavailableException.class);
        assertThat(posts.approve(p.id(), p.revisionId(), scope(), 0).status())
            .isEqualTo(PostRevision.State.APPROVED);
      } finally {
        release.countDown();
      }
      for (var f : futures) assertThat(f.get(20, TimeUnit.SECONDS)).isNotNull();
    }
  }

  @Test
  void sqlRejectsUnreviewedForeignAndWrongFormatEvenWithActualDraftAuditWitness() throws Exception {
    var p = create();
    var pending = fresh(DemoMediaFixture.VIDEO_V1);
    var wrong = reviewed(DemoMediaFixture.IMAGE_V1);
    var other = brands.saveAndFlush(new Brand("Foreign media", workspace));
    var f = media.create(other.getId(), scope(), DemoMediaFixture.VIDEO_V1);
    var foreign = media.review(f.id(), scope(), f.lockVersion(), true);
    for (var a : List.of(pending, wrong, foreign))
      try (var c = datasource.getConnection()) {
        c.setAutoCommit(false);
        try (var q =
            c.prepareStatement(
                "update post_revisions set edited_by=?,edited_at=clock_timestamp(),lock_version=lock_version+1 where id=?")) {
          q.setObject(1, actorId);
          q.setObject(2, p.revisionId());
          q.executeUpdate();
        }
        try (var q =
            c.prepareStatement(
                "insert into post_revision_media(revision_id,asset_order,asset_id) values (?,0,?)")) {
          q.setObject(1, p.revisionId());
          q.setObject(2, a.id());
          q.executeUpdate();
        }
        assertThatThrownBy(c::commit)
            .isInstanceOfSatisfying(
                java.sql.SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
        c.rollback();
      }
    assertThat(posts.read(p.id(), scope()).mediaAssetIds()).isEmpty();
    assertThat(posts.read(p.id(), scope()).lockVersion()).isZero();
  }

  @Test
  void stagingArchiveFencesLateStoreAndRetainsItsReservation() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test did not release");
              return call.callRealMethod();
            })
        .when(storage)
        .storeMedia(any(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return fresh(DemoMediaFixture.IMAGE_V1);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var intent =
            media
                .list(brand.getId(), scope(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()
                .getFirst();
        media.archive(intent.id(), scope(), intent.lockVersion());
      } finally {
        release.countDown();
      }
      assertThatThrownBy(() -> result.get(20, TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    var archived =
        media
            .list(brand.getId(), scope(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    assertThat(archived.status()).isEqualTo(MediaAsset.State.ARCHIVED);
    assertThat(archived.scanResult()).isNull();
    assertThat(assets.reservedBytes(brand.getId())).isEqualTo(archived.byteSize());
  }

  @Test
  void sqlProvenanceHistoryAndUnversionedLinkTamperingDenied() {
    var p = create();
    var asset = reviewed(DemoMediaFixture.VIDEO_V1);
    for (String mutation :
        List.of(
            "checksum=repeat('a',64)",
            "byte_size=10485761",
            "origin='LIVE_GENERATED'",
            "scan_signature='rewritten'"))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update media_assets set "
                          + mutation
                          + ",lock_version=lock_version+1 where id=?",
                      asset.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("delete from media_assets where id=?", asset.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into post_revision_media values (?,0,?)", p.revisionId(), asset.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var attached =
        posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(asset.id())).details();
    posts.approve(p.id(), p.revisionId(), scope(), attached.lockVersion());
    for (String query :
        List.of(
            "delete from post_revision_media where revision_id=?",
            "update post_revision_media set asset_order=1 where revision_id=?"))
      assertThatThrownBy(() -> jdbc.update(query, p.revisionId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void missingOrCorruptStoredBytesCannotApproveOrDeliver() {
    var p = create();
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var current = posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    doThrow(new QuarantineReadFailure(QuarantineReadFailure.Code.NOT_FOUND))
        .when(storage)
        .readMedia(any(), anyInt());
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), current.lockVersion()))
        .isInstanceOf(MediaUnavailableException.class);
    assertThatThrownBy(() -> media.delivery(a.id(), scope(), false))
        .isInstanceOf(MediaUnavailableException.class);
    assertThat(posts.read(p.id(), scope()).status()).isEqualTo(PostRevision.State.DRAFT);
    doReturn(new byte[] {1, 2, 3}).when(storage).readMedia(any(), anyInt());
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), current.lockVersion()))
        .isInstanceOf(MediaUnavailableException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"oversized", "wrong-format", "infected", "unavailable", "mutated"})
  void hostileSourceOrScannerNeverPublishesUsableAsset(String scenario) {
    switch (scenario) {
      case "oversized" ->
          doReturn(new byte[MediaObjectKey.MAX_BYTES + 1])
              .when(sourceBytes)
              .bytes(DemoMediaFixture.IMAGE_V1);
      case "wrong-format" ->
          doReturn(sourceBytes.bytes(DemoMediaFixture.VIDEO_V1))
              .when(sourceBytes)
              .bytes(DemoMediaFixture.IMAGE_V1);
      case "infected" ->
          doReturn(new MalwareScanReport(MalwareScanResult.INFECTED, "test-engine", "test-v1"))
              .when(scanner)
              .scan(any());
      case "unavailable" ->
          doThrow(new IllegalStateException("private upstream detail")).when(scanner).scan(any());
      case "mutated" ->
          doAnswer(
                  call -> {
                    ((byte[]) call.getArgument(0))[0] = 0;
                    return new MalwareScanReport(MalwareScanResult.CLEAN, "test-engine", "test-v1");
                  })
              .when(scanner)
              .scan(any());
    }
    assertThatThrownBy(() -> fresh(DemoMediaFixture.IMAGE_V1))
        .isInstanceOf(MediaUnavailableException.class);
    var a =
        media
            .list(brand.getId(), scope(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    assertThat(a.status()).isEqualTo(MediaAsset.State.REJECTED);
    assertThat(a.moderation()).isEqualTo(MediaAsset.Moderation.PENDING);
    assertThat(assets.reservedBytes(brand.getId())).isEqualTo(DemoMediaFixture.IMAGE_V1.size());
    if (scenario.equals("infected")) assertThat(a.scanResult()).isEqualTo(ScanVerdict.INFECTED);
    else assertThat(a.scanResult()).isNotEqualTo(ScanVerdict.CLEAN);
  }

  @Test
  void crashBeforeStoreLeavesFencedStagingAndRetainedQuota() {
    doThrow(new AssertionError("simulated process crash")).when(storage).storeMedia(any(), any());
    assertThatThrownBy(() -> fresh(DemoMediaFixture.IMAGE_V1)).isInstanceOf(AssertionError.class);
    var a =
        media
            .list(brand.getId(), scope(), org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    assertThat(a.status()).isEqualTo(MediaAsset.State.STAGING);
    assertThat(a.scanResult()).isNull();
    assertThatThrownBy(() -> media.delivery(a.id(), scope(), true))
        .isInstanceOf(IllegalStateException.class);
    media.archive(a.id(), scope(), 0);
    assertThat(assets.reservedBytes(brand.getId())).isEqualTo(a.byteSize());
  }

  @ParameterizedTest
  @ValueSource(strings = {"asset", "brand", "workspace", "account", "membership"})
  void revocationDuringPrivateDeliveryIsDeniedWithoutLocksAroundIo(String kind) throws Exception {
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test did not release I/O");
              return call.callRealMethod();
            })
        .when(storage)
        .readMedia(any(), anyInt());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return media.delivery(a.id(), scope(), false);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        switch (kind) {
          case "asset" -> media.archive(a.id(), scope(), a.lockVersion());
          case "brand" ->
              jdbc.update(
                  "update brands set status='ARCHIVED',lock_version=lock_version+1 where id=?",
                  brand.getId());
          case "workspace" ->
              jdbc.update(
                  "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
                  workspace.getId());
          case "account" ->
              jdbc.update("update cadence_users set active=false where id=?", actorId);
          case "membership" ->
              jdbc.update("delete from workspace_memberships where user_id=?", actorId);
        }
      } finally {
        release.countDown();
      }
      assertThatThrownBy(() -> result.get(20, TimeUnit.SECONDS))
          .hasCauseInstanceOf(
              kind.equals("account") || kind.equals("membership")
                  ? org.springframework.security.access.AccessDeniedException.class
                  : IllegalStateException.class);
    }
  }

  @Test
  void replacementDuringApprovalRejectsLateApprovalAndPreservesCurrentSelection() throws Exception {
    var p = create();
    var a = reviewed(DemoMediaFixture.VIDEO_V1);
    var b = reviewed(DemoMediaFixture.VIDEO_V1);
    var selected =
        posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              if (first.getAndSet(false)) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("Missing test release");
              }
              return call.callRealMethod();
            })
        .when(storage)
        .readMedia(any(), anyInt());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return posts.approve(p.id(), p.revisionId(), scope(), selected.lockVersion());
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        posts.replaceMedia(
            p.id(), p.revisionId(), scope(), selected.lockVersion(), List.of(b.id()));
      } finally {
        release.countDown();
      }
      assertThatThrownBy(() -> result.get(20, TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(posts.read(p.id(), scope()).mediaAssetIds()).containsExactly(b.id());
    assertThat(posts.read(p.id(), scope()).status()).isEqualTo(PostRevision.State.DRAFT);
  }

  @Test
  void retainedObjectQuotaCannotBeReleasedByArchiveOrFailedRegistration() {
    var first = fresh(DemoMediaFixture.IMAGE_V1);
    jdbc.update(
        "insert into media_assets(id,created_at,updated_at,lock_version,brand_id,fixture,origin,format,checksum,byte_size,width,height,duration_millis,requested_by,requested_at,deadline,status,moderation) select gen_random_uuid(),now(),now(),0,brand_id,fixture,origin,format,checksum,byte_size,width,height,duration_millis,requested_by,now(),now()+interval '120 seconds','STAGING','PENDING' from media_assets cross join generate_series(1,999) where id=?",
        first.id());
    assertThat(assets.countByBrandId(brand.getId())).isEqualTo(1000);
    assertThatThrownBy(() -> fresh(DemoMediaFixture.IMAGE_V1))
        .isInstanceOf(IllegalStateException.class);
    media.archive(first.id(), scope(), first.lockVersion());
    assertThatThrownBy(() -> fresh(DemoMediaFixture.IMAGE_V1))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runtimeCanReviewAttachApproveButCannotRewriteOrDeleteProvenance() throws Exception {
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
    }
    setRuntimeRole(true);
    try {
      var p = create();
      var a = reviewed(DemoMediaFixture.VIDEO_V1);
      var attached =
          posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
      posts.approve(p.id(), p.revisionId(), scope(), attached.lockVersion());
      assertThat(media.delivery(a.id(), scope(), false).bytes()).hasSize(a.byteSize());
      for (String mutation :
          List.of(
              "delete from media_assets where id=?",
              "update media_assets set checksum=repeat('a',64) where id=?"))
        assertThatThrownBy(() -> jdbc.update(mutation, a.id()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      media.archive(a.id(), scope(), a.lockVersion());
    } finally {
      setRuntimeRole(false);
    }
  }

  private void setRuntimeRole(boolean runtime) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var held = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) held.add(datasource.getConnection());
      for (var c : held)
        try (var s = c.createStatement()) {
          s.execute(runtime ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : held) c.close();
    }
  }
}

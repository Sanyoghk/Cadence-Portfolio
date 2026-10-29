package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cadence.application.repositories.CadenceUserRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.CadenceUser;
import com.cadence.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CadenceIdentityServiceTest {
  final CadenceUserRepository users = mock(CadenceUserRepository.class);
  final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
  final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

  CadenceIdentityService service() {
    when(manager.getTransaction(any())).thenAnswer(i -> mock(TransactionStatus.class));
    return new CadenceIdentityService(users, workspaces, manager);
  }

  Jwt jwt(String subject) {
    return Jwt.withTokenValue("test-only")
        .header("alg", "RS256")
        .subject(subject)
        .claim("cadence_role", "OWNER")
        .build();
  }

  void conflict(Optional<CadenceUser> winner) {
    when(users.findByExternalSubject("subject")).thenReturn(Optional.empty(), winner);
    when(workspaces.findAllById(any())).thenReturn(List.of());
    when(users.save(any())).thenThrow(new DataIntegrityViolationException("test conflict"));
  }

  @Test
  void committedWinnerRoleAndMembershipReplaceLosingClaimsAfterRollback() {
    var committed = new CadenceUser("subject", UserRole.VIEWER, List.of());
    conflict(Optional.of(committed));
    var principal = service().resolve(jwt("subject"));
    assertThat(principal.role()).isEqualTo(UserRole.VIEWER);
    assertThat(principal.workspaceIds()).isEmpty();
    verify(users, times(1)).save(any());
    verify(manager, times(1)).rollback(any());
    verify(manager, times(1)).commit(any());
  }

  @Test
  void inactiveWinnerNeverAuthenticates() {
    var committed = new CadenceUser("subject", UserRole.OWNER, List.of());
    org.springframework.test.util.ReflectionTestUtils.setField(committed, "active", false);
    conflict(Optional.of(committed));
    assertThatThrownBy(() -> service().resolve(jwt("subject")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void missingWinnerFailsClosedWithoutRepeatedInsert() {
    conflict(Optional.empty());
    assertThatThrownBy(() -> service().resolve(jwt("subject")))
        .isInstanceOf(BadCredentialsException.class);
    verify(users, times(2)).findByExternalSubject("subject");
    verify(users, times(1)).save(any());
  }

  @Test
  void failedAmbientTransactionCannotRecoverAFalsePrincipal() {
    conflict(Optional.of(new CadenceUser("subject", UserRole.OWNER, List.of())));
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> service().resolve(jwt("subject")))
          .isInstanceOf(BadCredentialsException.class);
      verify(users, times(1)).findByExternalSubject("subject");
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", " subject", "subject ", "subject\n", "subject\u0000"})
  void rejectsNonCanonicalSubjectsBeforePersistence(String subject) {
    assertThatThrownBy(() -> service().resolve(jwt(subject)))
        .isInstanceOf(BadCredentialsException.class);
    verifyNoInteractions(users, workspaces);
  }

  @Test
  void rejectsOversizedSubjectBeforePersistence() {
    assertThatThrownBy(() -> service().resolve(jwt("x".repeat(256))))
        .isInstanceOf(BadCredentialsException.class);
    verifyNoInteractions(users, workspaces);
  }
}

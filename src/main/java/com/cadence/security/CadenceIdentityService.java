package com.cadence.security;

import com.cadence.application.repositories.CadenceUserRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.CadenceUser;
import com.cadence.domain.UserRole;
import com.cadence.domain.Workspace;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CadenceIdentityService {

  private final CadenceUserRepository users;
  private final WorkspaceRepository workspaces;
  private final TransactionTemplate transactions;

  public CadenceIdentityService(
      CadenceUserRepository users,
      WorkspaceRepository workspaces,
      PlatformTransactionManager transactionManager) {
    this.users = users;
    this.workspaces = workspaces;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public CadencePrincipal resolve(Jwt jwt) {
    String subject = jwt.getSubject();
    if (subject == null
        || subject.isBlank()
        || subject.length() > 255
        || !subject.equals(subject.trim())
        || subject.codePoints().anyMatch(Character::isISOControl)) {
      throw new BadCredentialsException("Token subject is required");
    }
    try {
      return transactions.execute(
          status ->
              activePrincipal(
                  users.findByExternalSubject(subject).orElseGet(() -> provision(jwt, subject))));
    } catch (DataIntegrityViolationException conflict) {
      // Authentication normally owns this transaction. Recover only AFTER its rollback;
      // never read from a failed persistence context or continue an ambient rollback-only tx.
      if (TransactionSynchronizationManager.isActualTransactionActive())
        throw new BadCredentialsException("Identity provisioning unavailable");
      return transactions.execute(
          status ->
              activePrincipal(
                  users
                      .findByExternalSubject(subject)
                      .orElseThrow(
                          () -> new BadCredentialsException("Identity provisioning unavailable"))));
    }
  }

  private CadencePrincipal activePrincipal(CadenceUser user) {
    if (!user.isActive()) {
      throw new BadCredentialsException("User is inactive");
    }
    return toPrincipal(user);
  }

  private CadenceUser provision(Jwt jwt, String subject) {
    UserRole role = parseRole(jwt.getClaimAsString("cadence_role"));
    Set<UUID> requestedWorkspaceIds = parseWorkspaceIds(jwt.getClaim("cadence_workspace_ids"));
    List<Workspace> assignedWorkspaces = workspaces.findAllById(requestedWorkspaceIds);
    if (assignedWorkspaces.size() != requestedWorkspaceIds.size()) {
      throw new BadCredentialsException("Token contains an unknown workspace assignment");
    }
    return users.save(new CadenceUser(subject, role, assignedWorkspaces));
  }

  private UserRole parseRole(String value) {
    try {
      return UserRole.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BadCredentialsException("Token contains an invalid Cadence role", exception);
    }
  }

  private Set<UUID> parseWorkspaceIds(Object claim) {
    if (claim == null) {
      return Set.of();
    }
    Collection<?> values = claim instanceof Collection<?> collection ? collection : List.of(claim);
    if (values.size() > 10) {
      throw new BadCredentialsException("Token contains too many workspace assignments");
    }
    Set<UUID> ids = new LinkedHashSet<>();
    try {
      for (Object value : values) {
        ids.add(UUID.fromString(String.valueOf(value)));
      }
      return ids;
    } catch (IllegalArgumentException exception) {
      throw new BadCredentialsException(
          "Token contains an invalid workspace assignment", exception);
    }
  }

  private CadencePrincipal toPrincipal(CadenceUser user) {
    Set<UUID> workspaceIds =
        user.getWorkspaces().stream().map(Workspace::getId).collect(Collectors.toSet());
    return new CadencePrincipal(
        user.getId(), user.getExternalSubject(), user.getRole(), workspaceIds);
  }
}

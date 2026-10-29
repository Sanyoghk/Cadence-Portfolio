package com.cadence.security;

import static org.junit.jupiter.api.Assertions.*;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UseCaseMethodSecurityTest {
  @MockitoBean private JwtDecoder decoder;
  @Autowired private WorkspaceManagementUseCase workspaces;
  @Autowired private BrandManagementUseCase brands;
  @Autowired private RegisterBrandEvidenceUseCase evidence;
  @Autowired private AnalyzeBrandUseCase analysis;
  @Autowired private WorkspaceRepository workspaceRepository;
  @Autowired private BrandRepository brandRepository;
  @Autowired private CadenceUserRepository users;
  @Autowired private BrandEvidenceRepository evidenceRepository;
  @Autowired private DurableJobRepository jobs;

  private Workspace workspace;
  private Workspace foreignWorkspace;
  private Brand brand;
  private Brand foreignBrand;
  private CadenceUser owner;
  private Set<UUID> scope;
  private final PageRequest page = PageRequest.of(0, 20);

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    workspace = workspaceRepository.saveAndFlush(new Workspace("Own studio", "Creator"));
    foreignWorkspace = workspaceRepository.saveAndFlush(new Workspace("Other studio", "Creator"));
    brand = brandRepository.saveAndFlush(new Brand("Own brand", workspace));
    foreignBrand = brandRepository.saveAndFlush(new Brand("Other brand", foreignWorkspace));
    owner =
        users.saveAndFlush(
            new CadenceUser("owner-" + UUID.randomUUID(), UserRole.OWNER, List.of(workspace)));
    scope = Set.of(workspace.getId());
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(UserRole role) {
    CadenceUser actor =
        role == UserRole.OWNER
            ? owner
            : users.saveAndFlush(
                new CadenceUser("actor-" + UUID.randomUUID(), role, List.of(workspace)));
    var principal = new CadencePrincipal(actor.getId(), actor.getExternalSubject(), role, scope);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  private List<Executable> ownerOperations() {
    return List.of(
        () -> workspaces.create(owner.getId(), "New studio", "Creator"),
        () ->
            workspaces.update(
                workspace.getId(), scope, workspace.getLockVersion(), "Changed", "Creator"),
        () -> workspaces.archive(workspace.getId(), scope, workspace.getLockVersion()),
        () -> brands.create(workspace.getId(), scope, "New brand", null, null, null),
        () ->
            brands.update(
                brand.getId(), scope, brand.getLockVersion(), "Changed", null, null, null),
        () -> brands.archive(brand.getId(), scope, brand.getLockVersion()),
        () -> evidence.register(brand.getId(), scope, EvidenceType.TEXT, "Source"),
        () ->
            evidence.registerFile(
                brand.getId(),
                scope,
                "source.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  }

  @Test
  void unauthenticatedCallsAreDeniedBeforeAnyOperation() {
    for (Executable operation : ownerOperations()) {
      assertThrows(AuthenticationCredentialsNotFoundException.class, operation);
    }
    assertThrows(
        AuthenticationCredentialsNotFoundException.class, () -> workspaces.findAll(scope, page));
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> workspaces.findById(workspace.getId(), scope));
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> brands.findAll(workspace.getId(), scope, page));
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> brands.findById(brand.getId(), scope));
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> evidence.findAll(brand.getId(), scope, page));
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> analysis.execute(brand.getId(), scope));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"VIEWER", "EDITOR"})
  void nonOwnersCannotInvokeOwnerOperations(UserRole role) {
    authenticate(role);
    long evidenceCount = evidenceRepository.count();
    long jobCount = jobs.count();
    for (Executable operation : ownerOperations())
      assertThrows(AccessDeniedException.class, operation);
    assertEquals(
        "Own studio", workspaceRepository.findById(workspace.getId()).orElseThrow().getName());
    assertEquals("Own brand", brandRepository.findById(brand.getId()).orElseThrow().getName());
    assertEquals(evidenceCount, evidenceRepository.count());
    assertEquals(jobCount, jobs.count());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"OWNER"})
  void membersCanReadTheirWorkspaceAndBrands(UserRole role) {
    authenticate(role);
    assertEquals(1, workspaces.findAll(scope, page).getTotalElements());
    assertEquals(workspace.getId(), workspaces.findById(workspace.getId(), scope).getId());
    assertEquals(1, brands.findAll(workspace.getId(), scope, page).getTotalElements());
    assertEquals(brand.getId(), brands.findById(brand.getId(), scope).getId());
    assertTrue(evidence.findAll(brand.getId(), scope, page).isEmpty());
    assertTrue(workspaces.findAll(Set.of(), page).isEmpty());
  }

  @Test
  void suppliedScopeCannotExpandAuthenticatedMembership() {
    authenticate(UserRole.OWNER);
    Set<UUID> forged = Set.of(workspace.getId(), foreignWorkspace.getId());
    assertThrows(AccessDeniedException.class, () -> workspaces.findAll(forged, page));
    assertThrows(
        AccessDeniedException.class, () -> workspaces.findById(foreignWorkspace.getId(), forged));
    assertThrows(AccessDeniedException.class, () -> brands.findById(foreignBrand.getId(), forged));
    assertThrows(
        AccessDeniedException.class, () -> brands.findAll(foreignWorkspace.getId(), forged, page));
    assertThrows(
        AccessDeniedException.class,
        () -> brands.create(foreignWorkspace.getId(), forged, "New", null, null, null));
    assertThrows(
        AccessDeniedException.class, () -> evidence.findAll(foreignBrand.getId(), forged, page));
    assertThrows(
        AccessDeniedException.class,
        () -> evidence.register(foreignBrand.getId(), forged, EvidenceType.TEXT, "Source"));
    assertThrows(AccessDeniedException.class, () -> analysis.execute(foreignBrand.getId(), forged));
    assertThrows(AccessDeniedException.class, () -> workspaces.findAll(null, page));
    assertThrows(
        ResourceNotFoundException.class, () -> brands.findById(foreignBrand.getId(), scope));
    assertThrows(
        ResourceNotFoundException.class, () -> analysis.execute(foreignBrand.getId(), scope));
  }

  @Test
  void ownerCannotCreateWorkspaceOnBehalfOfAnotherUser() {
    authenticate(UserRole.OWNER);
    CadenceUser other =
        users.saveAndFlush(
            new CadenceUser(
                "other-" + UUID.randomUUID(), UserRole.OWNER, List.of(foreignWorkspace)));
    long before = workspaceRepository.count();
    assertThrows(
        AccessDeniedException.class,
        () -> workspaces.create(other.getId(), "Impersonated", "Creator"));
    assertEquals(before, workspaceRepository.count());
    Workspace created = workspaces.create(owner.getId(), "My new studio", "Creator");
    assertTrue(owner.getWorkspaces().contains(created));
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void ownerCanManageBrandAndEvidence() {
    authenticate(UserRole.OWNER);
    workspaces.update(
        workspace.getId(), scope, workspace.getLockVersion(), "Updated studio", "Creator");
    Brand created = brands.create(workspace.getId(), scope, "Created", null, null, null);
    brands.update(created.getId(), scope, created.getLockVersion(), "Updated", null, null, null);
    assertEquals("Updated", brands.findById(created.getId(), scope).getName());
    assertNotNull(evidence.register(created.getId(), scope, EvidenceType.TEXT, "A source").job());
    assertEquals(1, evidence.findAll(created.getId(), scope, page).getTotalElements());
    assertNotNull(analysis.execute(created.getId(), scope));
    // Each call represents a committed request; analysis deliberately suspends caller transactions.
    long publishedVersion = brands.findById(created.getId(), scope).getLockVersion();
    long workspaceVersion = workspaces.findById(workspace.getId(), scope).getLockVersion();
    assertEquals(
        BrandStatus.ARCHIVED, brands.archive(created.getId(), scope, publishedVersion).getStatus());
    assertEquals(
        WorkspaceStatus.ARCHIVED,
        workspaces.archive(workspace.getId(), scope, workspaceVersion).getStatus());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"EDITOR", "VIEWER"})
  void reservedRolesCannotReadOrAnalyzeEvenWithinTheirScope(UserRole role) {
    authenticate(role);
    assertAll(
        () -> assertThrows(AccessDeniedException.class, () -> workspaces.findAll(scope, page)),
        () ->
            assertThrows(
                AccessDeniedException.class, () -> workspaces.findById(workspace.getId(), scope)),
        () ->
            assertThrows(
                AccessDeniedException.class, () -> brands.findAll(workspace.getId(), scope, page)),
        () ->
            assertThrows(AccessDeniedException.class, () -> brands.findById(brand.getId(), scope)),
        () ->
            assertThrows(
                AccessDeniedException.class, () -> evidence.findAll(brand.getId(), scope, page)),
        () ->
            assertThrows(
                AccessDeniedException.class, () -> analysis.execute(brand.getId(), scope)));
  }

  @Test
  void authoritiesWithoutCadenceIdentityDoNotGrantAccess() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "unmapped", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    assertThrows(AccessDeniedException.class, () -> workspaces.findAll(scope, page));
    assertThrows(
        AccessDeniedException.class, () -> workspaces.create(owner.getId(), "New", "Creator"));
    var principal =
        new CadencePrincipal(owner.getId(), owner.getExternalSubject(), UserRole.OWNER, scope);
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
    assertThrows(AccessDeniedException.class, () -> workspaces.findAll(scope, page));
  }
}

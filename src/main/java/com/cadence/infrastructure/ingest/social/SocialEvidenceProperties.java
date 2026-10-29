package com.cadence.infrastructure.ingest.social;

import com.cadence.domain.SocialEvidenceProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-provisioned bindings; deliberately no generated toString containing credentials. */
public final class SocialEvidenceProperties {
  private boolean enabled;
  private Duration deadline = Duration.ofSeconds(10);
  private int maxRequestsPerHour = 100;
  private List<Binding> bindings = new ArrayList<>();

  public void validate() {
    if (deadline == null
        || deadline.compareTo(Duration.ofSeconds(1)) < 0
        || deadline.compareTo(Duration.ofSeconds(30)) > 0
        || maxRequestsPerHour < 1
        || maxRequestsPerHour > 1000
        || bindings == null
        || bindings.size() > 1000
        || (enabled && bindings.isEmpty())) throw invalid();
    Set<String> identities = new HashSet<>();
    for (Binding binding : bindings) {
      if (binding == null
          || binding.brandId == null
          || binding.provider == null
          || !identities.add(binding.brandId + ":" + binding.provider)
          || binding.accountId == null
          || !binding.accountId.matches(
              binding.provider == SocialEvidenceProvider.INSTAGRAM
                  ? "[0-9]{1,32}"
                  : "[A-Za-z0-9_-]{1,128}")
          || binding.token == null
          || binding.token.length() > 4096
          || !binding.token.matches("[A-Za-z0-9._~+/-]{10,4096}=*")
          || binding.expiresAt == null
          || binding.scopes == null
          || (binding.provider == SocialEvidenceProvider.INSTAGRAM
              && (binding.apiVersion == null || !binding.apiVersion.matches("v[1-9][0-9]\\.0")))
          || (enabled
              && (!binding.approved
                  || !binding.scopes.containsAll(requiredScopes(binding.provider)))))
        throw invalid();
    }
  }

  static Set<String> requiredScopes(SocialEvidenceProvider provider) {
    return provider == SocialEvidenceProvider.INSTAGRAM
        ? Set.of("instagram_basic", "pages_read_engagement")
        : Set.of("user.info.basic", "video.list");
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid approved social evidence configuration");
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public Duration getDeadline() {
    return deadline;
  }

  public void setDeadline(Duration value) {
    deadline = value;
  }

  public int getMaxRequestsPerHour() {
    return maxRequestsPerHour;
  }

  public void setMaxRequestsPerHour(int value) {
    maxRequestsPerHour = value;
  }

  public List<Binding> getBindings() {
    return bindings;
  }

  public void setBindings(List<Binding> value) {
    bindings = value;
  }

  public static final class Binding {
    private UUID brandId;
    private SocialEvidenceProvider provider;
    private String accountId;
    private String token;
    private String apiVersion;
    private Instant expiresAt;
    private boolean approved;
    private Set<String> scopes = Set.of();

    public UUID getBrandId() {
      return brandId;
    }

    public void setBrandId(UUID value) {
      brandId = value;
    }

    public SocialEvidenceProvider getProvider() {
      return provider;
    }

    public void setProvider(SocialEvidenceProvider value) {
      provider = value;
    }

    public String getAccountId() {
      return accountId;
    }

    public void setAccountId(String value) {
      accountId = value;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String value) {
      token = value;
    }

    public String getApiVersion() {
      return apiVersion;
    }

    public void setApiVersion(String value) {
      apiVersion = value;
    }

    public Instant getExpiresAt() {
      return expiresAt;
    }

    public void setExpiresAt(Instant value) {
      expiresAt = value;
    }

    public boolean isApproved() {
      return approved;
    }

    public void setApproved(boolean value) {
      approved = value;
    }

    public Set<String> getScopes() {
      return scopes;
    }

    public void setScopes(Set<String> value) {
      scopes = value;
    }
  }
}

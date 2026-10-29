package com.cadence.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ClerkSecurityProperties.class, ApiCorsProperties.class})
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CadenceJwtAuthenticationConverter authenticationConverter,
      ApiSecurityErrors errors)
      throws Exception {
    // Authentication uses only an explicit Bearer header, never a browser session cookie.
    var chain =
        http.cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .sessionManagement(
                sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                requests ->
                    requests
                        .requestMatchers(
                            org.springframework.http.HttpMethod.GET,
                            "/api/v1/health",
                            "/api/v1/health/ready")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
            .exceptionHandling(
                handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(
                resourceServer ->
                    resourceServer
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
            .build();
    // Security 7.0 adds public resource discovery ahead of authorization, without a disable option.
    // Keep the reviewed API surface; all authentication/authorization filters remain in order.
    return new DefaultSecurityFilterChain(
        chain.getRequestMatcher(),
        chain.getFilters().stream()
            .filter(filter -> !(filter instanceof OAuth2ProtectedResourceMetadataFilter))
            .toList());
  }

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(ApiCorsProperties properties) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    configuration.setExposedHeaders(List.of("WWW-Authenticate", "X-Request-ID", "Retry-After"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    // Browser routes are explicitly scoped by workflow; this is not resource authorization.
    source.registerCorsConfiguration("/api/v1/workspaces/**", configuration);
    source.registerCorsConfiguration("/api/v1/brands/*", configuration);
    var identity = new CorsConfiguration(configuration);
    identity.setExposedHeaders(
        List.of("WWW-Authenticate", "Location", "X-Request-ID", "Retry-After"));
    for (String path :
        List.of(
            "/api/v1/brands/*/evidence",
            "/api/v1/brands/*/evidence/social",
            "/api/v1/brands/*/identities",
            "/api/v1/brands/*/identities/from-analysis",
            "/api/v1/brands/*/identities/from-evidence-analysis",
            "/api/v1/brands/*/analysis-jobs",
            "/api/v1/evidence/*/ai-admission",
            "/api/v1/evidence/*/ai-admission/history",
            "/api/v1/evidence/*/ai-admission/*/snapshot",
            "/api/v1/evidence/*/ai-admission/*/withdrawals",
            "/api/v1/evidence/*/document-review",
            "/api/v1/evidence/*/document-review/admissions",
            "/api/v1/evidence/*/document-review/admissions/*/snapshot",
            "/api/v1/evidence/*/url-review",
            "/api/v1/evidence/*/url-review/admissions",
            "/api/v1/evidence/*/url-review/admissions/*/snapshot",
            "/api/v1/evidence/*/social-review",
            "/api/v1/evidence/*/social-review/admissions",
            "/api/v1/evidence/*/social-review/admissions/*/snapshot",
            "/api/v1/identities/*",
            "/api/v1/identities/*/approval",
            "/api/v1/identities/*/evidence-provenance",
            "/api/v1/jobs/*",
            "/api/v1/analysis-results/*")) {
      var route = new CorsConfiguration(identity);
      boolean readOnly =
          path.endsWith("/snapshot")
              || path.endsWith("/document-review")
              || path.endsWith("/url-review")
              || path.endsWith("/social-review")
              || path.endsWith("/history")
              || path.endsWith("/evidence-provenance")
              || path.startsWith("/api/v1/jobs/")
              || path.startsWith("/api/v1/analysis-results/");
      boolean postOnly =
          path.endsWith("/admissions")
              || path.endsWith("/withdrawals")
              || path.endsWith("/approval")
              || path.endsWith("/from-analysis")
              || path.endsWith("/from-evidence-analysis")
              || path.endsWith("/analysis-jobs")
              || path.endsWith("/social");
      route.setAllowedMethods(
          path.equals("/api/v1/identities/*")
              ? List.of("GET", "PATCH")
              : readOnly ? List.of("GET") : postOnly ? List.of("POST") : List.of("GET", "POST"));
      if (path.endsWith("/analysis-jobs"))
        route.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
      source.registerCorsConfiguration(path, route);
    }
    source.registerCorsConfiguration("/**", new CorsConfiguration());
    return source;
  }

  @Bean
  ApiSecurityErrors apiSecurityErrors() {
    return new ApiSecurityErrors();
  }

  @Bean
  WebSecurityCustomizer apiFirewallErrors(ApiSecurityErrors errors) {
    return web -> web.requestRejectedHandler(errors);
  }

  @Bean
  JwtDecoder jwtDecoder(ClerkSecurityProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    validators.add(
        jwt ->
            jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : failure("Token expiration is required"));
    validators.add(audienceValidator(properties.audience()));
    if (!properties.authorizedParties().isEmpty()) {
      validators.add(authorizedPartyValidator(properties.authorizedParties()));
    }
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }

  private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
    return jwt ->
        jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
            ? OAuth2TokenValidatorResult.success()
            : failure("Invalid token audience");
  }

  private OAuth2TokenValidator<Jwt> authorizedPartyValidator(List<String> allowedParties) {
    return jwt ->
        jwt.getClaimAsString("azp") != null && allowedParties.contains(jwt.getClaimAsString("azp"))
            ? OAuth2TokenValidatorResult.success()
            : failure("Invalid token authorized party");
  }

  private OAuth2TokenValidatorResult failure(String message) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", message, null));
  }
}

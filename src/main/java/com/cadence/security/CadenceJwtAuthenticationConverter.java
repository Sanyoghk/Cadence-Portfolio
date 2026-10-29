package com.cadence.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CadenceJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final CadenceIdentityService identities;

  public CadenceJwtAuthenticationConverter(CadenceIdentityService identities) {
    this.identities = identities;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    CadencePrincipal principal = identities.resolve(jwt);
    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    return new JwtAuthenticationToken(jwt, authorities, principal.externalSubject()) {
      @Override
      public Object getPrincipal() {
        return principal;
      }
    };
  }
}

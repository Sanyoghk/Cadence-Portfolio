package com.cadence.security;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtDecoderTest {
  private HttpServer server;
  private RSAKey key;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() throws Exception {
    key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jwks",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          try (var output = exchange.getResponseBody()) {
            output.write(jwks);
          } finally {
            exchange.close();
          }
        });
    server.start();
    decoder =
        new SecurityConfiguration()
            .jwtDecoder(
                new ClerkSecurityProperties(
                    "https://issuer.example",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                    "cadence-api",
                    List.of("https://app.example")));
  }

  @AfterEach
  void tearDown() {
    if (server != null) server.stop(0);
  }

  private JWTClaimsSet.Builder claims() {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .subject("creator")
        .issuer("https://issuer.example")
        .audience("cadence-api")
        .claim("azp", "https://app.example")
        .issueTime(Date.from(now.minusSeconds(10)))
        .notBeforeTime(Date.from(now.minusSeconds(10)))
        .expirationTime(Date.from(now.plusSeconds(300)));
  }

  private String sign(JWTClaimsSet.Builder claims, RSAKey signingKey) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  @Test
  void acceptsValidSignatureAndClaims() throws Exception {
    assertEquals("creator", decoder.decode(sign(claims(), key)).getSubject());
  }

  @Test
  void rejectsInvalidSignature() throws Exception {
    String token = sign(claims(), new RSAKeyGenerator(2048).keyID("test-key").generate());
    assertThrows(JwtException.class, () -> decoder.decode(token));
  }

  @Test
  void rejectsWrongIssuerAudiencePartyAndTokenTimes() throws Exception {
    List<JWTClaimsSet.Builder> invalid =
        List.of(
            claims().issuer("https://attacker.example"),
            claims().audience("another-api"),
            claims().audience(List.of()),
            claims().claim("azp", "https://attacker.example"),
            claims().claim("azp", null),
            claims().expirationTime(null),
            claims().expirationTime(Date.from(Instant.now().minusSeconds(300))),
            claims().notBeforeTime(Date.from(Instant.now().plusSeconds(300))));
    for (var candidate : invalid) {
      String token = sign(candidate, key);
      assertThrows(JwtException.class, () -> decoder.decode(token));
    }
  }
}

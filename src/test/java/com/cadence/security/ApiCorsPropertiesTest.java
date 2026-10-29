package com.cadence.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiCorsPropertiesTest {
  @Test
  void defaultsToNoOriginsAndAllowsOnlyExplicitHttpsOrLoopback() {
    assertThat(new ApiCorsProperties(null).allowedOrigins()).isEmpty();
    assertThat(
            new ApiCorsProperties(
                    List.of(
                        "https://app.example.com",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://[::1]:3000"))
                .allowedOrigins())
        .hasSize(4);
    assertThatThrownBy(() -> new ApiCorsProperties(List.of("https://a.test", "https://a.test")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ApiCorsProperties(
                    java.util.stream.IntStream.range(0, 11)
                        .mapToObj(i -> "https://a" + i + ".test")
                        .toList()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "*",
        "null",
        "https://*.example.com",
        "http://app.example.com",
        "https://app.example.com/",
        "https://app.example.com/path",
        "https://user:pass@app.example.com",
        "https://app.example.com?token=x",
        "https://app.example.com#x",
        "https://app.example.com:0",
        "https://app.example.com:65536",
        "https://app.example.com\r\n",
        "file:///tmp",
        "//app.example.com"
      })
  void rejectsUnsafeOrigins(String origin) {
    assertThatThrownBy(() -> new ApiCorsProperties(List.of(origin)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

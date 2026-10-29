package com.cadence.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadence.security.MultipartAdmissionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

class ApiExceptionHandlerBodyTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void translatesParserWrappedBodyLimitTo413WithoutReflectingInput() {
    var error =
        new HttpMessageNotReadableException(
            "sensitive rejected input",
            new RuntimeException(new MultipartAdmissionFilter.BodyLimitExceededException()),
            new MockHttpInputMessage(new byte[0]));
    var result = handler.handleUnreadableBody(error);
    assertThat(result.getStatus()).isEqualTo(413);
    assertThat(result.getDetail()).isEqualTo("Request body exceeds configured limit");
  }

  @Test
  void malformedJsonReturnsFixed400WithoutParserDetails() {
    var error =
        new HttpMessageNotReadableException(
            "sensitive rejected input", new MockHttpInputMessage(new byte[0]));
    var result = handler.handleUnreadableBody(error);
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getDetail()).isEqualTo("Request body is malformed or unreadable");
  }
}

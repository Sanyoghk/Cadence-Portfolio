package com.cadence.security;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MultipartAdmissionFilterTest {
  private MockHttpServletRequest multipart() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/brands/id/evidence");
    request.setContentType("multipart/form-data; boundary=fixture");
    return request;
  }

  @Test
  void concurrentCapacityRejectsTheThirdRequestBeforeBodyParsingOrTheChain() throws Exception {
    MultipartAdmissionFilter filter = new MultipartAdmissionFilter(2);
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Void> admitted =
          () -> {
            filter.doFilter(
                multipart(),
                new MockHttpServletResponse(),
                (request, response) -> {
                  calls.incrementAndGet();
                  entered.countDown();
                  await(release);
                });
            return null;
          };
      var first = executor.submit(admitted);
      var second = executor.submit(admitted);
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var rejected =
            new MockHttpServletRequest("GET", "/encoded%2Felsewhere") {
              @Override
              public ServletInputStream getInputStream() {
                throw new AssertionError("Rejected request body was read");
              }

              @Override
              public String getParameter(String name) {
                throw new AssertionError("Rejected request parameters were parsed");
              }
            };
        rejected.setContentType("  MULTIPART/mixed; boundary=fixture  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(rejected, response, (request, result) -> calls.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString())
            .contains("\"retryable\":true", "\"status\":503")
            .doesNotContain("Semaphore", "capacity=", "localhost", "Exception");
        assertThat(calls).hasValue(2);
      } finally {
        release.countDown();
      }
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    MockHttpServletResponse after = new MockHttpServletResponse();
    filter.doFilter(multipart(), after, (request, response) -> calls.incrementAndGet());
    assertThat(after.getStatus()).isEqualTo(200);
    assertThat(calls).hasValue(3);
  }

  @Test
  void exceptionalDownstreamCompletionAlwaysReleasesItsPermit() throws Exception {
    MultipartAdmissionFilter filter = new MultipartAdmissionFilter(1);
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    multipart(),
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      throw new ServletException("downstream failure");
                    }))
        .isInstanceOf(ServletException.class)
        .hasMessage("downstream failure");
    var calls = new AtomicInteger();
    MockHttpServletResponse next = new MockHttpServletResponse();
    filter.doFilter(multipart(), next, (request, response) -> calls.incrementAndGet());
    assertThat(calls).hasValue(1);
    assertThat(next.getStatus()).isEqualTo(200);
  }

  @Test
  void jsonAndRequestsWithoutContentTypeRemainAvailableWhileMultipartCapacityIsFull()
      throws Exception {
    MultipartAdmissionFilter filter = new MultipartAdmissionFilter(1);
    var calls = new AtomicInteger();
    filter.doFilter(
        multipart(),
        new MockHttpServletResponse(),
        (request, response) -> {
          for (String contentType : List.of("application/json", "application/octet-stream")) {
            MockHttpServletRequest other = new MockHttpServletRequest("POST", "/elsewhere");
            other.setContentType(contentType);
            filter.doFilter(
                other, new MockHttpServletResponse(), (input, output) -> calls.incrementAndGet());
          }
          filter.doFilter(
              new MockHttpServletRequest("GET", "/elsewhere"),
              new MockHttpServletResponse(),
              (input, output) -> calls.incrementAndGet());
        });
    assertThat(calls).hasValue(3);
  }

  @ParameterizedTest
  @CsvSource({
    "PUT,/unrelated,MULTIPART/form-data",
    "PATCH,/api%2Fv1%2Fbrands%2Fid%2Fevidence,multipart/mixed",
    "DELETE,/api/v1/brands/id/evidence,MulTiPaRt/form-data",
    "HEAD,/elsewhere,multipart/alternative"
  })
  void admissionCannotBeBypassedThroughMethodPathOrMultipartSubtype(
      String method, String path, String contentType) throws Exception {
    MultipartAdmissionFilter filter = new MultipartAdmissionFilter(1);
    MockHttpServletResponse rejected = new MockHttpServletResponse();
    var calls = new AtomicInteger();
    filter.doFilter(
        multipart(),
        new MockHttpServletResponse(),
        (request, response) -> {
          MockHttpServletRequest other = new MockHttpServletRequest(method, path);
          other.setContentType(contentType);
          filter.doFilter(other, rejected, (input, output) -> calls.incrementAndGet());
        });
    assertThat(rejected.getStatus()).isEqualTo(503);
    assertThat(calls).hasValue(0);
  }

  @Test
  void configurationBoundsAndServletOrderPreserveAuthenticationBeforeAdmission() {
    assertThatThrownBy(() -> new MultipartAdmissionFilter(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MultipartAdmissionFilter(9))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new MultipartAdmissionFilter(1)).isNotNull();
    assertThat(new MultipartAdmissionFilter(8)).isNotNull();
    assertThat(new MultipartAdmissionFilter(2, 131072)).isNotNull();
    assertThat(new MultipartAdmissionFilter(2, 1048576)).isNotNull();
    assertThatThrownBy(() -> new MultipartAdmissionFilter(2, 131071))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MultipartAdmissionFilter(2, 1048577))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(MultipartAdmissionFilter.class.getAnnotation(Order.class).value())
        .isEqualTo(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
  }

  @Test
  void knownOversizedBodyIsRejectedBeforeReadingOrEnteringTheChain() throws Exception {
    var request =
        new MockHttpServletRequest("POST", "/unrelated") {
          @Override
          public long getContentLengthLong() {
            return 131073;
          }

          @Override
          public ServletInputStream getInputStream() {
            throw new AssertionError("Oversized declared body was read");
          }
        };
    request.setContentType("application/json");
    var response = new MockHttpServletResponse();
    var calls = new AtomicInteger();
    new MultipartAdmissionFilter(2)
        .doFilter(request, response, (input, output) -> calls.incrementAndGet());
    assertThat(calls).hasValue(0);
    assertTooLarge(response);
  }

  @Test
  void chunkedIgnoredJsonFieldsCannotConsumeMoreThanOneByteBeyondTheLimit() throws Exception {
    byte[] body = ("{\"ignored\":\"" + "x".repeat(200000) + "\"}").getBytes(StandardCharsets.UTF_8);
    var consumed = new AtomicInteger();
    var request = unknownLength(body, consumed);
    var response = new MockHttpServletResponse();
    new MultipartAdmissionFilter(2)
        .doFilter(
            request,
            response,
            (input, output) -> {
              new ObjectMapper().readTree(((HttpServletRequest) input).getInputStream());
            });
    assertTooLarge(response);
    assertThat(consumed).hasValue(131073);
  }

  @Test
  void exactBodyLimitRemainsReadableAndTheRequestStreamCannotBeReopened() throws Exception {
    byte[] body = new byte[131072];
    Arrays.fill(body, (byte) 'a');
    var request = new MockHttpServletRequest("POST", "/elsewhere");
    request.setContentType("application/octet-stream");
    request.setContent(body);
    var response = new MockHttpServletResponse();
    new MultipartAdmissionFilter(2)
        .doFilter(
            request,
            response,
            (input, output) -> {
              var stream = ((HttpServletRequest) input).getInputStream();
              assertThat(stream.readAllBytes()).containsExactly(body);
              assertThat(((HttpServletRequest) input).getInputStream()).isSameAs(stream);
              assertThat(stream.read()).isEqualTo(-1);
            });
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void jsonWithTwentyThousandEscapedUtf16CharactersFitsTheDefaultWireLimit() throws Exception {
    String body = "{\"content\":\"" + "\\u0430".repeat(20000) + "\"}";
    var request = new MockHttpServletRequest("POST", "/api/v1/brands/id/evidence");
    request.setContentType("application/json");
    request.setCharacterEncoding("UTF-8");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse();
    new MultipartAdmissionFilter(2)
        .doFilter(
            request,
            response,
            (input, output) -> {
              var parsed = new ObjectMapper().readTree(((HttpServletRequest) input).getReader());
              assertThat(parsed.get("content").textValue()).isEqualTo("а".repeat(20000));
              assertThatThrownBy(() -> ((HttpServletRequest) input).getInputStream())
                  .isInstanceOf(IllegalStateException.class);
            });
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void unknownLengthReaderUsesTheSameBoundAndDoesNotExposeTheSubmittedBody() throws Exception {
    byte[] body = ("private-body-marker" + "x".repeat(140000)).getBytes(StandardCharsets.UTF_8);
    var consumed = new AtomicInteger();
    var request = unknownLength(body, consumed);
    var response = new MockHttpServletResponse();
    new MultipartAdmissionFilter(2)
        .doFilter(
            request,
            response,
            (input, output) -> {
              var reader = ((HttpServletRequest) input).getReader();
              char[] buffer = new char[32768];
              while (reader.read(buffer) >= 0) {}
            });
    assertTooLarge(response);
    assertThat(response.getContentAsString()).doesNotContain("private-body-marker");
    assertThat(consumed).hasValue(131073);
  }

  @Test
  void skipAndRepeatedReadsCannotBypassTheBodyLimit() throws Exception {
    var consumed = new AtomicInteger();
    var request = unknownLength(new byte[200000], consumed);
    var response = new MockHttpServletResponse();
    new MultipartAdmissionFilter(2)
        .doFilter(
            request,
            response,
            (input, output) -> {
              var stream = ((HttpServletRequest) input).getInputStream();
              assertThat(stream.skip(131072)).isEqualTo(131072);
              assertThatThrownBy(() -> stream.skip(1))
                  .isInstanceOf(MultipartAdmissionFilter.BodyLimitExceededException.class)
                  .hasMessage("Request body exceeds configured limit");
              assertThatThrownBy(stream::read)
                  .isInstanceOf(MultipartAdmissionFilter.BodyLimitExceededException.class);
              assertThatThrownBy(() -> stream.read(new byte[1000000]))
                  .isInstanceOf(MultipartAdmissionFilter.BodyLimitExceededException.class);
            });
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(consumed).hasValue(131073);
  }

  private static void assertTooLarge(MockHttpServletResponse response) throws Exception {
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(response.getContentAsString())
        .contains("\"status\":413", "\"retryable\":false")
        .doesNotContain("Exception", "configured limit=", "localhost", "C:\\");
  }

  private static MockHttpServletRequest unknownLength(byte[] body, AtomicInteger consumed) {
    var bytes = new ByteArrayInputStream(body);
    ServletInputStream stream =
        new ServletInputStream() {
          @Override
          public int read() {
            int value = bytes.read();
            if (value >= 0) consumed.incrementAndGet();
            return value;
          }

          @Override
          public int read(byte[] buffer, int offset, int length) {
            int count = bytes.read(buffer, offset, length);
            if (count > 0) consumed.addAndGet(count);
            return count;
          }

          @Override
          public boolean isFinished() {
            return bytes.available() == 0;
          }

          @Override
          public boolean isReady() {
            return true;
          }

          @Override
          public void setReadListener(ReadListener listener) {}
        };
    var request =
        new MockHttpServletRequest("POST", "/elsewhere") {
          @Override
          public int getContentLength() {
            return -1;
          }

          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public ServletInputStream getInputStream() {
            return stream;
          }
        };
    request.setContentType("application/json");
    request.setCharacterEncoding("UTF-8");
    return request;
  }

  private static void await(CountDownLatch latch) throws ServletException {
    try {
      if (!latch.await(10, TimeUnit.SECONDS))
        throw new ServletException("Admission fixture timed out");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ServletException("Admission fixture interrupted");
    }
  }
}

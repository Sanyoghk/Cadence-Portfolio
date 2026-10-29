package com.cadence.infrastructure.ingest;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.WebsiteExtractionFailure;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JsoupWebsiteMetadataExtractorTest {
  private final JsoupWebsiteMetadataExtractor extractor = new JsoupWebsiteMetadataExtractor();

  private com.cadence.domain.WebsiteMetadata html(String value) {
    return extractor.extract(value.getBytes(StandardCharsets.UTF_8), "text/html");
  }

  @Test
  void extractsNormalizedMetadataWithoutActiveContent() {
    var result =
        html(
            """
        <!doctype html><html><head><title>  Acme &amp; Studio </title>
        <meta name="description" content="Made   for creators">
        <script src="http://127.0.0.1/private">secret-script</script></head><body>
        <style>secret-style</style><h1>Hello</h1><p>World &amp; creators</p>
        <iframe src="http://169.254.169.254/">secret-frame</iframe>
        <form>secret-form</form><div hidden>secret-hidden</div>
        <svg><text>secret-svg</text></svg></body></html>
        """);
    assertThat(result.title()).isEqualTo("Acme & Studio");
    assertThat(result.description()).isEqualTo("Made for creators");
    assertThat(result.text()).isEqualTo("Hello World & creators");
    assertThat(result.version()).isEqualTo("website-v2");
    assertThat(result.warnings()).contains("UTF8_ASSUMED");
  }

  @Test
  void decodesExplicitUnicodeBomsWithoutExecutingActiveContent() {
    String markup = "<title>Бренд 😀</title><p>Привет café</p><script>secret</script>";
    var charsets =
        new java.nio.charset.Charset[] {
          StandardCharsets.UTF_8, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE
        };
    byte[][] boms = {
      {(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
      {(byte) 0xff, (byte) 0xfe},
      {(byte) 0xfe, (byte) 0xff}
    };
    String[] warnings = {"UTF8_BOM", "UTF16LE_BOM", "UTF16BE_BOM"};
    for (int i = 0; i < charsets.length; i++) {
      var result = extractor.extract(withBom(boms[i], markup.getBytes(charsets[i])), "text/html");
      assertThat(result.title()).isEqualTo("Бренд 😀");
      assertThat(result.text()).isEqualTo("Привет café");
      assertThat(result.warnings()).contains(warnings[i]).doesNotContain("UTF8_ASSUMED");
      assertThat(result.version()).isEqualTo("website-v2");
    }
  }

  @Test
  void rejectsMalformedUtf16AndUnsupportedUtf32WithoutReplacementCharacters() {
    byte[][] invalid = {
      {(byte) 0xff, (byte) 0xfe, 0x41},
      {(byte) 0xfe, (byte) 0xff, (byte) 0xd8, 0x00},
      {(byte) 0xff, (byte) 0xfe, 0x00, (byte) 0xdc},
      {(byte) 0xff, (byte) 0xfe, 0x00, 0x00, 0x41, 0x00, 0x00, 0x00},
      {0x00, 0x00, (byte) 0xfe, (byte) 0xff, 0x00, 0x00, 0x00, 0x41},
      {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, (byte) 0xff}
    };
    for (byte[] bytes : invalid) {
      assertThatThrownBy(() -> extractor.extract(bytes, "text/plain"))
          .isInstanceOf(WebsiteExtractionFailure.class)
          .hasMessage("Website metadata could not be extracted");
    }
  }

  @Test
  void preservesUtf16PlainTextAndBoundsSupplementaryCharacters() {
    byte[] bom = {(byte) 0xff, (byte) 0xfe};
    var literal =
        extractor.extract(
            withBom(bom, "<script>literal</script>".getBytes(StandardCharsets.UTF_16LE)),
            "text/plain");
    assertThat(literal.text()).isEqualTo("<script>literal</script>");
    var bounded =
        extractor.extract(
            withBom(bom, "😀".repeat(9000).getBytes(StandardCharsets.UTF_16LE)), "text/plain");
    assertThat(bounded.text()).hasSize(16000);
    assertThat(bounded.text().codePointCount(0, bounded.text().length())).isEqualTo(8000);
    assertThat(bounded.warnings()).contains("TEXT_TRUNCATED");
    assertThat(extractor.extract(bom, "text/plain").warnings()).contains("NO_TEXT");
    assertThatThrownBy(
            () -> extractor.extract(withBom(bom, new byte[2 * 1024 * 1024]), "text/plain"))
        .isInstanceOf(WebsiteExtractionFailure.class);
  }

  private byte[] withBom(byte[] bom, byte[] bytes) {
    byte[] result = java.util.Arrays.copyOf(bom, bom.length + bytes.length);
    System.arraycopy(bytes, 0, result, bom.length, bytes.length);
    return result;
  }

  @Test
  void rendererUsesStrictUtf8TranscodingAndEscapesPlainTextMarkup() {
    byte[] utf16 =
        withBom(
            new byte[] {(byte) 0xff, (byte) 0xfe},
            "<title>Бренд 😀</title>".getBytes(StandardCharsets.UTF_16LE));
    assertThat(new String(extractor.decodeForRendering(utf16, "text/html"), StandardCharsets.UTF_8))
        .isEqualTo("<title>Бренд 😀</title>");
    String literal =
        new String(
            extractor.decodeForRendering(
                "<img src=http://127.0.0.1/>".getBytes(StandardCharsets.UTF_8), "text/plain"),
            StandardCharsets.UTF_8);
    assertThat(literal).contains("&lt;img").doesNotContain("<img");
    byte[] expansion =
        withBom(
            new byte[] {(byte) 0xff, (byte) 0xfe},
            "界".repeat(700_000).getBytes(StandardCharsets.UTF_16LE));
    assertThatThrownBy(() -> extractor.decodeForRendering(expansion, "text/html"))
        .isInstanceOf(WebsiteExtractionFailure.class);
  }

  @Test
  void boundsFieldsAndDoesNotSplitSupplementaryCharacters() {
    var result =
        html(
            "<title>"
                + "x".repeat(400)
                + "</title><meta name=description content='"
                + "d".repeat(2100)
                + "'><p>"
                + "😀".repeat(9000));
    assertThat(result.title()).hasSize(300);
    assertThat(result.description()).hasSize(2000);
    assertThat(result.text()).hasSize(16000);
    assertThat(result.text().codePointCount(0, result.text().length())).isEqualTo(8000);
    assertThat(result.warnings()).contains("TEXT_TRUNCATED");
  }

  @Test
  void rejectsOversizedMalformedEncodingAndUnsupportedMedia() {
    assertThatThrownBy(() -> extractor.extract(new byte[2 * 1024 * 1024 + 1], "text/html"))
        .isInstanceOf(WebsiteExtractionFailure.class);
    assertThatThrownBy(() -> extractor.extract(new byte[] {(byte) 0xff}, "text/html"))
        .isInstanceOf(WebsiteExtractionFailure.class)
        .hasMessage("Website metadata could not be extracted");
    assertThatThrownBy(() -> extractor.extract(new byte[0], "application/xml"))
        .isInstanceOf(WebsiteExtractionFailure.class);
    assertThatThrownBy(() -> extractor.extract(null, "text/plain"))
        .isInstanceOf(WebsiteExtractionFailure.class);
  }

  @Test
  void boundsDeepMarkupAndNeverExpandsExternalEntities() {
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          var result =
              html(
                  "<!DOCTYPE html [<!ENTITY x SYSTEM 'file:///never-read'>]>"
                      + "<div>".repeat(2000)
                      + "hello &x;"
                      + "</div>".repeat(2000));
          assertThat(result.text()).contains("hello").doesNotContain("file://");
        });
  }

  @Test
  void preservesPlainTextAsUntrustedTextAndReportsEmptyInput() {
    var result =
        extractor.extract(
            "a\u0000\n b <script>literal</script>".getBytes(StandardCharsets.UTF_8), "text/plain");
    assertThat(result.text()).isEqualTo("a b <script>literal</script>");
    assertThat(html("<script>only code</script>").warnings()).contains("NO_TEXT");
    assertThat(html("<meta property='og:description' content='fallback'>").description())
        .isEqualTo("fallback");
  }
}

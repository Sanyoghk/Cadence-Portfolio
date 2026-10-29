package com.cadence.infrastructure.ingest;

import com.cadence.application.ports.WebsiteExtractionFailure;
import com.cadence.application.ports.WebsiteMetadataExtractor;
import com.cadence.domain.WebsiteMetadata;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

@Component
public class JsoupWebsiteMetadataExtractor implements WebsiteMetadataExtractor {
  private static final int MAX_BYTES = 2 * 1024 * 1024;

  @Override
  public WebsiteMetadata extract(byte[] content, String mediaType) {
    if (content == null
        || content.length > MAX_BYTES
        || !("text/html".equals(mediaType) || "text/plain".equals(mediaType)))
      throw new WebsiteExtractionFailure();
    List<String> warnings = new ArrayList<>();
    String input = decode(content, warnings);
    String title = "", description = "", text = input;
    if ("text/html".equals(mediaType)) {
      Parser parser = Parser.htmlParser().setMaxDepth(128).setTrackErrors(1);
      Document document = parser.parseInput(input, "");
      if (!parser.getErrors().isEmpty()) warnings.add("HTML_RECOVERED");
      title = document.title();
      Element meta = document.selectFirst("meta[name=description]");
      if (meta == null) meta = document.selectFirst("meta[property=og:description]");
      if (meta != null) description = meta.attr("content");
      // Do not execute HTML, fetch subresources, or interpret CSS visibility as a security
      // boundary.
      document
          .select("script,style,noscript,template,iframe,object,embed,svg,math,form,[hidden]")
          .remove();
      text = document.body().text();
    }
    title = bounded(title, 300, warnings);
    description = bounded(description, 2000, warnings);
    text = bounded(text, 16000, warnings);
    if (text.isBlank()) warnings.add("NO_TEXT");
    return new WebsiteMetadata(title, description, text, String.join(";", warnings), "website-v2");
  }

  private String decode(byte[] content, List<String> warnings) {
    // Reject UTF-32 before UTF-16: its little-endian BOM begins with the UTF-16LE BOM.
    if (startsWith(content, 0xff, 0xfe, 0x00, 0x00) || startsWith(content, 0x00, 0x00, 0xfe, 0xff))
      throw new WebsiteExtractionFailure();
    Charset charset = StandardCharsets.UTF_8;
    int offset = 0;
    String warning = "UTF8_ASSUMED";
    if (startsWith(content, 0xef, 0xbb, 0xbf)) {
      offset = 3;
      warning = "UTF8_BOM";
    } else if (startsWith(content, 0xff, 0xfe)) {
      charset = StandardCharsets.UTF_16LE;
      offset = 2;
      warning = "UTF16LE_BOM";
    } else if (startsWith(content, 0xfe, 0xff)) {
      charset = StandardCharsets.UTF_16BE;
      offset = 2;
      warning = "UTF16BE_BOM";
    }
    try {
      String input =
          charset
              .newDecoder()
              .decode(ByteBuffer.wrap(content, offset, content.length - offset))
              .toString();
      warnings.add(warning);
      return input;
    } catch (CharacterCodingException invalidEncoding) {
      throw new WebsiteExtractionFailure();
    }
  }

  @Override
  public byte[] decodeForRendering(byte[] content, String mediaType) {
    if (content == null
        || content.length > MAX_BYTES
        || !("text/html".equals(mediaType) || "text/plain".equals(mediaType)))
      throw new WebsiteExtractionFailure();
    String decoded = decode(content, new ArrayList<>());
    if ("text/plain".equals(mediaType)) {
      var document = org.jsoup.Jsoup.parse("");
      document.body().appendElement("pre").text(decoded);
      decoded = document.outerHtml();
    }
    byte[] encoded = decoded.getBytes(StandardCharsets.UTF_8);
    if (encoded.length > MAX_BYTES) throw new WebsiteExtractionFailure();
    return encoded;
  }

  private boolean startsWith(byte[] content, int... prefix) {
    if (content.length < prefix.length) return false;
    for (int i = 0; i < prefix.length; i++) {
      if (Byte.toUnsignedInt(content[i]) != prefix[i]) return false;
    }
    return true;
  }

  private String bounded(String value, int limit, List<String> warnings) {
    StringBuilder result = new StringBuilder();
    boolean space = false;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (Character.isWhitespace(codePoint)
          || Character.isSpaceChar(codePoint)
          || Character.isISOControl(codePoint)
          || Character.getType(codePoint) == Character.FORMAT) {
        space = !result.isEmpty();
        continue;
      }
      int needed = Character.charCount(codePoint) + (space ? 1 : 0);
      if (result.length() + needed > limit) {
        if (!warnings.contains("TEXT_TRUNCATED")) warnings.add("TEXT_TRUNCATED");
        break;
      }
      if (space) result.append(' ');
      result.appendCodePoint(codePoint);
      space = false;
    }
    return result.toString();
  }
}

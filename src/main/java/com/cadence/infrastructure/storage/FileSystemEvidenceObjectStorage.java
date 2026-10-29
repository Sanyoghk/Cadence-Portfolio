package com.cadence.infrastructure.storage;

import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.QuarantineReadFailure;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !s3 & !prod")
public class FileSystemEvidenceObjectStorage
    implements EvidenceObjectStorage, com.cadence.application.ports.EvidenceObjectLifecycleStorage {

  private final Path root;

  public FileSystemEvidenceObjectStorage(
      @Value("${cadence.evidence.storage-root:var/evidence}") String storageRoot) {
    root = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public String storeQuarantined(String objectKey, byte[] content) {
    return store(objectKey, content, "quarantine");
  }

  private String store(String objectKey, byte[] content, String namespace) {
    Path target = resolveKey(objectKey, namespace);
    if (content == null || content.length == 0 || content.length > MAX_QUARANTINE_BYTES)
      throw new IllegalArgumentException("Invalid evidence object size");
    try {
      Files.createDirectories(root);
      requireDirectory(root);
      Path directory = root;
      for (Path segment : root.relativize(target.getParent())) {
        directory = directory.resolve(segment);
        try {
          Files.createDirectory(directory);
        } catch (FileAlreadyExistsException existing) {
          // Validate existing directories before traversing them, including Windows junctions.
        }
        requireDirectory(directory);
      }
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegularFile(target);
      Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      try {
        Files.write(temporary, content);
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
      return namespace + ":" + root.relativize(target).toString().replace('\\', '/');
    } catch (IOException | SecurityException exception) {
      throw new IllegalStateException("Evidence file could not be stored");
    }
  }

  @Override
  public byte[] readQuarantined(String reference, int maxBytes) {
    return read(reference, maxBytes, "quarantine");
  }

  private byte[] read(String reference, int maxBytes, String namespace) {
    if (maxBytes < 1 || maxBytes > MAX_QUARANTINE_BYTES)
      throw new QuarantineReadFailure(Code.SIZE_LIMIT);
    if (reference == null || !reference.startsWith(namespace + ":"))
      throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    Path target = resolveKey(reference.substring(namespace.length() + 1), namespace);
    try {
      requireDirectory(root);
      Path directory = root;
      for (Path segment : root.relativize(target.getParent())) {
        directory = directory.resolve(segment);
        requireDirectory(directory);
      }
      requireRegularFile(target);
      try (FileChannel channel =
          FileChannel.open(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
        if (channel.size() > maxBytes) throw new QuarantineReadFailure(Code.SIZE_LIMIT);
        byte[] content = Channels.newInputStream(channel).readNBytes(maxBytes + 1);
        if (content.length > maxBytes) throw new QuarantineReadFailure(Code.SIZE_LIMIT);
        if (content.length == 0) throw new QuarantineReadFailure(Code.INVALID_CONTENT);
        return content;
      }
    } catch (NoSuchFileException missing) {
      throw new QuarantineReadFailure(Code.NOT_FOUND);
    } catch (IOException | SecurityException | UnsupportedOperationException failure) {
      throw new QuarantineReadFailure(Code.UNAVAILABLE);
    }
  }

  public void storeMedia(com.cadence.domain.MediaObjectKey key, byte[] content) {
    key.verify(content);
    store(key.key(), content, "media");
  }

  public byte[] readMedia(com.cadence.domain.MediaObjectKey key, int maxBytes) {
    return read("media:" + key.key(), maxBytes, "media");
  }

  private Path resolveKey(String key) {
    return resolveKey(key, "quarantine");
  }

  private Path resolveKey(String key, String namespace) {
    if (key == null || key.length() > 400 || !key.startsWith(namespace + "/"))
      throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    for (String segment : key.split("/", -1)) {
      if (!segment.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")
          || segment.length() > 128
          || segment
              .toUpperCase(Locale.ROOT)
              .matches("(?:CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\..*)?"))
        throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    }
    Path target = root.resolve(key).normalize();
    if (!target.startsWith(root)) throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    return target;
  }

  @Override
  public void deleteQuarantined(String reference) {
    if (reference == null || !reference.startsWith("quarantine:quarantine/writes/"))
      throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    Path target = resolveKey(reference.substring("quarantine:".length()));
    try {
      requireDirectory(root);
      Path directory = root;
      for (Path segment : root.relativize(target.getParent())) {
        directory = directory.resolve(segment);
        requireDirectory(directory);
      }
      requireRegularFile(target);
      Files.delete(target);
    } catch (NoSuchFileException missing) {
      // Never-reused managed generations: absence is already the desired result.
    } catch (IOException | SecurityException failure) {
      throw new QuarantineReadFailure(Code.UNAVAILABLE);
    }
  }

  private void requireDirectory(Path path) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isDirectory()
        || attributes.isSymbolicLink()
        || attributes.isOther()
        || !path.toRealPath().equals(path)) throw new QuarantineReadFailure(Code.UNSAFE_PATH);
  }

  private void requireRegularFile(Path path) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isRegularFile()
        || attributes.isSymbolicLink()
        || attributes.isOther()
        || !path.toRealPath().equals(path)) throw new QuarantineReadFailure(Code.UNSAFE_PATH);
  }
}

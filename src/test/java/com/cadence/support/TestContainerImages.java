package com.cadence.support;

import java.nio.file.Path;
import java.util.concurrent.Future;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/** Build the same reviewed distribution patches used by the local integration stack. */
public final class TestContainerImages {
  private TestContainerImages() {}

  private static boolean postgresPrepared;

  private static synchronized void preparePostgres() {
    if (!postgresPrepared) {
      new ImageFromDockerfile("cadence-postgres:integration", false)
          .withFileFromPath("Dockerfile", Path.of("docker/postgres/Dockerfile"))
          .get();
      postgresPrepared = true;
    }
  }

  /**
   * Lazy start preserves the existing Docker availability / explicit disposable-server condition.
   */
  public static PostgreSQLContainer<?> postgres() {
    return new ReviewedPostgres();
  }

  private static final class ReviewedPostgres extends PostgreSQLContainer<ReviewedPostgres> {
    ReviewedPostgres() {
      super(
          DockerImageName.parse("cadence-postgres:integration")
              .asCompatibleSubstituteFor("postgres"));
    }

    @Override
    public void start() {
      preparePostgres();
      super.start();
    }
  }

  public static Future<String> clamav() {
    return new ImageFromDockerfile("cadence-clamav:integration", false)
        .withFileFromPath("Dockerfile", Path.of("docker/clamav/Dockerfile"));
  }

  public static Future<String> minio() {
    var image = new ImageFromDockerfile("cadence-minio:integration", false);
    for (var name :
        new String[] {
          "Dockerfile", "server.go.mod", "server.go.sum", "client.go.mod", "client.go.sum"
        }) {
      image.withFileFromPath(name, Path.of("docker/minio", name));
    }
    return image;
  }
}

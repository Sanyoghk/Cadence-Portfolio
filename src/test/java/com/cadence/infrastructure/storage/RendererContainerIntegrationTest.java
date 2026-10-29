package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.EvidenceRenderFailure;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Capability;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerStatus;

@Testcontainers
class RendererContainerIntegrationTest {
  private static final String TOKEN =
      UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
  private static final Path DIRECTORY = Path.of("docker", "evidence-renderer");
  private static final ImageFromDockerfile IMAGE =
      new ImageFromDockerfile()
          .withFileFromPath("Dockerfile", DIRECTORY.resolve("Dockerfile"))
          .withFileFromPath("requirements.txt", DIRECTORY.resolve("requirements.txt"))
          .withFileFromPath("server.py", DIRECTORY.resolve("server.py"))
          .withFileFromPath("child.py", DIRECTORY.resolve("child.py"))
          .withFileFromPath("smoke.py", DIRECTORY.resolve("smoke.py"))
          .withFileFromPath("namespace_probe.py", DIRECTORY.resolve("namespace_probe.py"))
          .withFileFromPath("test_probe.py", DIRECTORY.resolve("test_probe.py"))
          .withFileFromPath("ephemeral.py", DIRECTORY.resolve("ephemeral.py"))
          .withFileFromPath("test_ephemeral.py", DIRECTORY.resolve("test_ephemeral.py"))
          .withFileFromPath("test_server.py", DIRECTORY.resolve("test_server.py"));

  @Container
  static final GenericContainer<?> RENDERER =
      hardened(false, RendererContainerSupport.securityOptions(seccomp()))
          .withExposedPorts(8081)
          .waitingFor(
              Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(4)));

  private static GenericContainer<?> hardened(boolean init, List<String> securityOptions) {
    return new GenericContainer<>(IMAGE)
        .withEnv("CADENCE_RENDERER_TOKEN", TOKEN)
        .withCreateContainerCmdModifier(
            command ->
                command
                    .getHostConfig()
                    .withInit(init)
                    .withCapDrop(Capability.ALL)
                    .withReadonlyRootfs(true)
                    .withMemory(2L * 1024 * 1024 * 1024)
                    .withNanoCPUs(1_000_000_000L)
                    .withPidsLimit(256L)
                    .withShmSize(256L * 1024 * 1024)
                    .withTmpFs(
                        Map.of(
                            "/tmp", "rw,noexec,nosuid,nodev,size=128m",
                            "/dev/mqueue", "ro,noexec,nosuid,nodev,size=4k,mode=000"))
                    .withSecurityOpts(securityOptions));
  }

  @BeforeAll
  static void verifyNamespaceBoundary() throws Exception {
    RendererContainerSupport.verifyNamespaceBoundary(RENDERER);
  }

  @Test
  void realChromiumScreenshotDoesNotExecuteScriptsOrFetchEmbeddedUrls() throws Exception {
    try (ServerSocket spy = new ServerSocket(0);
        var adapter = adapter(RENDERER, TOKEN)) {
      spy.setSoTimeout(500);
      byte[] plain =
          adapter
              .screenshot(
                  "<html><body><h1>Website evidence</h1></body></html>"
                      .getBytes(StandardCharsets.UTF_8))
              .bytes();
      String hostile =
          "<html><body><h1>Website evidence</h1><script>document.body.remove();fetch('http://host.docker.internal:"
              + spy.getLocalPort()
              + "/secret')</script><img style='display:none' src='http://host.docker.internal:"
              + spy.getLocalPort()
              + "/metadata'></body></html>";
      var result = adapter.screenshot(hostile.getBytes(StandardCharsets.UTF_8));
      assertThat(result.bytes()).isEqualTo(plain);
      assertThat(result.width()).isEqualTo(1280);
      assertThat(result.height()).isEqualTo(720);
      assertThatThrownBy(spy::accept).isInstanceOf(SocketTimeoutException.class);
    }
  }

  @Test
  void realPopplerExtractsTextFromValidPdfAndRejectsMalformedPdf() {
    try (var adapter = adapter(RENDERER, TOKEN)) {
      var result = adapter.extractPdf(pdf("Document evidence"));
      assertThat(result.text()).isEqualTo("Document evidence");
      assertThat(result.version()).isEqualTo("isolated-v1");
      assertThatThrownBy(
              () -> adapter.extractPdf("%PDF-broken".getBytes(StandardCharsets.US_ASCII)))
          .isInstanceOf(EvidenceRenderFailure.class)
          .hasMessage("Evidence rendering failed");
    }
  }

  @Test
  void privateApiRejectsWrongToken() {
    try (var adapter = adapter(RENDERER, UUID.randomUUID().toString().replace("-", ""))) {
      assertThatThrownBy(() -> adapter.extractPdf(pdf("Document")))
          .isInstanceOf(EvidenceRenderFailure.class);
    }
  }

  @Test
  void pythonSecurityBoundarySuitePassesWithRealLinuxFileDescriptors() throws Exception {
    var result =
        RENDERER.execInContainer(
            "python3",
            "-I",
            "-m",
            "unittest",
            "discover",
            "-s",
            "/opt/evidence-renderer",
            "-p",
            "test_*.py");
    assertThat(result.getExitCode())
        .withFailMessage("Renderer Python security boundary suite failed")
        .isZero();
    assertThat(result.getStderr()).contains("OK").doesNotContain("skipped");
    var count = java.util.regex.Pattern.compile("Ran ([0-9]+) tests").matcher(result.getStderr());
    assertThat(count.find()).as("Renderer security suite discovered tests").isTrue();
    assertThat(Integer.parseInt(count.group(1))).isGreaterThanOrEqualTo(10);
  }

  @Test
  void missingNamespacePermissionsFailClosedWithoutUnsafeFallback() {
    var securityOptions =
        RendererContainerSupport.securityOptions(seccomp()).stream()
            .filter(option -> !option.startsWith("seccomp="))
            .toList();
    assertStartupRejected(
        hardened(false, securityOptions), "namespace-probe failed: userns-or-seccomp");
  }

  @Test
  void initWrapperIsRejectedBeforeTokenReadOrParserAdmission() {
    assertStartupRejected(
        hardened(true, RendererContainerSupport.securityOptions(seccomp())),
        "Invalid renderer process boundary");
  }

  private static void assertStartupRejected(GenericContainer<?> container, String expectedReason) {
    var rejectedWithExitOne = new AtomicBoolean();
    var rejectedWithExpectedReason = new AtomicBoolean();
    container
        .withStartupAttempts(1)
        .withStartupCheckStrategy(
            new StartupCheckStrategy() {
              @Override
              public StartupStatus checkStartupState(DockerClient client, String containerId) {
                var state = getCurrentState(client, containerId);
                if (!DockerStatus.isContainerStopped(state)) return StartupStatus.NOT_YET_KNOWN;
                rejectedWithExitOne.set(Long.valueOf(1).equals(state.getExitCodeLong()));
                rejectedWithExpectedReason.set(container.getLogs().contains(expectedReason));
                return StartupStatus.FAILED;
              }
            }.withTimeout(Duration.ofSeconds(10)));
    try (container) {
      assertThatThrownBy(container::start)
          .isInstanceOf(org.testcontainers.containers.ContainerLaunchException.class);
      assertThat(rejectedWithExitOne.get())
          .as("Renderer stopped with exit code 1 before parser admission")
          .isTrue();
      assertThat(rejectedWithExpectedReason.get())
          .as("Renderer startup rejected the specific unsafe configuration")
          .isTrue();
    }
  }

  private static RendererEvidenceAdapter adapter(GenericContainer<?> container, String token) {
    return new RendererEvidenceAdapter(
        new RendererProperties(
            container.getHost(),
            container.getMappedPort(8081),
            List.of("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"),
            token,
            Duration.ofSeconds(20),
            true));
  }

  private static String seccomp() {
    try {
      return Files.readString(DIRECTORY.resolve("seccomp.json"));
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Missing renderer seccomp profile");
    }
  }

  private static byte[] pdf(String text) {
    var output = new ByteArrayOutputStream();
    List<Integer> offsets = new ArrayList<>();
    output.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
    String stream = "BT /F1 12 Tf 50 700 Td (" + text + ") Tj ET";
    List<String> objects =
        List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream");
    for (int index = 0; index < objects.size(); index++) {
      offsets.add(output.size());
      output.writeBytes(
          ((index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n")
              .getBytes(StandardCharsets.US_ASCII));
    }
    int xref = output.size();
    output.writeBytes("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
    for (int offset : offsets)
      output.writeBytes(
          String.format(Locale.ROOT, "%010d 00000 n \n", offset)
              .getBytes(StandardCharsets.US_ASCII));
    output.writeBytes(
        ("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII));
    return output.toByteArray();
  }
}

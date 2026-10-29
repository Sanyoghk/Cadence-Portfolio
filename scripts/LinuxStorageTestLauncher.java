import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Runs the existing complete filesystem contract, including Linux-only link tests. */
public final class LinuxStorageTestLauncher {
  public static void main(String[] args) {
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClass("com.cadence.infrastructure.storage.FileSystemEvidenceObjectStorageTest"))
        .build());
    var summary = listener.getSummary();
    summary.printTo(new PrintWriter(System.out, true));
    summary.printFailuresTo(new PrintWriter(System.out, true));
    if (summary.getTestsFoundCount() == 0 || summary.getTestsSkippedCount() != 0
        || summary.getTestsFailedCount() != 0
        || summary.getTestsFoundCount() != summary.getTestsSucceededCount()) System.exit(1);
  }
}

package dev.vlearning.reliability.profiling;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import org.springframework.stereotype.Component;

/**
 * Renders a settlement report. Two things in here are wrong, and neither is
 * visible in a metric: the whole class shares one lock, and laying out a report
 * allocates roughly 25 MB it immediately throws away.
 *
 * <p>Step 5 is about finding both from a recording rather than from reading this
 * file — which is why the lesson text does not tell you which methods they are in.
 */
@Component
public class ReportRenderer {

    private static final int PAGES = 400;
    private static final int PAGE_BYTES = 64 * 1024;

    private final Object renderLock = new Object();
    private final ChaosSwitch chaos;
    private final AtomicLong rendered = new AtomicLong();

    public ReportRenderer(ChaosSwitch chaos) {
        this.chaos = chaos;
    }

    public byte[] render(String reportId) {
        long checksum = layout(reportId);
        synchronized (renderLock) {
            return stamp(reportId, checksum, chaos.lockHold());
        }
    }

    public long renderCount() {
        return rendered.get();
    }

    private long layout(String reportId) {
        long checksum = reportId.hashCode();
        for (int page = 0; page < PAGES; page++) {
            byte[] buffer = new byte[PAGE_BYTES];
            Arrays.fill(buffer, (byte) (reportId.hashCode() + page));
            checksum += buffer[page % PAGE_BYTES] + buffer[PAGE_BYTES - 1];
        }
        return checksum;
    }

    private byte[] stamp(String reportId, long checksum, Duration hold) {
        try {
            Thread.sleep(hold);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        rendered.incrementAndGet();
        return ("REPORT " + reportId + " pages=" + PAGES + " checksum=" + checksum)
                .getBytes(StandardCharsets.UTF_8);
    }
}

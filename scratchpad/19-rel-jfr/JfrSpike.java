import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class JfrSpike {

    // planted hotspot class
    static final class ReportRenderer {
        private final Object lock = new Object();
        private long sink;
        void render() {
            synchronized (lock) {
                try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        void allocate() {
            for (int i = 0; i < 4000; i++) {
                byte[] buf = new byte[64 * 1024];
                buf[0] = (byte) i;
                sink += buf.length;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of("/Users/krisss/V-learning/scratchpad/19-rel-jfr/spike.jfr");
        var r = new Recording();
        r.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
        r.enable("jdk.ObjectAllocationSample").with("throttle", "500/s").withStackTrace();
        r.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
        r.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace();
        r.start();

        var renderer = new ReportRenderer();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 40; i++) pool.submit(renderer::render);
            for (int i = 0; i < 4; i++) pool.submit(renderer::allocate);
        }

        r.dump(file);
        r.close();

        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> plantedByType = new TreeMap<>();
        try (var rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                var e = rf.readEvent();
                String name = e.getEventType().getName();
                byType.merge(name, 1, Integer::sum);
                var st = e.getStackTrace();
                if (st != null && st.getFrames().stream().anyMatch(f ->
                        f.getMethod().getType().getName().contains("ReportRenderer"))) {
                    plantedByType.merge(name, 1, Integer::sum);
                }
            }
        }
        System.out.println("ALL EVENTS: " + byType);
        System.out.println("PLANTED-ATTRIBUTED: " + plantedByType);
        System.out.println("file size = " + Files.size(file));
    }
}

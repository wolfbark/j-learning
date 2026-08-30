package dev.vlearning.reliability.profiling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

public final class JfrProfiler {

    public static final List<String> SUGGESTED_EVENTS = List.of(
            "jdk.JavaMonitorEnter", "jdk.ObjectAllocationSample", "jdk.ExecutionSample",
            "jdk.ThreadPark");

    public record HotSpot(String eventType, long count, Duration total) {
    }

    public Path recordWhile(Path destination, Runnable load) {
        try (var recording = new Recording()) {
            recording.enable("jdk.JavaMonitorEnter")
                    .withThreshold(Duration.ofMillis(1)).withStackTrace();
            recording.enable("jdk.ObjectAllocationSample")
                    .with("throttle", "500/s").withStackTrace();
            recording.start();
            load.run();
            recording.stop();
            recording.dump(destination);
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, HotSpot> attributeTo(Path recording, String classNameFragment) {
        var hotspots = new LinkedHashMap<String, HotSpot>();
        try (var file = new RecordingFile(recording)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (!mentions(event.getStackTrace(), classNameFragment)) {
                    continue;
                }
                String type = event.getEventType().getName();
                Duration duration = event.hasField("duration") ? event.getDuration() : Duration.ZERO;
                hotspots.merge(type, new HotSpot(type, 1, duration),
                        (a, b) -> new HotSpot(type, a.count() + b.count(),
                                a.total().plus(b.total())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hotspots;
    }

    private static boolean mentions(RecordedStackTrace stackTrace, String fragment) {
        return stackTrace != null && stackTrace.getFrames().stream()
                .anyMatch(frame -> frame.getMethod().getType().getName().contains(fragment));
    }
}

package dev.vlearning.reliability.profiling;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Step 5's exercise: a recording, and an answer to "which of my classes is
 * responsible?".
 *
 * <p>JFR is in the JDK, always has been since 11, and costs a couple of percent.
 * You do not need a profiler installed, an agent attached, or a service
 * restart — {@code jdk.jfr.Recording} starts one from inside the process, and
 * {@code jdk.jfr.consumer.RecordingFile} reads the result back as ordinary
 * objects with stack traces attached.
 *
 * <p>Both methods below are yours to write. The checkpoint test uses only this
 * API, so keep the signatures.
 */
public final class JfrProfiler {

    /**
     * Event types worth enabling for this lesson. Enabling everything is how you
     * turn a 2% profiler into a 20% one.
     */
    public static final List<String> SUGGESTED_EVENTS = List.of(
            "jdk.JavaMonitorEnter",       // lock contention, with the blocked stack
            "jdk.ObjectAllocationSample", // sampled allocation, with the allocating stack
            "jdk.ExecutionSample",        // where CPU time goes (a flame graph, essentially)
            "jdk.ThreadPark");            // waiting on a lock/queue rather than a monitor

    /**
     * Events of one type that are attributable to a class, plus how much wall
     * time they cost.
     */
    public record HotSpot(String eventType, long count, Duration total) {
    }

    /**
     * Start a recording, run {@code load}, dump the recording to
     * {@code destination} and return that path.
     */
    public Path recordWhile(Path destination, Runnable load) {
        throw new UnsupportedOperationException("Step 5: record the JFR file");
    }

    /**
     * Read a recording and group the events whose stack trace mentions
     * {@code classNameFragment} by event type.
     */
    public Map<String, HotSpot> attributeTo(Path recording, String classNameFragment) {
        throw new UnsupportedOperationException("Step 5: parse the JFR file");
    }
}

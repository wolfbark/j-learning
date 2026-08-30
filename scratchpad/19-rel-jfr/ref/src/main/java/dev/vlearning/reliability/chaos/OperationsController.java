package dev.vlearning.reliability.chaos;

import java.util.Map;

import dev.vlearning.reliability.load.ThrottledWorkload;
import dev.vlearning.reliability.profiling.ReportRenderer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operator's door: flip the chaos switch, drive the throttled workload, read
 * the raw meters. For the step 7 drill, someone else flips the switch and you
 * are not allowed to read this file's callers.
 */
@RestController
public class OperationsController {

    private final ChaosSwitch chaos;
    private final ThrottledWorkload workload;
    private final ReportRenderer renderer;

    public OperationsController(ChaosSwitch chaos, ThrottledWorkload workload,
                                ReportRenderer renderer) {
        this.chaos = chaos;
        this.workload = workload;
        this.renderer = renderer;
    }

    @PostMapping("/chaos")
    public Map<String, String> setMode(@RequestParam ChaosSwitch.Mode mode) {
        chaos.set(mode);
        return Map.of("mode", chaos.mode().name());
    }

    @GetMapping("/workload")
    public Map<String, Object> workload() throws InterruptedException {
        long millis = workload.handle().toMillis();
        return Map.of("latencyMillis", millis,
                "inFlight", workload.inFlight(),
                "peakInFlight", workload.peakInFlight(),
                "completed", workload.completed());
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        return Map.of("workloadCapacity", ThrottledWorkload.CAPACITY,
                "workloadPeakInFlight", workload.peakInFlight(),
                "workloadCompleted", workload.completed(),
                "reportsRendered", renderer.renderCount());
    }

    @PostMapping("/diagnostics/reset")
    public Map<String, String> reset() {
        workload.reset();
        chaos.reset();
        return Map.of("status", "reset");
    }
}

package dev.vlearning.reliability.profiling;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import dev.vlearning.reliability.database.ReportQueryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportRenderer renderer;
    private final ReportQueryRepository reports;

    public ReportController(ReportRenderer renderer, ReportQueryRepository reports) {
        this.renderer = renderer;
        this.reports = reports;
    }

    /** The CPU/allocation/lock path — step 5's subject. */
    @GetMapping("/reports/{id}")
    public String render(@PathVariable String id) {
        return new String(renderer.render(id), StandardCharsets.UTF_8);
    }

    /** The database path — step 6's subject. Needs Postgres. */
    @GetMapping("/reports/{id}/aggregate")
    public Map<String, Object> aggregate(@PathVariable String id) {
        return Map.of("reportId", id, "rows", reports.aggregate(id));
    }
}

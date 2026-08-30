package dev.vlearning.ledger.api;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.ledger.projection.RebuildableProjection;

/**
 * Given: the admin lever for the event-sourcing superpower. Any bean implementing
 * {@link RebuildableProjection} can be rebuilt from history by name. Your step-6 projector
 * plugs in here without touching this class.
 */
@RestController
public class ProjectionAdminController {

    private final Map<String, RebuildableProjection> projections;

    public ProjectionAdminController(List<RebuildableProjection> projections) {
        this.projections = projections.stream()
                .collect(Collectors.toMap(RebuildableProjection::name, Function.identity()));
    }

    @PostMapping("/admin/projections/{name}/rebuild")
    ResponseEntity<Void> rebuild(@PathVariable String name) {
        var projection = projections.get(name);
        if (projection == null) {
            return ResponseEntity.notFound().build();
        }
        projection.rebuild();
        return ResponseEntity.accepted().build();
    }
}

package dev.vlearning.parcels.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * A compatibility gate you can run in CI without a schema registry. It encodes the same rules a
 * registry enforces, over a deliberately tiny schema model — the point is that the rules are
 * simple and mechanical, so there is no excuse for finding out about a breaking change in
 * production. In production you would delegate this to Apicurio or Confluent Schema Registry
 * (see the README): they version the contract centrally and can refuse the producer's very first
 * connection, which a unit test cannot.
 *
 * <p>Directions, because they are easy to mix up:
 * <ul>
 *   <li><b>Backward</b> compatible — a reader on the <em>new</em> schema can read data written
 *       with the <em>old</em> one. Consumers upgrade first.</li>
 *   <li><b>Forward</b> compatible — a reader on the <em>old</em> schema can read data written
 *       with the <em>new</em> one. Producers upgrade first.</li>
 *   <li><b>Full</b> — both. What you want for a topic with consumers you do not control.</li>
 * </ul>
 */
public final class SchemaCompatibility {

    public record Violation(String field, String rule, String detail) {
    }

    private SchemaCompatibility() {
    }

    /**
     * Rules for FULL compatibility between two consecutive versions.
     *
     * <p>Implemented: dropping a field that old readers require (forward incompatibility).
     * <p>Checkpoint 7 adds: adding a required field with no default (backward incompatibility)
     * and changing a field's type (breaks both directions).
     */
    public static List<Violation> fullCompatibilityViolations(RecordSchema previous, RecordSchema next) {
        var violations = new ArrayList<Violation>();

        for (FieldSpec before : previous.fields()) {
            if (next.field(before.name()).isEmpty() && before.required()) {
                violations.add(new Violation(before.name(), "field-removed",
                        "readers on v%d require '%s'; v%d no longer writes it"
                                .formatted(previous.version(), before.name(), next.version())));
            }
        }

        // Checkpoint 7: two more rules go here.

        return List.copyOf(violations);
    }

    public static boolean isFullyCompatible(RecordSchema previous, RecordSchema next) {
        return fullCompatibilityViolations(previous, next).isEmpty();
    }
}

package dev.vlearning.parcels;

import dev.vlearning.parcels.schema.SchemaCompatibility;
import dev.vlearning.parcels.schema.ScanSchemas;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCompatibilityTest {

    @Test
    void addingAnOptionalFieldWithADefaultIsCompatible() {
        assertThat(SchemaCompatibility.fullCompatibilityViolations(
                ScanSchemas.V1, ScanSchemas.V2_OPTIONAL_DELIVERY_WINDOW)).isEmpty();
    }

    @Test
    void droppingAFieldOldReadersRequireIsCaught() {
        var violations = SchemaCompatibility.fullCompatibilityViolations(
                ScanSchemas.V1, ScanSchemas.V1.without("hubId", 2));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.field()).isEqualTo("hubId");
            assertThat(violation.rule()).isEqualTo("field-removed");
        });
    }
}

package dev.vlearning.parcels;

import dev.vlearning.parcels.scan.ParcelScan;
import dev.vlearning.parcels.schema.SchemaCompatibility;
import dev.vlearning.parcels.schema.ScanSchemas;
import dev.vlearning.parcels.support.KafkaSupport;
import dev.vlearning.parcels.wire.JsonCodec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint 7 — the compatibility gate. A registry is the production answer; the rules it
 * enforces are simple enough that a unit test can enforce them today.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7SchemaEvolutionTest {

    private static final JsonCodec CODEC = new JsonCodec();

    private static final String V2_PAYLOAD = """
            {"scanId":"S-1","parcelId":"P-1","customerId":"C-1","status":"DELIVERED","hubId":"HUB-9",
             "scannedAtEpochMs":1760000000000,"sequence":3,"deliveryWindow":"08:00-12:00"}
            """;

    @Test
    void theCompatibleChangePassesTheGate() {
        assertThat(SchemaCompatibility.isFullyCompatible(
                ScanSchemas.V1, ScanSchemas.V2_OPTIONAL_DELIVERY_WINDOW)).isTrue();
    }

    @Test
    void aNewRequiredFieldWithoutADefaultFailsTheGate() {
        var violations = SchemaCompatibility.fullCompatibilityViolations(
                ScanSchemas.V1, ScanSchemas.V3_REQUIRED_CARRIER);

        assertThat(violations).extracting(SchemaCompatibility.Violation::field).containsExactly("carrierId");
    }

    @Test
    void changingAFieldsTypeFailsTheGate() {
        var violations = SchemaCompatibility.fullCompatibilityViolations(
                ScanSchemas.V1, ScanSchemas.V4_RETYPED_TIMESTAMP);

        assertThat(violations).extracting(SchemaCompatibility.Violation::field)
                .containsExactly("scannedAtEpochMs");
    }

    @Test
    void aRenameIsARemovalAndAnAddition() {
        var violations = SchemaCompatibility.fullCompatibilityViolations(
                ScanSchemas.V1, ScanSchemas.V5_RENAMED_HUB);

        assertThat(violations).extracting(SchemaCompatibility.Violation::field)
                .containsExactlyInAnyOrder("hubId", "facilityId");
    }

    @Test
    void anOldConsumerSurvivesTheCompatibleChangeOnTheWire() {
        String topic = KafkaSupport.freshTopic("cp7-evolution", 1);
        KafkaSupport.send(topic, "P-1", V2_PAYLOAD);

        var record = KafkaSupport.drain(topic, 1, Duration.ofSeconds(20)).getFirst();
        var asV1 = CODEC.fromJson(record.value(), ParcelScan.class);

        assertThat(asV1.parcelId()).isEqualTo("P-1");
        assertThat(asV1.sequence()).isEqualTo(3);
    }

    @Test
    void aStrictReaderTurnsTheSameChangeIntoAnOutage() {
        var strict = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        assertThatThrownBy(() -> strict.readValue(V2_PAYLOAD, ParcelScan.class))
                .as("tolerant reading is a decision somebody has to make on purpose")
                .hasMessageContaining("deliveryWindow");
    }
}

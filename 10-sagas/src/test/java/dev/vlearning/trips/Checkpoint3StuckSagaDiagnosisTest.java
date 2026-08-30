package dev.vlearning.trips;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3 — the crime scene. This test doesn't check your code; it plants
 * evidence. It seeds the database with exactly what a choreographed saga
 * leaves behind when the hotel's answer is lost in transit, then dumps every
 * table. Your job (in the README) is the investigation: where is booking #42
 * stuck, and how many places did you have to look?
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3StuckSagaDiagnosisTest extends AbstractIntegrationTest {

    private static final UUID BOOKING_42 = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void seedTheStuckSagaAndStartYourInvestigation() {
        // What really happened (only this comment knows): TripRequested was
        // published, the flight was reserved, and the hotel never answered.
        jdbc.sql("""
                INSERT INTO trips (trip_id, traveller, destination, price, status)
                VALUES (:id, 'Grace Hopper', 'Kyoto', 1499.00, 'PENDING')""")
                .param("id", BOOKING_42).update();
        jdbc.sql("""
                INSERT INTO flight_reservations (trip_id, flight_number, status)
                VALUES (:id, 'VL-0042', 'RESERVED')""")
                .param("id", BOOKING_42).update();

        System.out.println();
        System.out.println("=== Support ticket: \"Customer asks why booking ...0042 is still pending after 3 days\" ===");
        dump("trips");
        dump("flight_reservations");
        dump("hotel_reservations");
        dump("payments");
        dump("saga_instance");
        System.out.println("=== That is ALL the state there is. Now answer the step 3 questions in the README. ===");
        System.out.println();

        assertThat(tripStatus(BOOKING_42)).isEqualTo("PENDING"); // ...and it always will be
    }

    private void dump(String table) {
        var rows = jdbc.sql("SELECT * FROM " + table).query().listOfRows();
        System.out.printf("%n%s (%d row%s)%n", table, rows.size(), rows.size() == 1 ? "" : "s");
        rows.forEach(row -> System.out.println("  " + row));
    }
}

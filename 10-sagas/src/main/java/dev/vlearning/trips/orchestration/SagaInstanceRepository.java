package dev.vlearning.trips.orchestration;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the orchestrator's state machine. Given, because it is
 * boring on purpose: the interesting part — WHICH transitions happen on WHICH
 * events — is yours to write in {@code TripSagaOrchestrator} (step 4).
 */
@Repository
public class SagaInstanceRepository {

    private final JdbcClient jdbc;

    public SagaInstanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void start(UUID tripId, SagaStep step) {
        jdbc.sql("""
                INSERT INTO saga_instance (trip_id, current_step, status)
                VALUES (:tripId, :step, 'RUNNING')
                ON CONFLICT (trip_id) DO NOTHING""")
                .param("tripId", tripId).param("step", step.name())
                .update();
    }

    public void transition(UUID tripId, SagaStep step, SagaStatus status) {
        jdbc.sql("""
                UPDATE saga_instance SET current_step = :step, status = :status, updated_at = now()
                WHERE trip_id = :tripId""")
                .param("tripId", tripId).param("step", step.name()).param("status", status.name())
                .update();
    }

    public Optional<SagaInstance> find(UUID tripId) {
        return jdbc.sql("SELECT trip_id, current_step, status FROM saga_instance WHERE trip_id = :tripId")
                .param("tripId", tripId)
                .query((rs, rowNum) -> new SagaInstance(
                        rs.getObject("trip_id", UUID.class),
                        SagaStep.valueOf(rs.getString("current_step")),
                        SagaStatus.valueOf(rs.getString("status"))))
                .optional();
    }
}

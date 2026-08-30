package dev.vlearning.ledger.application;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.vlearning.ledger.domain.AccountState;
import tools.jackson.databind.json.JsonMapper;

/**
 * Given: snapshots are boring JDBC. The state is serialized with a dedicated mapper for the
 * same reason event payloads are — it's a persisted format, not an API response. Note the
 * upsert: one snapshot per stream, newer overwrites older.
 */
@Component
public class PostgresSnapshotStore implements SnapshotStore {

    private final JdbcClient jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public PostgresSnapshotStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Snapshot> load(String streamId) {
        return jdbc.sql("SELECT version, state FROM snapshots WHERE stream_id = :streamId")
                .param("streamId", streamId)
                .query((rs, rowNum) -> new Snapshot(streamId, rs.getLong("version"),
                        mapper.readValue(rs.getString("state"), AccountState.class)))
                .optional();
    }

    @Override
    public void save(Snapshot snapshot) {
        jdbc.sql("""
                        INSERT INTO snapshots (stream_id, version, state)
                        VALUES (:streamId, :version, CAST(:state AS jsonb))
                        ON CONFLICT (stream_id)
                        DO UPDATE SET version = EXCLUDED.version, state = EXCLUDED.state, taken_at = now()
                        """)
                .param("streamId", snapshot.streamId())
                .param("version", snapshot.version())
                .param("state", mapper.writeValueAsString(snapshot.state()))
                .update();
    }
}

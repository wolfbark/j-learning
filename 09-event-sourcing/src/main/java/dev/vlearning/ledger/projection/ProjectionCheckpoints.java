package dev.vlearning.ledger.projection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Given: each projection remembers the last global sequence it has processed, in the same
 * database it writes its read model to. That co-location is what makes "update the read
 * model + advance the checkpoint" atomically committable — the projection can crash at any
 * point and resume without double-applying (as long as both happen in one transaction).
 */
@Component
public class ProjectionCheckpoints {

    private final JdbcClient jdbc;

    public ProjectionCheckpoints(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long position(String projectionName) {
        return jdbc.sql("SELECT position FROM projection_checkpoint WHERE projection_name = :name")
                .param("name", projectionName)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public void advance(String projectionName, long position) {
        jdbc.sql("""
                        INSERT INTO projection_checkpoint (projection_name, position)
                        VALUES (:name, :position)
                        ON CONFLICT (projection_name) DO UPDATE SET position = EXCLUDED.position
                        """)
                .param("name", projectionName)
                .param("position", position)
                .update();
    }

    public void reset(String projectionName) {
        jdbc.sql("DELETE FROM projection_checkpoint WHERE projection_name = :name")
                .param("name", projectionName)
                .update();
    }
}

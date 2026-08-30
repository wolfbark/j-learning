package dev.vlearning.reliability.database;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The report aggregate. Most report ids are cheap; one in five hits a query that
 * takes two seconds, because somebody's partition is cold and nobody has looked
 * at that query plan since 2023.
 *
 * <p>{@code pg_sleep} stands in for that query. It is a fair stand-in for step 6's
 * purpose: it occupies a real pooled connection for a real length of time, and a
 * statement timeout cancels it exactly as it would cancel a real seq scan.
 */
@Repository
public class ReportQueryRepository {

    public static final double CHEAP_SECONDS = 0.05;
    public static final double PATHOLOGICAL_SECONDS = 2.0;

    private final JdbcClient jdbc;
    private final ChaosSwitch chaos;

    public ReportQueryRepository(JdbcClient jdbc, ChaosSwitch chaos) {
        this.jdbc = jdbc;
        this.chaos = chaos;
    }

    public long aggregate(String reportId) {
        double seconds = isPathological(reportId) ? PATHOLOGICAL_SECONDS : CHEAP_SECONDS;
        return jdbc.sql("SELECT count(*) FROM pg_sleep(?)")
                .param(seconds)
                .query(Long.class)
                .single();
    }

    private boolean isPathological(String reportId) {
        return Math.floorMod(reportId.hashCode(), 100) < chaos.databasePathologicalShare() * 100;
    }
}

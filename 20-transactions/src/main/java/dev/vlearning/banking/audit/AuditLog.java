package dev.vlearning.banking.audit;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "We must have a record that this was attempted, even if it failed."
 *
 * <p>As written, that requirement is not met: propagation REQUIRED joins the
 * caller's transaction, so when the business transaction rolls back it takes the
 * audit row with it. Checkpoint 7a is one word long.
 */
@Service
public class AuditLog {

    private final JdbcClient jdbc;

    AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(String message) {
        jdbc.sql("INSERT INTO audit_log (message) VALUES (:message)")
                .param("message", message).update();
    }

    @Transactional(readOnly = true)
    public List<String> messages() {
        return jdbc.sql("SELECT message FROM audit_log ORDER BY id").query(String.class).list();
    }
}

package dev.vlearning.ledger.projection;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Given: the query side of the balances read model. One indexed lookup, no folding —
 * this is what the read model buys over rehydration. It may be momentarily stale;
 * that is the deal you signed.
 */
@Component
public class BalancesReadModel {

    private final JdbcClient jdbc;

    public BalancesReadModel(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BalanceRow> find(String accountId) {
        return jdbc.sql("""
                        SELECT account_id, owner, balance_cents, status
                        FROM account_balances WHERE account_id = :accountId
                        """)
                .param("accountId", accountId)
                .query((rs, rowNum) -> new BalanceRow(
                        rs.getString("account_id"),
                        rs.getString("owner"),
                        rs.getLong("balance_cents"),
                        rs.getString("status")))
                .optional();
    }

    public record BalanceRow(String accountId, String owner, long balanceCents, String status) {
    }
}

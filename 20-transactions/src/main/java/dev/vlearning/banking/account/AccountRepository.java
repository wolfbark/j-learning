package dev.vlearning.banking.account;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain SQL on purpose. This lesson is about what the database does when two
 * transactions meet, and JPA's first-level cache would hide half of it.
 */
@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long balanceOf(long id) {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    /**
     * Read-modify-write's second half: whatever the caller computed, blindly stored.
     * The value was decided in application memory, from a read that may already be stale.
     */
    public void setBalance(long id, long balance) {
        jdbc.sql("UPDATE account SET balance = :balance WHERE id = :id")
                .param("balance", balance).param("id", id).update();
    }

    public long combinedBalance(String customer) {
        return jdbc.sql("SELECT coalesce(sum(balance), 0) FROM account WHERE customer = :customer")
                .param("customer", customer).query(Long.class).single();
    }

    public List<Account> ofCustomer(String customer) {
        return jdbc.sql("SELECT id, customer, kind, balance FROM account WHERE customer = :customer ORDER BY id")
                .param("customer", customer)
                .query((rs, n) -> new Account(rs.getLong("id"), rs.getString("customer"),
                        Account.Kind.valueOf(rs.getString("kind")), rs.getLong("balance")))
                .list();
    }

    public long balanceOf(String customer, Account.Kind kind) {
        return jdbc.sql("SELECT balance FROM account WHERE customer = :customer AND kind = :kind")
                .param("customer", customer).param("kind", kind.name())
                .query(Long.class).single();
    }

    public boolean exists(long id) {
        return jdbc.sql("SELECT count(*) FROM account WHERE id = :id")
                .param("id", id).query(Long.class).single() > 0;
    }

    /**
     * Checkpoint 2a: one statement in which the database reads and writes the
     * balance, so no other transaction can slip between the two halves.
     */
    public void addToBalance(long id, long delta) {
        throw new UnsupportedOperationException(
                "Checkpoint 2a: UPDATE account SET balance = balance + :delta WHERE id = :id");
    }

    /**
     * Checkpoint 2b: read the balance <em>and</em> take an exclusive row lock, so
     * that a second transaction reading the same row waits for this one to finish.
     */
    public long balanceForUpdate(long id) {
        throw new UnsupportedOperationException(
                "Checkpoint 2b: SELECT balance FROM account WHERE id = :id FOR UPDATE");
    }
}

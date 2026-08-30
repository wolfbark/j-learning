package dev.vlearning.apisecurity.expense;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository {

    private static final String COLUMNS =
            "id, owner_username, team, merchant, amount_cents, currency, category, status, card_last4, receipt_url, receipt_bytes";

    private final JdbcClient jdbc;

    public ExpenseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ExpenseReport> findById(long id) {
        return jdbc.sql("select " + COLUMNS + " from expense_report where id = :id")
                .param("id", id)
                .query(ExpenseReport.class)
                .optional();
    }

    public List<ExpenseReport> search(String userId, String query, String sort) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from expense_report where 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (userId != null && !userId.isBlank()) {
            sql.append(" and owner_username = :owner");
            params.put("owner", userId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and lower(merchant) like :like");
            params.put("like", "%" + query.toLowerCase() + "%");
        }
        sql.append(" order by ").append(sort == null || sort.isBlank() ? "id" : sort).append(" limit 200");
        return jdbc.sql(sql.toString())
                .params(params)
                .query(ExpenseReport.class)
                .list();
    }

    public long insert(String owner, String team, String merchant, long amountCents,
                       String currency, String category, String cardLast4) {
        return jdbc.sql("""
                        insert into expense_report
                            (owner_username, team, merchant, amount_cents, currency, category, status, card_last4)
                        values (:owner, :team, :merchant, :amount, :currency, :category, 'DRAFT', :last4)
                        returning id
                        """)
                .param("owner", owner)
                .param("team", team)
                .param("merchant", merchant)
                .param("amount", amountCents)
                .param("currency", currency)
                .param("category", category)
                .param("last4", cardLast4)
                .query(Long.class)
                .single();
    }

    public int update(long id, String merchant, long amountCents, String category) {
        return jdbc.sql("""
                        update expense_report
                           set merchant = :merchant, amount_cents = :amount, category = :category
                         where id = :id
                        """)
                .param("id", id)
                .param("merchant", merchant)
                .param("amount", amountCents)
                .param("category", category)
                .update();
    }

    public int updateStatus(long id, String status) {
        return jdbc.sql("update expense_report set status = :status where id = :id")
                .param("id", id)
                .param("status", status)
                .update();
    }

    public int attachReceipt(long id, String url, int bytes) {
        return jdbc.sql("update expense_report set receipt_url = :url, receipt_bytes = :bytes where id = :id")
                .param("id", id)
                .param("url", url)
                .param("bytes", bytes)
                .update();
    }

    /** Boring plumbing you will need in steps 3 and 4. */
    public Optional<String> teamOf(String username) {
        return jdbc.sql("select team from team_member where username = :u")
                .param("u", username)
                .query(String.class)
                .optional();
    }

    /** Which teams this person manages. Empty for a plain employee. */
    public List<String> teamsManagedBy(String username) {
        return jdbc.sql("select team from team_manager where manager_username = :u")
                .param("u", username)
                .query(String.class)
                .list();
    }
}

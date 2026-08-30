package dev.vlearning.apisecurity.expense;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Deterministic fixture: expense report ids 1..8, owned by the four Keycloak users. Tests call
 * {@link #reset()} before every test method, so ids are stable and the enumeration checkpoint in
 * step 4 can walk 1..8 and know exactly what should and should not be visible.
 *
 * <pre>
 *   id  owner  team   status
 *    1  alice  alpha  DRAFT
 *    2  alice  alpha  SUBMITTED
 *    3  bob    beta   SUBMITTED
 *    4  bob    beta   DRAFT
 *    5  carol  alpha  DRAFT       (managers file expenses too)
 *    6  alice  alpha  SUBMITTED
 *    7  bob    beta   APPROVED
 *    8  dave   beta   SUBMITTED
 * </pre>
 *
 * Teams: alpha is managed by carol, beta by dave.
 */
@Component
public class ExpenseSeeder implements ApplicationRunner {

    private final JdbcClient jdbc;

    public ExpenseSeeder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = jdbc.sql("select count(*) from expense_report").query(Long.class).single();
        if (count == 0) {
            reset();
        }
    }

    public void reset() {
        jdbc.sql("truncate table expense_report restart identity").update();
        jdbc.sql("delete from team_member").update();
        jdbc.sql("delete from team_manager").update();

        member("alice", "alpha");
        member("bob", "beta");
        member("carol", "alpha");
        member("dave", "beta");
        manager("alpha", "carol");
        manager("beta", "dave");

        report("alice", "alpha", "Blue Bottle Coffee", 1_250, "MEALS", ExpenseReport.DRAFT, "4242");
        report("alice", "alpha", "Lufthansa LH2451", 43_900, "TRAVEL", ExpenseReport.SUBMITTED, "4242");
        report("bob", "beta", "Hotel Adlon", 78_000, "LODGING", ExpenseReport.SUBMITTED, "1881");
        report("bob", "beta", "Deutsche Bahn", 5_990, "TRAVEL", ExpenseReport.DRAFT, "1881");
        report("carol", "alpha", "O'Reilly Media", 4_900, "TRAINING", ExpenseReport.DRAFT, "9001");
        report("alice", "alpha", "Uber", 2_340, "TRAVEL", ExpenseReport.SUBMITTED, "4242");
        report("bob", "beta", "WeWork Berlin", 29_000, "OFFICE", ExpenseReport.APPROVED, "1881");
        report("dave", "beta", "Sushi Express", 6_100, "MEALS", "SUBMITTED", "5309");
    }

    private void member(String username, String team) {
        jdbc.sql("insert into team_member (username, team) values (:u, :t)")
                .param("u", username).param("t", team).update();
    }

    private void manager(String team, String username) {
        jdbc.sql("insert into team_manager (team, manager_username) values (:t, :u)")
                .param("t", team).param("u", username).update();
    }

    private void report(String owner, String team, String merchant, long amountCents,
                        String category, String status, String last4) {
        jdbc.sql("""
                        insert into expense_report
                            (owner_username, team, merchant, amount_cents, currency, category, status, card_last4)
                        values (:owner, :team, :merchant, :amount, 'EUR', :category, :status, :last4)
                        """)
                .param("owner", owner)
                .param("team", team)
                .param("merchant", merchant)
                .param("amount", amountCents)
                .param("category", category)
                .param("status", status)
                .param("last4", last4)
                .update();
    }
}

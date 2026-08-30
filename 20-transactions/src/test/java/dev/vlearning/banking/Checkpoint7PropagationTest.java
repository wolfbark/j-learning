package dev.vlearning.banking;

import java.util.List;

import dev.vlearning.banking.audit.AuditLog;
import dev.vlearning.banking.audit.AuditedTransferService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 7: propagation, where it actually matters.
 *
 * <p>Two requirements that pull in opposite directions — a child that must
 * survive the parent's rollback, and a child that must be undoable without
 * taking the parent down with it — and each is one annotation attribute.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7PropagationTest extends AbstractIntegrationTest {

    @Autowired
    AuditedTransferService transfers;

    @Autowired
    AuditLog audit;

    @Test
    void requiresNew_theAuditTrailSurvivesTheRollback() {
        assertThatThrownBy(() -> transfers.transferAudited(ADA_CHECKING, LINUS_CHECKING, 999_999))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balance(ADA_CHECKING)).as("no money moved").isEqualTo(5000);
        assertThat(audit.messages())
                .as("but we still know it was attempted")
                .singleElement().asString().contains("attempted");
    }

    @Test
    void requiresNew_isASecondTransactionOnASecondConnection() {
        transfers.transferAudited(ADA_CHECKING, LINUS_CHECKING, 1000);

        assertThat(balance(ADA_CHECKING)).isEqualTo(4000);
        assertThat(balance(LINUS_CHECKING)).isEqualTo(11_000);
        assertThat(auditRowCount()).isEqualTo(1);
    }

    @Test
    void nested_oneBadRecipientDoesNotCostTheWholeBatch() {
        int paid = transfers.payoutBatch(LINUS_CHECKING, List.of(ADA_CHECKING, NO_SUCH_ACCOUNT, ADA_SAVINGS), 1000);

        assertThat(paid).isEqualTo(2);
        assertThat(balance(ADA_CHECKING)).isEqualTo(6000);
        assertThat(balance(ADA_SAVINGS)).isEqualTo(6000);
        assertThat(balance(LINUS_CHECKING))
                .as("debited twice, and the failed payout's debit was rolled back to the savepoint")
                .isEqualTo(8000);
    }
}

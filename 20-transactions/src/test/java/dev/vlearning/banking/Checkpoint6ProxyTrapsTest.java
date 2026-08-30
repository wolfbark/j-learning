package dev.vlearning.banking;

import dev.vlearning.banking.traps.PaperworkMissingException;
import dev.vlearning.banking.traps.TrapsService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 6: three ways {@code @Transactional} does nothing like what the code
 * says. Nothing here is about the database — it is all proxy mechanics.
 *
 * <p>Each trap comes as a pair: the {@code asWritten} test pins today's
 * behaviour and stays green forever as the exhibit, and the one after it is red
 * until you write the repaired variant.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6ProxyTrapsTest extends AbstractIntegrationTest {

    @Autowired
    TrapsService traps;

    @Test
    void trap1_asWritten_selfInvocationMeansNoTransactionAtAll() {
        assertThatThrownBy(() -> traps.selfInvoked(ADA_CHECKING, 1000))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balance(ADA_CHECKING))
                .as("the annotated method threw, and the money is still there: nothing rolled back")
                .isEqualTo(6000);
    }

    @Test
    void trap1_repaired_theCallReachesTheProxy() {
        assertThatThrownBy(() -> traps.selfInvokedButTransactional(ADA_CHECKING, 1000))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balance(ADA_CHECKING)).isEqualTo(5000);
    }

    @Test
    void trap2_asWritten_aCheckedExceptionCommits() {
        assertThatThrownBy(() -> traps.depositThenFailChecked(ADA_CHECKING, 1000))
                .isInstanceOf(PaperworkMissingException.class);

        assertThat(balance(ADA_CHECKING))
                .as("rollback-on-Error-and-RuntimeException is the default; checked exceptions commit")
                .isEqualTo(6000);
    }

    @Test
    void trap2_repaired_theCheckedExceptionRollsBack() {
        assertThatThrownBy(() -> traps.depositThenFailCheckedWithRollback(ADA_CHECKING, 1000))
                .isInstanceOf(PaperworkMissingException.class);

        assertThat(balance(ADA_CHECKING)).isEqualTo(5000);
    }

    @Test
    void trap3_asWritten_catchingTheInnerFailureDoesNotSaveTheTransaction() {
        assertThatThrownBy(() -> traps.depositAndSwallowInnerFailure(ADA_CHECKING, 1000))
                .as("thrown by the commit, from a line that touches nothing")
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        assertThat(balance(ADA_CHECKING))
                .as("and the deposit that was never in question is gone too")
                .isEqualTo(5000);
    }

    @Test
    void trap3_repaired_theInnerFailureBelongsToTheInnerTransaction() {
        traps.depositAndSurviveInnerFailure(ADA_CHECKING, 1000);

        assertThat(balance(ADA_CHECKING)).isEqualTo(6000);
    }
}

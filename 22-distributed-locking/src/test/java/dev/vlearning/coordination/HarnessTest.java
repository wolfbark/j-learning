package dev.vlearning.coordination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Green on checkout: the container starts, the schema loads, and one worker with
 * nobody to argue with bills everybody exactly once.
 */
class HarnessTest extends AbstractIntegrationTest {

    @Test
    void oneWorkerAloneBillsEverybodyOnce() {
        worker("pod-a").runUnprotected(PERIOD);

        assertThat(invoiceCount()).isEqualTo(CUSTOMERS);
        assertThat(gateway.charges()).hasSize(CUSTOMERS);
        assertThat(gateway.totalCharged()).isEqualTo(TOTAL_OWED);
    }

    @Test
    void theLeaseRowStartsUnheld() {
        assertThat(leases.currentHolder("nightly-billing")).isEmpty();
        assertThat(leases.currentFencingToken("nightly-billing")).isZero();
    }
}

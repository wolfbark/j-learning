package dev.vlearning.reliability;

import java.time.Duration;

import dev.vlearning.reliability.load.ThrottledWorkload;
import dev.vlearning.reliability.support.LoadHarness;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 4. The same nominal load — 200 requests per second at a service that can
 * do 160 — measured two ways. The closed model reports a service that is coping.
 * The open model reports the service your users are actually using.
 */
class Checkpoint4LoadModelTest {

    private static final Duration RUN = Duration.ofSeconds(2);
    private static final int CLOSED_USERS = 10;
    private static final Duration THINK_TIME = Duration.ofMillis(25);
    private static final double OPEN_ARRIVAL_RATE = 200;

    @Test
    @DisplayName("the closed model cannot queue more than its own population")
    void closedModelSelfThrottles() {
        var workload = new ThrottledWorkload();

        var result = LoadHarness.closedModel(CLOSED_USERS, THINK_TIME, RUN, workload::handle);
        System.out.println(result + " peakInFlight=" + workload.peakInFlight());

        assertThat(workload.peakInFlight())
                .as("ten users cannot have eleven requests outstanding: the harness itself is "
                        + "the admission control, which is why saturation looks so calm here")
                .isLessThanOrEqualTo(CLOSED_USERS);
        assertThat(result.completed()).isGreaterThan(100);
    }

    @Test
    @DisplayName("the open model exposes the queue the closed model cannot form")
    void openModelExposesQueueing() {
        var closedWorkload = new ThrottledWorkload();
        var closed = LoadHarness.closedModel(CLOSED_USERS, THINK_TIME, RUN, closedWorkload::handle);
        int closedPeak = closedWorkload.peakInFlight();

        var openWorkload = new ThrottledWorkload();
        var open = LoadHarness.openModel(OPEN_ARRIVAL_RATE, RUN, openWorkload::handle);
        int openPeak = openWorkload.peakInFlight();

        System.out.println(closed + " peakInFlight=" + closedPeak);
        System.out.println(open + " peakInFlight=" + openPeak);

        assertThat(openPeak)
                .as("arrivals do not slow down because you did: the backlog grows at "
                        + "(arrival rate − service rate) per second, for as long as it lasts")
                .isGreaterThan(closedPeak * 2);

        assertThat(open.p99())
                .as("same offered load, same throughput ceiling, wildly different tail — "
                        + "and only one of these two numbers is what a user sees")
                .isGreaterThan(closed.p99().multipliedBy(2));

        assertThat(open.completed())
                .as("throughput is capped by the service either way; the load model changes "
                        + "the latency you observe, not the capacity you have")
                .isBetween((int) (closed.completed() * 0.6), (int) (closed.completed() * 1.6));
    }
}

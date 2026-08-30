package dev.vlearning.library.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

import dev.vlearning.library.lending.LoanCreated;

/**
 * Module-scoped integration test for notifications, bootstrapped STANDALONE
 * (the default): no catalog, no lending beans — after step 4 this module
 * depends on nothing but lending's published event types. The Scenario API
 * injects the event from the outside, exactly like lending would at runtime.
 */
@ApplicationModuleTest
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5NotificationsScenarioTest {

    @Autowired
    NotificationSender sender;

    @Test
    void loanCreatedProducesAConfirmationMessage(Scenario scenario) {
        var event = new LoanCreated(4242L, "maria@example.com", "Domain-Driven Design", "DDD-1",
                LocalDate.now().plusDays(14));

        scenario.publish(event)
                .andWaitForStateChange(() -> sender.sent().stream()
                        .anyMatch(message -> message.contains("maria@example.com")), arrived -> arrived)
                .andVerify(arrived -> assertThat(sender.sent())
                        .anyMatch(message -> message.contains("Domain-Driven Design")
                                && message.contains("DDD-1")));
    }
}

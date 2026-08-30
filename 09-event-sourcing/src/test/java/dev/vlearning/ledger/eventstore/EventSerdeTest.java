package dev.vlearning.ledger.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import dev.vlearning.ledger.domain.AccountEvent;
import dev.vlearning.ledger.domain.AccountEvent.AccountClosed;
import dev.vlearning.ledger.domain.AccountEvent.AccountOpened;
import dev.vlearning.ledger.domain.AccountEvent.MoneyDeposited;
import dev.vlearning.ledger.domain.AccountEvent.MoneyWithdrawn;

/**
 * ENABLED: the persisted JSON contract. Every event record round-trips; record equality
 * makes the assertion trivial. When you add FeeCharged/FeeRefunded in step 6, extend
 * {@code allEventTypes} — the serde itself needs no change (the sealed interface is the
 * registry).
 */
class EventSerdeTest {

    private final EventSerde serde = new EventSerde();

    static List<AccountEvent> allEventTypes() {
        return List.of(
                new AccountOpened("acc-1", "Ada"),
                new MoneyDeposited("acc-1", 100_00, "salary"),
                new MoneyWithdrawn("acc-1", 40_00, "rent"),
                new AccountClosed("acc-1"));
    }

    @ParameterizedTest
    @MethodSource("allEventTypes")
    void everyEventTypeRoundTrips(AccountEvent event) {
        var json = serde.toJson(event);
        var back = serde.fromJson(serde.typeName(event), json);

        assertThat(back).isEqualTo(event);
    }

    @Test
    void typeNameIsTheSimpleClassName() {
        assertThat(serde.typeName(new AccountOpened("acc-1", "Ada"))).isEqualTo("AccountOpened");
    }

    @Test
    void unknownTypeFailsLoudlyAndPointsAtUpcasting() {
        assertThatThrownBy(() -> serde.fromJson("BalanceCorrected", "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BalanceCorrected")
                .hasMessageContaining("upcaster");
    }

    @Test
    void payloadIsPlainReadableJson() {
        // The store should be inspectable with psql — no binary blobs, no class names.
        var json = serde.toJson(new MoneyDeposited("acc-1", 100_00, "salary"));

        assertThat(json).contains("\"accountId\"").contains("\"amountCents\"").contains("10000")
                .doesNotContain("dev.vlearning");
    }
}

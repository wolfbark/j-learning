package dev.vlearning.trips.messages;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.messages.TripMessage.PaymentFailed;
import dev.vlearning.trips.messages.TripMessage.TripRequested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripMessageCodecTest {

    private final TripMessageCodec codec = new TripMessageCodec();

    @Test
    void roundTripsEveryFieldIncludingMoney() {
        var original = new TripRequested(UUID.randomUUID(), "Ada Lovelace", "Lisbon", new BigDecimal("499.50"));

        var decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void wireFormatIsReadableByHumansWithAConsoleConsumer() {
        var tripId = UUID.randomUUID();
        var json = codec.encode(new PaymentFailed(tripId, "Card declined"));

        assertThat(json)
                .contains("\"type\":\"PaymentFailed\"")
                .contains(tripId.toString())
                .contains("Card declined");
    }

    @Test
    void refusesMessagesFromOutsideTheContract() {
        assertThatThrownBy(() -> codec.decode("""
                {"type": "CancelEverything", "data": {}}"""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CancelEverything");
    }
}

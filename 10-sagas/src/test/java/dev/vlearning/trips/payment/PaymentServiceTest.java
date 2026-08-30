package dev.vlearning.trips.payment;

import java.math.BigDecimal;
import java.util.UUID;

import dev.vlearning.trips.chaos.ChaosToggles;
import dev.vlearning.trips.messages.MessageBus;
import dev.vlearning.trips.messages.TripMessage;
import dev.vlearning.trips.messages.TripMessage.PaymentCaptured;
import dev.vlearning.trips.messages.TripMessage.PaymentFailed;
import dev.vlearning.trips.messages.TripMessage.PaymentRefunded;
import dev.vlearning.trips.messages.TripTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final TripTopics TOPICS =
            new TripTopics("trips.events", "trips.flight.commands", "trips.hotel.commands", "trips.payment.commands");

    @Mock
    private Payments payments;

    @Mock
    private MessageBus bus;

    private final ChaosToggles chaos = new ChaosToggles();

    private PaymentService service;

    private final UUID tripId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, bus, chaos, TOPICS);
    }

    @Test
    void captureStoresThePaymentAndAnnouncesIt() {
        service.capture(tripId, new BigDecimal("499.50"));

        verify(payments).insertCaptured(tripId, new BigDecimal("499.50"));
        assertThat(publishedEvent()).isInstanceOfSatisfying(PaymentCaptured.class,
                captured -> assertThat(captured.amount()).isEqualByComparingTo("499.50"));
    }

    @Test
    void chaosFailNextDeclinesWithoutCapturing() {
        chaos.failNext("payment");

        service.capture(tripId, new BigDecimal("499.50"));

        verify(payments, never()).insertCaptured(any(), any());
        assertThat(publishedEvent()).isInstanceOfSatisfying(PaymentFailed.class,
                failed -> assertThat(failed.reason()).contains("declined"));
    }

    @Test
    void refundIsAnIdempotentCompensation() {
        when(payments.markRefunded(tripId)).thenReturn(0);

        service.refund(tripId);

        assertThat(publishedEvent()).isInstanceOf(PaymentRefunded.class);
    }

    private TripMessage publishedEvent() {
        var captor = ArgumentCaptor.forClass(TripMessage.class);
        verify(bus).publish(eq(TOPICS.events()), captor.capture());
        return captor.getValue();
    }
}

package com.vlearning.tdd.outsidein;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Your working test class for the London-school round (step 3).
 *
 * <p>The first interaction is already specified — an unrecognised object must
 * be passed to the coin return. Continue from here: pick the next TESTLIST.md
 * item, write the failing interaction test, and let the mocks tell you what
 * the controller needs.
 */
@ExtendWith(MockitoExtension.class)
class VendingControllerTest {

    @Mock CoinValidator coinValidator;
    @Mock ProductCatalog catalog;
    @Mock Dispenser dispenser;
    @Mock CoinReturn coinReturn;
    @Mock Display display;

    @InjectMocks VendingController controller;

    @Test
    void anUnrecognisedObjectFallsThroughToTheCoinReturn() {
        var buttonSizedSlug = new PhysicalCoin(3.10, 22.00);
        when(coinValidator.classify(buttonSizedSlug)).thenReturn(Optional.empty());

        controller.insertCoin(buttonSizedSlug);

        verify(coinReturn).reject(buttonSizedSlug);
        verifyNoInteractions(dispenser);
    }
}

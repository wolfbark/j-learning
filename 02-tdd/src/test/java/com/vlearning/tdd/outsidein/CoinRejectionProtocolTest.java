package com.vlearning.tdd.outsidein;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Protocol-level specification of the coin rejection path. */
@ExtendWith(MockitoExtension.class)
class CoinRejectionProtocolTest {

    @Mock CoinValidator coinValidator;
    @Mock ProductCatalog catalog;
    @Mock Dispenser dispenser;
    @Mock CoinReturn coinReturn;
    @Mock Display display;

    @InjectMocks VendingController controller;

    @Test
    void rejectionFollowsTheInternalProtocolExactly() {
        var slug = new PhysicalCoin(1.00, 16.00);
        when(coinValidator.classify(slug)).thenReturn(Optional.empty());

        controller.insertCoin(slug);

        InOrder order = inOrder(coinValidator, coinReturn);
        order.verify(coinValidator, times(1)).classify(slug);
        order.verify(coinReturn, times(1)).reject(slug);
        verifyNoMoreInteractions(coinValidator, catalog, dispenser, coinReturn, display);
    }
}

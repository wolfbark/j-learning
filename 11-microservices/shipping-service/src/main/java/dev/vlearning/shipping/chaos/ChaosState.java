package dev.vlearning.shipping.chaos;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

@Component
public class ChaosState {

    private final AtomicReference<ChaosMode> mode = new AtomicReference<>(ChaosMode.OK);

    public ChaosMode mode() {
        return mode.get();
    }

    public void set(ChaosMode newMode) {
        mode.set(newMode);
    }
}

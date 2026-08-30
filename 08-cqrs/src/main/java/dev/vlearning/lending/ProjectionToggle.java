package dev.vlearning.lending;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * Kill switch for the projector you will build in step 3. Unused in the given
 * code — from step 4 on, the projector must consult it before applying an
 * event, so tests (and you) can simulate projector downtime without killing
 * the JVM. Pausing does not queue events: whatever happens while paused is
 * simply never projected. That is deliberate — it is what step 5 heals.
 */
@Component
public class ProjectionToggle {

    private final AtomicBoolean paused = new AtomicBoolean(false);

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public boolean isPaused() {
        return paused.get();
    }
}

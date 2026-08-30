package vlearning.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step 5 — Replace the ThreadLocal request context with a ScopedValue.
 *
 * The discriminating test is the nested scope: the old callWith() clears the
 * ThreadLocal in its finally block, so an inner scope silently WIPES the outer
 * one — a real ThreadLocal bug class. ScopedValue rebinds for the dynamic extent
 * of the inner scope and restores the outer binding automatically.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5ScopedValueTest {

    @Test
    void currentThrowsWhenNoContextIsBound() {
        assertThatThrownBy(RequestContext::current)
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void isSetReportsFalseOutsideAnyScope() {
        assertThat(RequestContext.isSet()).isFalse();
    }

    @Test
    void theBindingIsVisibleInsideTheScope() {
        RequestContext ctx = new RequestContext("req-1", "EU");

        RequestContext seen = RequestContext.callWith(ctx, RequestContext::current);

        assertThat(seen).isEqualTo(ctx);
        assertThat(RequestContext.isSet()).isFalse();
    }

    @Test
    void aNestedScopeShadowsAndThenRestoresTheOuterBinding() {
        RequestContext outer = new RequestContext("req-1", "EU");
        RequestContext inner = new RequestContext("req-2", "US");

        List<RequestContext> observed = RequestContext.callWith(outer, () -> {
            RequestContext seenInInner = RequestContext.callWith(inner, RequestContext::current);
            // With the ThreadLocal implementation, current() is already null here.
            return List.of(seenInInner, RequestContext.current());
        });

        assertThat(observed).containsExactly(inner, outer);
    }

    @Test
    void theThreadLocalIsGone() {
        Field[] fields = RequestContext.class.getDeclaredFields();

        assertThat(Arrays.stream(fields))
                .as("RequestContext should hold no ThreadLocal")
                .noneMatch(f -> ThreadLocal.class.isAssignableFrom(f.getType()));
        assertThat(Arrays.stream(fields))
                .as("RequestContext should hold a ScopedValue")
                .anyMatch(f -> f.getType() == ScopedValue.class);
    }
}

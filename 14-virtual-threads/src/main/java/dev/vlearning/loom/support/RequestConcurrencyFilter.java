package dev.vlearning.loom.support;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.stereotype.Component;

/**
 * Counts how many requests are being processed at the same instant, measured on
 * the request thread itself.
 *
 * <p>This is the number that answers "how many users can this application serve
 * concurrently", and it is deliberately separate from the downstream call
 * counter: once you fan out in step 3, one request can have three calls in
 * flight, so downstream concurrency stops being a measure of *request*
 * concurrency. Only the web layer's thread model moves this number.
 */
@Component
public class RequestConcurrencyFilter implements Filter {

    private final ConcurrencyMeter meter = new ConcurrencyMeter();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            meter.measure(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    public ConcurrencyMeter meter() {
        return meter;
    }
}

package dev.vlearning.reliability.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Every request gets an id, and the id is the only reason a support ticket can
 * become a log query. Accepts an inbound id so the id survives a hop between
 * services; generates one otherwise.
 */
@Component
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String inbound = request instanceof HttpServletRequest http ? http.getHeader(HEADER) : null;
        String id = (inbound == null || inbound.isBlank())
                ? UUID.randomUUID().toString().substring(0, 12)
                : inbound;
        MDC.put(MDC_KEY, id);
        if (response instanceof HttpServletResponse http) {
            http.setHeader(HEADER, id);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The id for the request currently being handled, or {@code "none"} outside one. */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "none" : id;
    }
}

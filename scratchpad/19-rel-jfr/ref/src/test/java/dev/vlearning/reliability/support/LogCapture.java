package dev.vlearning.reliability.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Captures what a logger actually emitted, so a test can assert on log
 * <em>events</em> rather than on a formatted string. This is the only way to
 * make "our logs are queryable" a property the build enforces instead of a
 * habit that decays.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previousLevel;

    private LogCapture(Logger logger) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture on(Class<?> type) {
        return new LogCapture((Logger) LoggerFactory.getLogger(type));
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** Events at INFO or above — the ones that reach production log storage. */
    public List<ILoggingEvent> operationalEvents() {
        return events().stream().filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO)).toList();
    }

    /**
     * Every machine-readable field on an event: SLF4J key/value pairs plus MDC,
     * with keys normalised to snake_case so {@code correlationId} and
     * {@code correlation_id} are the same field. Both are legitimate ways to
     * attach structure; neither is prose.
     */
    public static Map<String, String> fields(ILoggingEvent event) {
        var fields = new LinkedHashMap<String, String>();
        event.getMDCPropertyMap().forEach((k, v) -> fields.put(snakeCase(k), String.valueOf(v)));
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs()
                    .forEach(kv -> fields.put(snakeCase(kv.key), String.valueOf(kv.value)));
        }
        return fields;
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }
}

package dev.vlearning.apisecurity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.vlearning.apisecurity.audit.AuditLogger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unglamorous third of API security: how often something may happen, where the secrets live, and
 * what ends up in the log aggregator that half the company can read.
 */
@Disabled("Checkpoint 7 — enable when you start step 7")
class Checkpoint7LimitsSecretsAuditTest extends AbstractSecurityTest {

    private static final String CARD_NUMBER = "4111111111111111";

    @Value("${expense.audit.hmac-key}")
    private String hmacKey;

    /**
     * Business-flow abuse (API6:2023): every one of these is a syntactically valid approval by a
     * genuine manager. The only thing wrong with them is the rate. Note that the failed attempts have
     * to count too, which is why the limiter belongs in front of the handler, not inside it.
     */
    @Test
    void approvals_are_rate_limited() {
        String carol = tokenFor(CAROL);
        List<Integer> statuses = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            statuses.add(postJson("/api/expenses/2/approve", carol, "").statusCode());
        }

        assertThat(statuses.getFirst()).as("the first approval is legitimate").isEqualTo(200);
        assertThat(statuses).as("statuses were %s", statuses).contains(429);
    }

    @Test
    void the_configuration_file_holds_no_literal_secrets() throws IOException {
        Path config = Path.of("src", "main", "resources", "application.properties");
        assertThat(config).exists();

        List<String> offenders = Files.readAllLines(config).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                .filter(line -> {
                    String key = line.substring(0, line.indexOf('=')).toLowerCase();
                    return key.matches(".*(password|secret|hmac-key|signing-key|api-key|token|credential).*");
                })
                .filter(line -> !line.substring(line.indexOf('=') + 1).strip().matches("\\$\\{[^}]+}"))
                .toList();

        assertThat(offenders)
                .as("a secret in a properties file is a secret in git history, forever")
                .isEmpty();
    }

    /**
     * An audit event has to survive being read by people who are not allowed to see the data it
     * describes: keep subject, action, object and decision; drop everything else.
     */
    @Test
    void audit_events_keep_the_decision_and_drop_the_payload() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            postJson("/api/expenses", tokenFor(ALICE), """
                    {"ownerUsername":"alice","team":"alpha","merchant":"Blue Bottle Coffee",
                     "amountCents":1250,"currency":"EUR","category":"MEALS",
                     "cardNumber":"%s","employeeEmail":"alice@example.com"}
                    """.formatted(CARD_NUMBER));
            postJson("/api/expenses/2/approve", tokenFor(CAROL), "");

            String output = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));

            assertThat(output)
                    .as("who did what to which object, and what was decided")
                    .contains("alice").contains("create")
                    .contains("carol").contains("approve").contains("ALLOW");

            assertThat(output).as("card numbers").doesNotContain(CARD_NUMBER);
            assertThat(output).as("card numbers, partially masked or not").doesNotContain("411111");
            assertThat(output).as("email addresses").doesNotContain("alice@example.com");
            assertThat(output).as("bearer tokens").doesNotContain("eyJ");
            if (!hmacKey.isBlank()) {
                assertThat(output).as("the signing key itself").doesNotContain(hmacKey);
            }
        }
        finally {
            auditLogger.detachAppender(appender);
        }
    }
}

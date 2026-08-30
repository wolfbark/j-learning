package dev.vlearning.apisecurity.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Every mutating call goes through here. Compliance asked for "a full audit trail", and this is what
 * they got.
 */
@Component
public class AuditLogger {

    public static final String LOGGER_NAME = "audit";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private final String hmacKey;

    public AuditLogger(@Value("${expense.audit.hmac-key}") String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public void record(String subject, String action, Object objectId, String decision,
                       Object requestBody, String authorizationHeader) {
        log.info("audit key={} subject={} action={} object={} decision={} body={} authorization={}",
                hmacKey, subject, action, objectId, decision, requestBody, authorizationHeader);
    }
}

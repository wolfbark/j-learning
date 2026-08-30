package dev.vlearning.apisecurity.legacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The internal admin page that nobody has rewritten yet. It authenticates with a session cookie, not
 * a bearer token — which is exactly why step 5 exists.
 */
@RestController
@RequestMapping("/session")
public class LegacyPreferencesController {

    private final Map<String, String> preferences = new ConcurrentHashMap<>(
            Map.of("approvalThresholdCents", "50000", "notifyByEmail", "true"));

    /**
     * Standard single-page-app handshake: read the CSRF token before doing anything unsafe.
     * Returns an empty object while CSRF protection is switched off — which it currently is.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        if (token == null) {
            return Map.of();
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("headerName", token.getHeaderName());
        body.put("parameterName", token.getParameterName());
        body.put("token", token.getToken());
        return body;
    }

    @GetMapping("/preferences")
    public Map<String, String> read() {
        return Map.copyOf(preferences);
    }

    @PostMapping("/preferences")
    public Map<String, String> write(@RequestBody Map<String, String> update) {
        preferences.putAll(update);
        return Map.copyOf(preferences);
    }
}

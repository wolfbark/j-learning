package dev.vlearning.apisecurity.security;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the interesting bits out of a Keycloak access token. Handed to you so that the lesson is
 * about authorization decisions rather than claim archaeology.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Keycloak puts the login name in {@code preferred_username}. */
    public static String username(Jwt jwt) {
        return jwt == null ? "anonymous" : jwt.getClaimAsString("preferred_username");
    }

    /** Realm roles live in {@code realm_access.roles} — nested, which is why Spring cannot map them for you. */
    @SuppressWarnings("unchecked")
    public static List<String> realmRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }
}

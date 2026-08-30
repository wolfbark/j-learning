package dev.vlearning.shipping.support;

import com.jayway.jsonpath.JsonPath;

/** Minimal JsonPath sugar for assertions: everything comes back as a string. */
public final class Json {

    private Json() {
    }

    public static String read(String json, String path) {
        Object value = JsonPath.read(json, path);
        return value == null ? null : value.toString();
    }
}

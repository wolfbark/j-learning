package dev.vlearning.orders.support;

import org.springframework.http.HttpHeaders;

/** One HTTP exchange, as the customer saw it: status, body, headers — and how long it took. */
public record HttpResult(int status, String body, HttpHeaders headers, long elapsedMillis) {

    public String json(String path) {
        return Json.read(body, path);
    }
}

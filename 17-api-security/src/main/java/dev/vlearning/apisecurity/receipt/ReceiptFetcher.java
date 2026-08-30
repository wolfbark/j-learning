package dev.vlearning.apisecurity.receipt;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Employees kept emailing receipts to themselves, so we let the app pull them from a link."
 */
@Component
public class ReceiptFetcher {

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public FetchedReceipt fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "receipt host answered " + response.statusCode());
            }
            return new FetchedReceipt(response.headers().firstValue("content-type").orElse("application/octet-stream"),
                    response.body().length);
        }
        catch (ResponseStatusException e) {
            throw e;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "interrupted while fetching the receipt");
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "could not fetch the receipt: " + e.getMessage());
        }
    }
}

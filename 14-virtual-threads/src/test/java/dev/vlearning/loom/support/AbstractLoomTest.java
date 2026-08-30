package dev.vlearning.loom.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.vlearning.loom.downstream.DownstreamService;
import dev.vlearning.loom.pool.ScarcePool;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Shared plumbing: a real server on a real port (concurrency behaviour is the
 * subject here, so MockMvc's single-threaded dispatch would defeat the point),
 * plus the meters reset before every test.
 */
public abstract class AbstractLoomTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected DownstreamService downstream;

    @Autowired
    protected ScarcePool pool;

    @Autowired
    protected RequestConcurrencyFilter requests;

    protected final LoadHarness harness = new LoadHarness();

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @BeforeEach
    void resetMeters() {
        downstream.reset();
        pool.reset();
        requests.meter().reset();
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected HttpResponse<String> get(String path, String correlationId) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                .header("X-Correlation-Id", correlationId)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}

package dev.vlearning.orders;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The behavior pin for the whole lesson: place an order over HTTP, see it in
 * Postgres, see the event on the broker, see fulfillment react. This test
 * stays enabled (and green) from the naive dual write through the outbox
 * refactoring — if it ever fails, you changed behavior, not just plumbing.
 */
class HappyPathIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern UUID_IN_RESPONSE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    MockMvc mvc;

    @Test
    void placedOrderReachesBothTheDatabaseAndFulfillment() throws Exception {
        try (var probe = newProbe()) {
            var body = mvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "customer": "Ada Lovelace",
                                      "items": [
                                        {"sku": "KB-42",   "quantity": 2, "unitPrice": 59.50},
                                        {"sku": "MOUSE-7", "quantity": 1, "unitPrice": 25.00}
                                      ]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").exists())
                    .andReturn().getResponse().getContentAsString();

            var matcher = UUID_IN_RESPONSE.matcher(body);
            assertThat(matcher.find()).isTrue();
            var orderId = UUID.fromString(matcher.group());

            // 1. the database has the order
            assertThat(orderExists(orderId)).isTrue();

            // 2. the broker got the event
            var records = probe.awaitRecords(1, Duration.ofSeconds(15));
            assertThat(records.getFirst())
                    .contains(orderId.toString())
                    .contains("Ada Lovelace");

            // 3. fulfillment reacted to it
            await().atMost(Duration.ofSeconds(15)).untilAsserted(
                    () -> assertThat(fulfillmentTaskCount(orderId)).isEqualTo(1));
        }
    }
}

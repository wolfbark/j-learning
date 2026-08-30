package dev.vlearning.shipping;

import java.util.UUID;

import dev.vlearning.shipping.chaos.ChaosMode;
import dev.vlearning.shipping.chaos.ChaosState;
import dev.vlearning.shipping.shipment.ShipmentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavior of the shipping-service on its own — no Kafka, no other service.
 * The chaos switch is part of the contract: it is how the lesson injects the
 * failures the order-service must learn to survive.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShipmentApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ChaosState chaos;

    @Autowired
    ShipmentStore store;

    @BeforeEach
    void calmChaos() {
        chaos.set(ChaosMode.OK);
        store.clear();
    }

    private String shipmentJson(UUID orderId) {
        return """
                {"orderId":"%s","item":"mechanical keyboard","quantity":2}""".formatted(orderId);
    }

    @Test
    void arrangesAShipment() throws Exception {
        var orderId = UUID.randomUUID();

        mvc.perform(post("/shipments").contentType(MediaType.APPLICATION_JSON).content(shipmentJson(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentId").value(startsWith("SHP-")))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("ARRANGED"));
    }

    @Test
    void aShipmentCanBeFetchedAgain() throws Exception {
        var orderId = UUID.randomUUID();
        mvc.perform(post("/shipments").contentType(MediaType.APPLICATION_JSON).content(shipmentJson(orderId)))
                .andExpect(status().isCreated());

        var shipmentId = store.findByOrderId(orderId).orElseThrow().shipmentId();
        mvc.perform(get("/shipments/{id}", shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void chaosDownRefusesShipmentsUntilCalmedAgain() throws Exception {
        mvc.perform(post("/chaos").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"DOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DOWN"));

        mvc.perform(post("/shipments").contentType(MediaType.APPLICATION_JSON).content(shipmentJson(UUID.randomUUID())))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(post("/chaos").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"OK\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/shipments").contentType(MediaType.APPLICATION_JSON).content(shipmentJson(UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    @Test
    void chaosModeIsReadable() throws Exception {
        mvc.perform(get("/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("OK"));

        mvc.perform(post("/chaos").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SLOW_5S\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/chaos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SLOW_5S"));
    }

    @Test
    void unknownChaosModesAreRejected() throws Exception {
        mvc.perform(post("/chaos").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"MAYHEM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonsenseShipmentRequestsAreRejected() throws Exception {
        mvc.perform(post("/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":null,\"item\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}

package com.vlearning.bdd.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole HTTP layer, tested once. Everything about *what shipping costs* is specified
 * in free-shipping.feature against the domain; this test only proves the wire mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mapsAShippingQuoteRequestOntoTheDomainAndBack() throws Exception {
        mockMvc.perform(post("/api/checkout/shipping-quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCharged": "12.00", "tier": "GOLD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value("0.00"))
                .andExpect(jsonPath("$.reason").value("GOLD_MEMBER"));
    }

    @Test
    void chargesTheStandardRateForASmallGuestOrder() throws Exception {
        mockMvc.perform(post("/api/checkout/shipping-quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCharged": "12.00", "tier": "GUEST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value("4.95"))
                .andExpect(jsonPath("$.reason").value("STANDARD_RATE"));
    }
}

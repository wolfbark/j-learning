package com.vlearning.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlearning.bdd.pricing.MemberTier;
import com.vlearning.bdd.shipping.ShippingQuote;
import com.vlearning.bdd.shipping.ShippingService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;

public class FreeShippingSteps {

    private final ShippingService shippingService;

    private MemberTier tier = MemberTier.GUEST;
    private ShippingQuote quote;

    FreeShippingSteps(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @Given("the customer is a {tier}")
    public void theCustomerIsA(MemberTier tier) {
        this.tier = tier;
    }

    @When("the order charges €{euros}")
    public void theOrderCharges(BigDecimal amountCharged) {
        this.quote = shippingService.quoteFor(amountCharged, tier);
    }

    @Then("shipping is free")
    public void shippingIsFree() {
        assertThat(quote.isFree()).isTrue();
    }

    @Then("shipping costs €{euros}")
    public void shippingCosts(BigDecimal expected) {
        assertThat(quote.cost()).isEqualByComparingTo(expected);
    }

    @Then("the shipping reason is {string}")
    public void theShippingReasonIs(String reason) {
        assertThat(quote.reason().name()).isEqualTo(reason);
    }
}

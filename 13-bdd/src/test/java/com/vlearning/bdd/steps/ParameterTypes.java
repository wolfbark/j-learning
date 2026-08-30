package com.vlearning.bdd.steps;

import com.vlearning.bdd.pricing.MemberTier;
import io.cucumber.java.ParameterType;
import java.math.BigDecimal;

public class ParameterTypes {

    @ParameterType("\\d+\\.\\d{2}")
    public BigDecimal euros(String amount) {
        return new BigDecimal(amount);
    }

    @ParameterType("guest|member|gold member")
    public MemberTier tier(String name) {
        return switch (name) {
            case "guest" -> MemberTier.GUEST;
            case "member" -> MemberTier.MEMBER;
            case "gold member" -> MemberTier.GOLD;
            default -> throw new IllegalArgumentException("unknown tier: " + name);
        };
    }
}

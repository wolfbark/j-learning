package com.vlearning.bdd.pricing;

/** Membership levels and the percentage each one is worth on a qualifying order. */
public enum MemberTier {

    GUEST(0),
    MEMBER(10),
    GOLD(20);

    private final int discountPercentage;

    MemberTier(int discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public int discountPercentage() {
        return discountPercentage;
    }
}

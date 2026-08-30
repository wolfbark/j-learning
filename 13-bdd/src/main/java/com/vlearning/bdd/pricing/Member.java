package com.vlearning.bdd.pricing;

/** The customer checking out, with the loyalty points they hold before this order. */
public record Member(MemberTier tier, int pointBalance) {

    public Member {
        if (tier == null) {
            throw new IllegalArgumentException("tier is required");
        }
        if (pointBalance < 0) {
            throw new IllegalArgumentException("point balance cannot be negative");
        }
    }

    public static Member guest() {
        return new Member(MemberTier.GUEST, 0);
    }

    public static Member member(int pointBalance) {
        return new Member(MemberTier.MEMBER, pointBalance);
    }

    public static Member gold(int pointBalance) {
        return new Member(MemberTier.GOLD, pointBalance);
    }
}

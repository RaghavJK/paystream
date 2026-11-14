package com.rk.paystream.payment;

public final class Topics {
    public static final String INITIATED = "payments.initiated";
    public static final String SCORED = "payments.scored";
    public static final String SCORED_DLT = SCORED + ".DLT";

    private Topics() {
    }
}

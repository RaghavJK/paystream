package com.rk.paystream.payment;

/** The requested change is not allowed from the payment's current status. Mapped to HTTP 409. */
public class PaymentStateException extends RuntimeException {

    public PaymentStateException(String message) {
        super(message);
    }
}

package com.rk.paystream.payment;

/** The same Idempotency-Key was reused with a different request body. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}

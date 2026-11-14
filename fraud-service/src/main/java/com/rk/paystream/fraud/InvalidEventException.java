package com.rk.paystream.fraud;

/** The record parsed as JSON but is not a usable event. Retrying cannot fix it, so it goes straight to the DLT. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}

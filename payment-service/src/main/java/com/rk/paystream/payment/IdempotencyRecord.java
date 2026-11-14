package com.rk.paystream.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "idem_key")
    private String key;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public IdempotencyRecord(String key, UUID paymentId, String requestHash) {
        this.key = key;
        this.paymentId = paymentId;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
    }

    public String getKey() { return key; }
    public UUID getPaymentId() { return paymentId; }
    public String getRequestHash() { return requestHash; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.rk.paystream.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

    public enum Status { INITIATED, APPROVED, FLAGGED, ON_HOLD }

    @Id
    private UUID id;

    @Column(name = "debtor_account", nullable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_reason")
    private String riskReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "held_at")
    private Instant heldAt;

    @Version
    private long version;

    protected Payment() {
        // for JPA
    }

    public static Payment initiate(String debtorAccount, String creditorAccount, BigDecimal amount, String currency) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.debtorAccount = debtorAccount;
        p.creditorAccount = creditorAccount;
        p.amount = amount;
        p.currency = currency;
        p.status = Status.INITIATED;
        p.createdAt = Instant.now();
        return p;
    }

    /**
     * Idempotent: applying the same score twice leaves the payment unchanged.
     * A payment already on hold keeps that status, so a replayed score event cannot release it.
     */
    public void applyScore(int score, String decision, String reason) {
        this.riskScore = score;
        this.riskReason = reason;
        if (this.status != Status.ON_HOLD) {
            this.status = "FLAGGED".equals(decision) ? Status.FLAGGED : Status.APPROVED;
        }
    }

    /**
     * Places a FLAGGED payment on hold. Holding a payment that is already on hold is a no-op, so
     * the caller can safely retry. Any other status is refused: only a flagged payment may be held.
     */
    public void hold(String reason) {
        if (status == Status.ON_HOLD) {
            return;
        }
        if (status != Status.FLAGGED) {
            throw new PaymentStateException("Only a FLAGGED payment can be put on hold; this one is " + status);
        }
        this.status = Status.ON_HOLD;
        this.holdReason = reason;
        this.heldAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDebtorAccount() { return debtorAccount; }
    public String getCreditorAccount() { return creditorAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Integer getRiskScore() { return riskScore; }
    public String getRiskReason() { return riskReason; }
    public Instant getCreatedAt() { return createdAt; }
    public String getHoldReason() { return holdReason; }
    public Instant getHeldAt() { return heldAt; }
}

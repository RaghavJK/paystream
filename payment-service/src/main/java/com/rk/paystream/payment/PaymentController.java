package com.rk.paystream.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    static final int MAX_KEY_LENGTH = 100;

    public record PaymentRequest(@NotBlank @Size(max = 64) String debtorAccount,
                                 @NotBlank @Size(max = 64) String creditorAccount,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /**
     * 201 Created for a new payment; 200 OK when the Idempotency-Key was seen before and the
     * original payment is returned; 422 when the key is reused with a different body.
     */
    @PostMapping
    public ResponseEntity<Payment> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        if (idempotencyKey != null && idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        PaymentService.CreateResult result = service.create(idempotencyKey, request.debtorAccount(),
                request.creditorAccount(), request.amount(), request.currency());
        Payment payment = result.payment();
        if (result.replayed()) {
            return ResponseEntity.ok(payment);
        }
        return ResponseEntity.created(URI.create("/api/payments/" + payment.getId())).body(payment);
    }

    public record HoldRequest(@NotBlank @Size(max = 300) String reason) {
    }

    /**
     * Puts a FLAGGED payment on hold. Safe to repeat. 404 if the payment does not exist,
     * 409 if its status does not allow a hold.
     *
     * Called by agent-service only after a human approves a hold request.
     * TODO (roadmap step 14): require authentication; today anyone who can reach this port can call it.
     */
    @PostMapping("/{id}/hold")
    public ResponseEntity<Payment> hold(@PathVariable UUID id, @Valid @RequestBody HoldRequest request) {
        return ResponseEntity.of(service.hold(id, request.reason()));
    }

    @GetMapping
    public List<Payment> recent() {
        return service.recent();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> get(@PathVariable UUID id) {
        return ResponseEntity.of(service.find(id));
    }
}

package com.rk.paystream.agent;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-in-the-loop gate. The agent can only PROPOSE a state-changing action; it becomes an
 * Approval in PENDING. A person approves or rejects it through ApprovalController.
 *
 * In-memory on purpose for now: approvals are lost on restart and not shared between instances.
 * TODO (roadmap step 8): persist approvals, and record WHO decided once authentication exists.
 */
@Component
public class ApprovalStore {

    public enum Status { PENDING, APPROVED, REJECTED }

    public record Approval(String id, String paymentId, String reason, Status status,
                           Instant createdAt, Instant decidedAt, String outcome) {
    }

    private final ConcurrentHashMap<String, Approval> approvals = new ConcurrentHashMap<>();

    /** One pending request per payment: asking twice returns the existing request. */
    public synchronized Approval propose(String paymentId, String reason) {
        Optional<Approval> existing = approvals.values().stream()
                .filter(a -> a.paymentId().equals(paymentId) && a.status() == Status.PENDING)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        Approval approval = new Approval(UUID.randomUUID().toString(), paymentId, reason, Status.PENDING,
                Instant.now(), null, null);
        approvals.put(approval.id(), approval);
        return approval;
    }

    /**
     * Moves PENDING to APPROVED or REJECTED exactly once, even if two people click at the same time.
     * Returns empty if the approval does not exist or was already decided.
     */
    public Optional<Approval> decide(String id, boolean approve, String outcome) {
        Approval[] decided = new Approval[1];
        approvals.computeIfPresent(id, (key, current) -> {
            if (current.status() != Status.PENDING) {
                return current;
            }
            decided[0] = new Approval(current.id(), current.paymentId(), current.reason(),
                    approve ? Status.APPROVED : Status.REJECTED, current.createdAt(), Instant.now(), outcome);
            return decided[0];
        });
        return Optional.ofNullable(decided[0]);
    }

    /** Replaces the outcome text of an already-decided approval, for example with the result of the action. */
    public Optional<Approval> recordOutcome(String id, String outcome) {
        return Optional.ofNullable(approvals.computeIfPresent(id, (key, current) ->
                new Approval(current.id(), current.paymentId(), current.reason(), current.status(),
                        current.createdAt(), current.decidedAt(), outcome)));
    }

    public Optional<Approval> find(String id) {
        return Optional.ofNullable(approvals.get(id));
    }

    public List<Approval> all() {
        return approvals.values().stream()
                .sorted(Comparator.comparing(Approval::createdAt).reversed())
                .toList();
    }
}

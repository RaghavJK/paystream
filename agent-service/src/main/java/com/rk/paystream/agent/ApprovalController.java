package com.rk.paystream.agent;

import com.rk.paystream.agent.ApprovalStore.Approval;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The human side of the approval gate. These endpoints are called by a person through the UI,
 * never by the agent: there is no tool that maps to them.
 *
 * TODO (roadmap step 14): require authentication and record the approver's identity.
 */
@RestController
@RequestMapping("/agent/approvals")
public class ApprovalController {

    private final ApprovalStore store;
    private final HoldExecutor executor;

    public ApprovalController(ApprovalStore store, HoldExecutor executor) {
        this.store = store;
        this.executor = executor;
    }

    @GetMapping
    public List<Approval> all() {
        return store.all();
    }

    @PostMapping("/{id}/approve")
    public Approval approve(@PathVariable String id) {
        Approval pending = store.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such approval"));
        if (pending.status() != ApprovalStore.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval already " + pending.status());
        }
        // Mark the decision first: decide() succeeds for exactly one caller, so the hold runs at most once.
        Approval decided = store.decide(id, true, "Approved; executing")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Approval already decided"));
        String outcome = executor.hold(decided.paymentId(), decided.reason());
        return store.recordOutcome(id, outcome).orElse(decided);
    }

    @PostMapping("/{id}/reject")
    public Approval reject(@PathVariable String id) {
        store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such approval"));
        return store.decide(id, false, "Rejected by reviewer")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Approval already decided"));
    }
}

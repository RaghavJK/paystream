package com.rk.paystream.fraud;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskAssessmentRepository repository;

    public RiskController(RiskAssessmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<RiskAssessment> get(@PathVariable String paymentId) {
        return ResponseEntity.of(repository.findById(paymentId));
    }

    /** The 50 most recently flagged payments, newest first: a work queue for a reviewer. */
    @GetMapping("/flagged")
    public List<RiskAssessment> flagged() {
        return repository.findTop50ByDecisionOrderByScoredAtDesc(FraudRules.FLAGGED);
    }
}

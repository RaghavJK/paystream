package com.rk.paystream.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One error contract for the API: RFC 9457 problem details (application/problem+json).
 * Validation errors, malformed JSON and unknown routes are handled by Spring itself because
 * spring.mvc.problemdetails.enabled=true; only domain errors need a handler here.
 */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Idempotency key reused with a different request");
        return problem;
    }

    @ExceptionHandler(PaymentStateException.class)
    public ProblemDetail invalidState(PaymentStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Payment status does not allow this action");
        return problem;
    }
}

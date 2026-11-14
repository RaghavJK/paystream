# ADR 0001: Start with a thin working slice and known flaws

Date: 2026-11-14 · Status: accepted; shortcuts 1 to 3 were removed from payment-service by ADR 0002

## Context

PayStream exists to give its owner first-hand experience of Kafka, event-driven design, agentic AI,
full-stack delivery and Kubernetes. A finished system teaches less than building one.

## Decision

Ship one request flowing end to end, with deliberate, labelled shortcuts:

1. `PaymentService.create` saves to PostgreSQL and then publishes to Kafka (a dual write).
2. Kafka listeners have no error handler or dead-letter topic.
3. `POST /api/payments` has no idempotency key.
4. Events are unversioned JSON strings.
5. The agent defaults to a deterministic stub; the Bedrock adapter is written but not yet run.

Each shortcut is a numbered step in the README roadmap.

## Consequences

- The slice is small enough to read in one sitting.
- Each fix is a self-contained exercise with a before and an after, which makes a good interview story.
- Until steps 1 to 3 are done, the system can lose or duplicate events. It must not be described as production-ready.

## Template for the next ADR

Copy this file. Record: context, the options you considered, the decision, and what gets worse because of it.

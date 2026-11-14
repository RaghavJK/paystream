# ADR 0002: Outbox, dead-letter topic and idempotency keys in payment-service

Date: 2026-11-14 · Status: accepted, not yet verified by integration tests

## Context

The thin slice saved a payment and then published to Kafka: a dual write that loses the event if the
process dies between the two calls. The consumer had no error handling, and a client retry of
`POST /api/payments` created a second payment.

## Decisions

### 1. Transactional outbox with a polling relay

The payment row and an `outbox` row are written in one PostgreSQL transaction. `OutboxRelay` polls
every 500 ms, locks a batch with `FOR UPDATE SKIP LOCKED`, publishes each row, waits for the broker
acknowledgement (`acks=all`) and marks the row sent.

- Considered: change data capture with Debezium. Lower latency and no polling, but another runtime
  component to operate. Polling is enough at this scale and easier to reason about.
- Considered: Kafka transactions. They make Kafka-to-Kafka atomic, not database-to-Kafka.
- Gets worse: up to 500 ms extra latency; the outbox table grows until sent rows are cleaned up;
  delivery is at-least-once, so a row can be published twice if the commit fails after the send.

### 2. Retry with backoff, then a dead-letter topic

A failing record is retried 3 times (0.5 s, 1 s, 2 s), then published to `payments.scored.DLT`.
Unparseable records skip the retries.

- Considered: non-blocking retry topics (`@RetryableTopic`). They do not block the partition during
  backoff, but they break ordering per key. Scores for one payment must apply in order, so blocking
  retries with a short budget were chosen.
- Gets worse: a partition is blocked for up to 3.5 seconds per poison record; someone must watch
  and drain the DLT.

### 3. Idempotency-Key header

The key, the payment ID and a SHA-256 hash of the request are stored in `idempotency_record` in the
same transaction as the payment. The primary key on the key column decides a race between two
concurrent requests: the loser's transaction rolls back and it returns the winner's payment.

- Same key, same body: 200 with the original payment.
- Same key, different body: 422.
- No key: every request creates a payment.
- Considered: storing keys in Redis. Faster, but then the key and the payment are not written
  atomically, which reintroduces the problem this solves.
- Gets worse: one extra row and one extra lookup per request; keys are kept forever until expiry is added.

## What is not done

- No integration test proves any of this against real PostgreSQL and Kafka yet.
- `fraud-service` still has no error handler or dead-letter topic.
- Sent outbox rows and old idempotency keys are never deleted.

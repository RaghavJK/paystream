# ADR 0003: fraud-service reliability, rule engine and velocity rule

Date: 2026-11-14 · Status: accepted, not yet verified by integration tests

## Context

`fraud-service` saved an assessment and then published the score without waiting for the result.
A failed publish was silent: the payment stayed `INITIATED` for ever. It had no error handling for
bad records, and its rules were one static method with hard-coded thresholds.

## Decisions

### 1. Synchronous publish inside the listener, no outbox

The listener saves the assessment, publishes the score and blocks until the broker acknowledges.
Any failure throws, so the offset is not committed and the record is processed again.

- Why this is safe: the save is an upsert by payment ID, and `payment-service` applies a score idempotently.
- Considered: an outbox in MongoDB. It needs multi-document transactions, which need a replica set,
  and solves nothing extra here because the input record can always be reprocessed.
- Considered: Kafka transactions (consume-process-produce). They would make the offset commit and the
  publish atomic, but MongoDB sits outside the transaction, so idempotency would still be required.
- Gets worse: throughput. One blocking send per record caps the consumer at roughly one broker
  round trip per payment per partition. Batch listeners are the next step if that matters.

### 2. Retry with backoff, then `payments.initiated.DLT`

Same policy as `payment-service` (ADR 0002). JSON that cannot be parsed and events with missing
fields are not retried.

### 3. Rule engine as a strategy pattern with configurable thresholds

Each rule is a `FraudRule` bean returning an optional hit (rule name, points, reason). `FraudRules`
sums the points, caps at 100 and decides. Thresholds come from `paystream.fraud.*`.

- Considered: a rules engine such as Drools. Too heavy for five rules.
- Gets worse: more classes to read than one static method.

### 4. Velocity rule by counting in MongoDB

The rule counts the debtor's other assessments in the 60 seconds before the event's `occurredAt`.
Assessments now store `debtorAccount`, `occurredAt` and the list of hits, with a compound index on
debtor and time.

- Considered: Kafka Streams windowed count on a stream re-keyed by debtor. Exact and scalable, and
  the right long-term answer, but a larger step. It is roadmap step 6.
- Gets worse: the count is approximate under concurrency, because two payments from one debtor can
  be scored at the same moment on different partitions. It is also one extra query per payment.

## What is not done

- No integration test against real MongoDB and Kafka.
- Nobody consumes either dead-letter topic.
- The velocity rule is approximate (see above).

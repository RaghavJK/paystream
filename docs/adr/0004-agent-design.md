# ADR 0004: Agent design: bounded tools, human approval, deterministic controls

Date: 2026-11-14 · Status: accepted; the Bedrock path has never been compiled or run

## Context

`agent-service` lets operations staff ask about payments. An LLM is non-deterministic and can be
manipulated through its input, so the design question is not "what can the model do" but "what is
the most damage a wrong or manipulated model can cause here".

## Decisions

### 1. The model can read and propose; only a human can act

Four tools: `getPayment`, `getRiskAssessment`, `listFlaggedPayments` and `proposePaymentHold`.
None changes a payment. `proposePaymentHold` creates an approval request; a person approves or
rejects it through `ApprovalController`, and only that controller reaches `HoldExecutor`.

- Considered: a `holdPayment` tool guarded by a prompt instruction ("only when sure"). Rejected:
  a prompt is a request, not a control.
- Gets worse: every hold needs a human click. That is the intent.

### 2. Controls are code that runs outside the model

- `Guardrails` checks length and obvious injection phrases and masks long digit runs before any
  brain sees the message.
- Tools validate their own arguments, because the model supplies them.
- `ToolCallRecorder` records every tool call inside the tool, so the audit trail is identical for
  the stub and the model and cannot be skipped by either.

### 3. One port, two brains, automatic fallback

`AgentBrain` has `StubBrain` (deterministic, offline) and `BedrockBrain` (Spring AI, Bedrock
Converse API). With the `bedrock` profile the model answers; if the call fails for any reason the
stub answers and the reply says so.

- Why: builds and tests need no AWS; an outage degrades the feature instead of removing it.
- Gets worse: the stub understands three intents by keyword, so a fallback answer is cruder.

### 4. Conversation memory is a sliding window, in memory

Spring AI `MessageWindowChatMemory` (20 messages) keyed by conversation ID for Bedrock; the stub
remembers the last payment per conversation.

- Gets worse: memory is lost on restart and is not shared between instances.

### 5. Timeouts on tool HTTP calls, no circuit breaker yet

Connect 1 s, read 2 s. Failures come back as data so the brain can explain them.

## Known weaknesses

- The phrase-list guardrail is easy to evade. Bedrock Guardrails are not configured.
- Tool results are not screened for injected instructions; the system prompt asks the model to
  treat them as data, which is a request, not a control.
- Approvals are in memory, unauthenticated, and an approved hold does nothing because
  `payment-service` cannot hold a payment yet.
- No rate limit or per-user token budget.
- No evaluation set: there is no measured answer quality for the Bedrock path.

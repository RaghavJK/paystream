package com.rk.paystream.agent;

/**
 * Port for "the thing that decides what to say and which tools to call".
 * Two adapters: StubBrain (always present, offline, deterministic) and BedrockBrain (profile "bedrock").
 *
 * A brain only produces text. Guardrails, tool-call auditing and the HTTP contract live in
 * AgentController, so they apply identically to every brain.
 */
public interface AgentBrain {

    /** Token counts reported by the model; null for the stub. */
    record Usage(Integer inputTokens, Integer outputTokens) {
    }

    record Answer(String text, String brain, Usage usage) {
    }

    /**
     * @param conversationId identifies one chat, so the brain can remember earlier turns
     * @param userMessage    already checked and redacted by Guardrails
     */
    Answer answer(String conversationId, String userMessage);
}

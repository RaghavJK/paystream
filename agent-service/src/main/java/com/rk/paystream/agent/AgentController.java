package com.rk.paystream.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    public record ChatRequest(@NotBlank @Size(max = Guardrails.MAX_LENGTH) String message,
                              @Size(max = 64) String conversationId) {
    }

    public record ChatReply(String reply, String brain, String conversationId,
                            List<ToolCallRecorder.ToolCall> toolCalls, AgentBrain.Usage usage) {
    }

    private final AgentBrain brain;
    private final Guardrails guardrails;
    private final ToolCallRecorder recorder;

    public AgentController(AgentBrain brain, Guardrails guardrails, ToolCallRecorder recorder) {
        this.brain = brain;
        this.guardrails = guardrails;
        this.recorder = recorder;
    }

    /**
     * One turn of a conversation. Send the returned conversationId back on the next turn so the
     * agent remembers which payment you were discussing.
     */
    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();

        Optional<String> refusal = guardrails.violation(request.message());
        if (refusal.isPresent()) {
            return new ChatReply(refusal.get(), "guardrail", conversationId, List.of(), null);
        }

        recorder.begin();
        AgentBrain.Answer answer;
        List<ToolCallRecorder.ToolCall> calls;
        try {
            answer = brain.answer(conversationId, guardrails.redact(request.message()));
        } finally {
            calls = recorder.end();
        }

        // Audit line: who answered, which tools ran, how long each took. No message text, to keep PII out of logs.
        log.info("conversation={} brain={} tools={}", conversationId, answer.brain(), calls);
        return new ChatReply(answer.text(), answer.brain(), conversationId, calls, answer.usage());
    }
}

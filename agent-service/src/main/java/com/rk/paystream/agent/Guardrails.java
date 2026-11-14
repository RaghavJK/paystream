package com.rk.paystream.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic checks that run BEFORE any brain sees the message. They are code, not prompts,
 * so a model cannot be talked out of them.
 *
 * This is a first line of defence only. A phrase list will not stop a determined attacker.
 * TODO (roadmap step 8): add Amazon Bedrock Guardrails on the model call, and screen tool
 * RESULTS as well, since text coming back from a tool can carry instructions too.
 */
@Component
public class Guardrails {

    static final int MAX_LENGTH = 2000;

    private static final List<String> INJECTION_PHRASES = List.of(
            "ignore previous instructions", "ignore all previous", "disregard the above",
            "reveal your system prompt", "show your system prompt", "you are now", "act as the system");

    // 12 to 19 consecutive digits (optionally separated by spaces or dashes) looks like a card or account number.
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    // A payment ID is matched first and kept, so an ID that happens to be mostly digits is never masked.
    private static final Pattern UUID_OR_LONG_NUMBER = Pattern.compile(UUID + "|\\b\\d(?:[ -]?\\d){11,18}\\b");

    /** Returns a refusal message if the request must not be processed. */
    public Optional<String> violation(String message) {
        if (message == null || message.isBlank()) {
            return Optional.of("Ask me a question about a payment.");
        }
        if (message.length() > MAX_LENGTH) {
            return Optional.of("That message is too long. Keep it under " + MAX_LENGTH + " characters.");
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (INJECTION_PHRASES.stream().anyMatch(lower::contains)) {
            return Optional.of("I can only help with questions about payments and their risk assessments.");
        }
        return Optional.empty();
    }

    /** Masks long digit runs so card and account numbers are not sent to a model or written to logs. */
    public String redact(String message) {
        return UUID_OR_LONG_NUMBER.matcher(message)
                .replaceAll(match -> match.group().matches(UUID) ? match.group() : "[number removed]");
    }
}

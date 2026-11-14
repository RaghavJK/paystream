package com.rk.paystream.agent;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for the LLM: no AWS account, no cost, same tools.
 *
 * It shows the agent loop in plain Java: read the request, pick tools, call them, compose an
 * answer. Where a model would reason, this class uses keyword rules, so it only understands
 * three intents: explain a payment, list flagged payments, and propose a hold.
 *
 * Always registered, because BedrockBrain falls back to it when the model call fails.
 */
@Component
public class StubBrain implements AgentBrain {

    static final String NAME = "stub";
    private static final int MAX_CONVERSATIONS = 500;
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final PaymentTools tools;

    // Conversation memory: the last payment discussed in each conversation, least-recently-used evicted.
    private final Map<String, String> lastPaymentByConversation = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    public StubBrain(PaymentTools tools) {
        this.tools = tools;
    }

    @Override
    public Answer answer(String conversationId, String userMessage) {
        String message = userMessage == null ? "" : userMessage;
        String lower = message.toLowerCase(Locale.ROOT);

        Matcher matcher = UUID_PATTERN.matcher(message);
        String id = matcher.find() ? matcher.group() : lastPaymentByConversation.get(conversationId);

        if (id == null) {
            if (lower.contains("flagged")) {
                return reply(describeFlagged());
            }
            return reply("Give me a payment ID and I will check its status and risk assessment. "
                    + "You can also ask which payments are flagged.");
        }
        lastPaymentByConversation.put(conversationId, id);

        Map<String, Object> payment = tools.getPayment(id);
        if (payment.containsKey("error")) {
            return reply("I could not find payment " + id + ".");
        }
        Map<String, Object> risk = tools.getRiskAssessment(id);
        String status = String.valueOf(payment.get("status"));
        boolean flagged = "FLAGGED".equals(status);

        StringBuilder text = new StringBuilder()
                .append("Payment ").append(id).append(" for ")
                .append(payment.get("amount")).append(' ').append(payment.get("currency"))
                .append(" is ").append(payment.get("status")).append('.');
        if (!risk.containsKey("error")) {
            text.append(" Risk score ").append(risk.get("score")).append(" (").append(risk.get("reason")).append(").");
        }

        if ("ON_HOLD".equals(status)) {
            text.append(" It is already on hold; no further action is needed from me.");
        } else if (asksForHold(lower)) {
            if (flagged) {
                Map<String, Object> proposal = tools.proposePaymentHold(id, String.valueOf(risk.get("reason")));
                text.append(" I have raised hold request ").append(proposal.get("approvalId"))
                        .append(" for a human reviewer. The payment is not on hold until it is approved.");
            } else {
                text.append(" I have not proposed a hold because the payment is not flagged.");
            }
        } else {
            text.append(flagged
                    ? " Suggested next step: ask me to propose a hold, or confirm the beneficiary with the customer."
                    : " No action needed.");
        }
        return reply(text.toString());
    }

    /** "held" is deliberately absent: "why is it held?" is a question, not a request. */
    private static boolean asksForHold(String lower) {
        return lower.contains("hold") || lower.contains("freeze") || lower.contains("block it")
                || lower.contains("block this") || lower.contains("stop it") || lower.contains("stop this");
    }

    private String describeFlagged() {
        List<Map<String, Object>> flagged = tools.listFlaggedPayments();
        if (flagged.isEmpty()) {
            return "No payments are flagged right now.";
        }
        if (flagged.get(0).containsKey("error")) {
            return "I could not reach fraud-service to list flagged payments.";
        }
        StringBuilder text = new StringBuilder(flagged.size() + " flagged payment(s). Most recent: ");
        flagged.stream().limit(3).forEach(a -> text.append(a.get("paymentId"))
                .append(" (score ").append(a.get("score")).append("); "));
        return text.toString().trim();
    }

    private static Answer reply(String text) {
        return new Answer(text, NAME, null);
    }
}

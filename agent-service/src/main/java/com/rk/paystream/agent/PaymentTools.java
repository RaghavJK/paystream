package com.rk.paystream.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The tools the agent may call.
 *
 * Three are read-only. The fourth, proposePaymentHold, does NOT change a payment: it files a
 * request that a human must approve (see ApprovalStore). There is deliberately no tool that
 * changes state directly, so nothing the model says can move money or hold a payment on its own.
 *
 * Every call is timed and recorded by ToolCallRecorder. Failures are returned as data
 * ({"error": ...}) so the brain can explain them instead of the request blowing up.
 *
 * TODO (roadmap step 4): add retry and a circuit breaker (Resilience4j) around the HTTP calls.
 */
@Component
public class PaymentTools {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient payments;
    private final RestClient fraud;
    private final ApprovalStore approvals;
    private final ToolCallRecorder recorder;

    public PaymentTools(@Value("${paystream.payment-service-url}") String paymentUrl,
                        @Value("${paystream.fraud-service-url}") String fraudUrl,
                        @Value("${paystream.tools.connect-timeout-ms:1000}") long connectTimeoutMs,
                        @Value("${paystream.tools.read-timeout-ms:2000}") long readTimeoutMs,
                        ApprovalStore approvals, ToolCallRecorder recorder) {
        // Without explicit timeouts a slow downstream would hang the agent, and the user, indefinitely.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.payments = RestClient.builder().baseUrl(paymentUrl).requestFactory(factory).build();
        this.fraud = RestClient.builder().baseUrl(fraudUrl).requestFactory(factory).build();
        this.approvals = approvals;
        this.recorder = recorder;
    }

    @Tool(description = "Look up a payment by its ID. Returns amount, currency, accounts, status and risk score.")
    public Map<String, Object> getPayment(@ToolParam(description = "The payment ID, a UUID") String paymentId) {
        return recorder.record("getPayment", paymentId,
                () -> invalidId(paymentId) ? notAnId() : fetch(payments, "/api/payments/{id}", paymentId));
    }

    @Tool(description = "Look up the fraud risk assessment for a payment: score, decision, reason and the rules that fired.")
    public Map<String, Object> getRiskAssessment(@ToolParam(description = "The payment ID, a UUID") String paymentId) {
        return recorder.record("getRiskAssessment", paymentId,
                () -> invalidId(paymentId) ? notAnId() : fetch(fraud, "/api/risk/{id}", paymentId));
    }

    @Tool(description = "List the most recently flagged payments, newest first, with their scores and reasons.")
    public List<Map<String, Object>> listFlaggedPayments() {
        return recorder.record("listFlaggedPayments", "", () -> {
            try {
                List<Map<String, Object>> body = fraud.get().uri("/api/risk/flagged").retrieve().body(LIST);
                return body != null ? body : List.of();
            } catch (RestClientException e) {
                return List.of(Map.of("error", "fraud-service unavailable: " + e.getMessage()));
            }
        });
    }

    @Tool(description = "Propose placing a payment on hold. This does NOT hold the payment. It creates a request "
            + "that a human reviewer must approve. Use only when the user explicitly asks for a hold.")
    public Map<String, Object> proposePaymentHold(
            @ToolParam(description = "The payment ID, a UUID") String paymentId,
            @ToolParam(description = "Why the payment should be held, in one sentence") String reason) {
        return recorder.record("proposePaymentHold", paymentId, () -> {
            if (invalidId(paymentId)) {
                return notAnId();
            }
            String safeReason = (reason == null || reason.isBlank()) ? "No reason given" : truncate(reason, 300);
            ApprovalStore.Approval approval = approvals.propose(paymentId, safeReason);
            return Map.<String, Object>of(
                    "approvalId", approval.id(),
                    "status", "PENDING_HUMAN_APPROVAL",
                    "note", "The payment is NOT on hold. A human reviewer must approve this request first.");
        });
    }

    private Map<String, Object> fetch(RestClient client, String path, String id) {
        try {
            Map<String, Object> body = client.get().uri(path, id).retrieve().body(OBJECT);
            return body != null ? body : Map.of("error", "empty response");
        } catch (RestClientException e) {
            return Map.of("error", "not found or service unavailable: " + e.getMessage());
        }
    }

    /** The model supplies tool arguments, so validate them like any other untrusted input. */
    private static boolean invalidId(String paymentId) {
        return paymentId == null || !UUID_FORMAT.matcher(paymentId).matches();
    }

    private static Map<String, Object> notAnId() {
        return Map.of("error", "not a valid payment ID");
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}

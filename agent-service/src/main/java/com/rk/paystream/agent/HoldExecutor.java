package com.rk.paystream.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Port for the action that runs AFTER a human approves a hold. Only ApprovalController calls it;
 * no agent tool can reach it.
 */
public interface HoldExecutor {

    /** Returns a short description of what happened, stored on the approval and shown to the reviewer. */
    String hold(String paymentId, String reason);

    /**
     * Calls POST /api/payments/{id}/hold on payment-service. That endpoint is idempotent, so a
     * repeat is harmless. A failure is reported in the returned text and never thrown: the
     * approval stays APPROVED with the failure recorded, and the reviewer can see it.
     *
     * TODO (roadmap step 8): if the call fails, the approval is APPROVED but the payment is not
     * held. Add a retry, or a way for the reviewer to re-run an approved hold.
     */
    @Component
    class ViaPaymentService implements HoldExecutor {

        private static final Logger log = LoggerFactory.getLogger(ViaPaymentService.class);

        private final RestClient payments;

        public ViaPaymentService(@Value("${paystream.payment-service-url}") String paymentUrl,
                                 @Value("${paystream.tools.connect-timeout-ms:1000}") long connectTimeoutMs,
                                 @Value("${paystream.tools.read-timeout-ms:2000}") long readTimeoutMs) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
            this.payments = RestClient.builder().baseUrl(paymentUrl).requestFactory(factory).build();
        }

        @Override
        public String hold(String paymentId, String reason) {
            try {
                payments.post().uri("/api/payments/{id}/hold", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", reason))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Payment {} placed on hold after human approval", paymentId);
                return "Payment placed ON_HOLD";
            } catch (RestClientResponseException e) {
                log.warn("Hold for payment {} refused by payment-service: {}", paymentId, e.getStatusCode());
                return "Approved, but payment-service refused the hold (HTTP " + e.getStatusCode().value() + ")";
            } catch (RestClientException e) {
                log.warn("Hold for payment {} failed: {}", paymentId, e.toString());
                return "Approved, but payment-service could not be reached. The payment is NOT on hold.";
            }
        }
    }
}

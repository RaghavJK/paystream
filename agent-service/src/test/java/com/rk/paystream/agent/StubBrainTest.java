package com.rk.paystream.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StubBrainTest {

    private static final String ID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    private static final String CONVERSATION = "conv-1";

    private PaymentTools tools;
    private StubBrain brain;

    @BeforeEach
    void setUp() {
        tools = mock(PaymentTools.class);
        brain = new StubBrain(tools);
    }

    private void flaggedPayment() {
        when(tools.getPayment(ID)).thenReturn(Map.of("amount", 250000, "currency", "INR", "status", "FLAGGED"));
        when(tools.getRiskAssessment(ID)).thenReturn(Map.of("score", 70, "reason", "High amount; Round amount"));
    }

    @Test
    void asksForAPaymentIdWhenNoneIsGiven() {
        AgentBrain.Answer answer = brain.answer(CONVERSATION, "why was my payment blocked?");

        assertThat(answer.text()).contains("payment ID");
        assertThat(answer.brain()).isEqualTo("stub");
        verifyNoInteractions(tools);
    }

    @Test
    void explainsAFlaggedPaymentUsingBothLookupTools() {
        flaggedPayment();

        AgentBrain.Answer answer = brain.answer(CONVERSATION, "Why is payment " + ID + " flagged?");

        assertThat(answer.text()).contains("FLAGGED").contains("High amount").contains("Risk score 70");
        verify(tools, never()).proposePaymentHold(anyString(), anyString());
    }

    @Test
    void remembersThePaymentWithinAConversation() {
        flaggedPayment();
        brain.answer(CONVERSATION, "Tell me about " + ID);

        AgentBrain.Answer followUp = brain.answer(CONVERSATION, "what was its risk score again?");

        assertThat(followUp.text()).contains(ID).contains("Risk score 70");
    }

    @Test
    void doesNotLeakMemoryIntoAnotherConversation() {
        flaggedPayment();
        brain.answer(CONVERSATION, "Tell me about " + ID);

        AgentBrain.Answer other = brain.answer("conv-2", "what was its risk score again?");

        assertThat(other.text()).contains("Give me a payment ID");
    }

    @Test
    void proposesAHoldOnlyWhenAskedAndSaysItIsNotYetHeld() {
        flaggedPayment();
        when(tools.proposePaymentHold(ID, "High amount; Round amount"))
                .thenReturn(Map.of("approvalId", "appr-1", "status", "PENDING_HUMAN_APPROVAL"));

        AgentBrain.Answer answer = brain.answer(CONVERSATION, "Please hold payment " + ID);

        assertThat(answer.text()).contains("appr-1").contains("not on hold until it is approved");
    }

    @Test
    void refusesToProposeAHoldForAPaymentThatIsNotFlagged() {
        when(tools.getPayment(ID)).thenReturn(Map.of("amount", 100, "currency", "INR", "status", "APPROVED"));
        when(tools.getRiskAssessment(ID)).thenReturn(Map.of("score", 0, "reason", "No rule triggered"));

        AgentBrain.Answer answer = brain.answer(CONVERSATION, "hold " + ID);

        assertThat(answer.text()).contains("not proposed a hold");
        verify(tools, never()).proposePaymentHold(anyString(), anyString());
    }

    @Test
    void understandsFreezeAsARequestToHoldButNotTheQuestionWhyIsItHeld() {
        flaggedPayment();
        when(tools.proposePaymentHold(ID, "High amount; Round amount")).thenReturn(Map.of("approvalId", "appr-2"));

        assertThat(brain.answer(CONVERSATION, "Why is " + ID + " held?").text()).doesNotContain("hold request");
        assertThat(brain.answer(CONVERSATION, "freeze it").text()).contains("appr-2");
    }

    @Test
    void saysSoWhenThePaymentIsAlreadyOnHold() {
        when(tools.getPayment(ID)).thenReturn(Map.of("amount", 250000, "currency", "INR", "status", "ON_HOLD"));
        when(tools.getRiskAssessment(ID)).thenReturn(Map.of("score", 70, "reason", "High amount"));

        AgentBrain.Answer answer = brain.answer(CONVERSATION, "hold " + ID);

        assertThat(answer.text()).contains("already on hold");
        verify(tools, never()).proposePaymentHold(anyString(), anyString());
    }

    @Test
    void listsFlaggedPaymentsWhenAskedWithoutAnId() {
        when(tools.listFlaggedPayments()).thenReturn(List.of(Map.of("paymentId", ID, "score", 70)));

        AgentBrain.Answer answer = brain.answer(CONVERSATION, "which payments are flagged?");

        assertThat(answer.text()).contains("1 flagged payment").contains(ID);
    }

    @Test
    void saysSoWhenThePaymentDoesNotExist() {
        when(tools.getPayment(ID)).thenReturn(Map.of("error", "not found"));

        assertThat(brain.answer(CONVERSATION, "status of " + ID).text()).contains("could not find payment");
    }
}

package com.rk.paystream.agent;

import com.rk.paystream.agent.ApprovalStore.Approval;
import com.rk.paystream.agent.ApprovalStore.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalStoreTest {

    private final ApprovalStore store = new ApprovalStore();

    @Test
    void aProposalStartsPendingAndASecondProposalForTheSamePaymentReusesIt() {
        Approval first = store.propose("pay-1", "High amount");
        Approval second = store.propose("pay-1", "Asked again");

        assertThat(first.status()).isEqualTo(Status.PENDING);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(store.all()).hasSize(1);
    }

    @Test
    void anApprovalCanBeDecidedExactlyOnce() {
        Approval pending = store.propose("pay-1", "High amount");

        assertThat(store.decide(pending.id(), true, "ok")).get().extracting(Approval::status).isEqualTo(Status.APPROVED);
        assertThat(store.decide(pending.id(), false, "changed my mind")).isEmpty();
        assertThat(store.find(pending.id())).get().extracting(Approval::status).isEqualTo(Status.APPROVED);
    }

    @Test
    void afterADecisionANewProposalForTheSamePaymentIsANewRequest() {
        Approval first = store.propose("pay-1", "High amount");
        store.decide(first.id(), false, "Rejected");

        assertThat(store.propose("pay-1", "New evidence").id()).isNotEqualTo(first.id());
    }

    @Test
    void decidingAnUnknownApprovalDoesNothing() {
        assertThat(store.decide("nope", true, "ok")).isEmpty();
    }
}

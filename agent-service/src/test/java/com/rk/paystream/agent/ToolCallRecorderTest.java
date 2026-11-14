package com.rk.paystream.agent;

import com.rk.paystream.agent.ToolCallRecorder.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCallRecorderTest {

    private final ToolCallRecorder recorder = new ToolCallRecorder();

    @Test
    void recordsEachCallWithItsOutcomeInOrder() {
        recorder.begin();
        recorder.record("getPayment", "id-1", () -> Map.of("status", "FLAGGED"));
        recorder.record("getRiskAssessment", "id-1", () -> Map.of("error", "not found"));

        List<ToolCall> calls = recorder.end();

        assertThat(calls).extracting(ToolCall::tool).containsExactly("getPayment", "getRiskAssessment");
        assertThat(calls.get(0).outcome()).isEqualTo("ok");
        assertThat(calls.get(1).outcome()).isEqualTo("error: not found");
    }

    @Test
    void aToolThatThrowsIsRecordedAndTheExceptionStillPropagates() {
        recorder.begin();

        assertThatThrownBy(() -> recorder.record("getPayment", "id-1", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(recorder.end()).singleElement().extracting(ToolCall::outcome).isEqualTo("exception: IllegalStateException");
    }

    @Test
    void endClearsTheStateSoTheNextRequestStartsEmpty() {
        recorder.begin();
        recorder.record("getPayment", "id-1", () -> Map.of());
        recorder.end();

        assertThat(recorder.end()).isEmpty();
    }

    @Test
    void aToolCalledOutsideARequestStillRuns() {
        assertThat(recorder.<String>record("getPayment", "id-1", () -> "result")).isEqualTo("result");
    }
}

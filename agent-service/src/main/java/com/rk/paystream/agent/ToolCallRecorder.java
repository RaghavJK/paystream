package com.rk.paystream.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Audit trail of the tools an agent called while answering one request: which tool, with what
 * input, whether it worked and how long it took. The same record is produced whether the stub or
 * the model chose the tool, because the recording happens inside the tool itself.
 *
 * Uses a ThreadLocal because Spring AI executes tool calls on the calling thread during a
 * blocking call(). It would NOT capture tools run on other threads (for example with stream()).
 */
@Component
public class ToolCallRecorder {

    public record ToolCall(String tool, String input, String outcome, long millis) {
    }

    private final ThreadLocal<List<ToolCall>> current = new ThreadLocal<>();

    public void begin() {
        current.set(new ArrayList<>());
    }

    /** Returns the calls recorded since begin() and clears the thread's state. */
    public List<ToolCall> end() {
        List<ToolCall> calls = current.get();
        current.remove();
        return calls == null ? List.of() : List.copyOf(calls);
    }

    public <T> T record(String tool, String input, Supplier<T> call) {
        long start = System.nanoTime();
        String outcome = "ok";
        try {
            T result = call.get();
            if (result instanceof Map<?, ?> map && map.containsKey("error")) {
                outcome = "error: " + map.get("error");
            }
            return result;
        } catch (RuntimeException e) {
            outcome = "exception: " + e.getClass().getSimpleName();
            throw e;
        } finally {
            List<ToolCall> calls = current.get();
            if (calls != null) {
                calls.add(new ToolCall(tool, input, outcome, (System.nanoTime() - start) / 1_000_000));
            }
        }
    }
}

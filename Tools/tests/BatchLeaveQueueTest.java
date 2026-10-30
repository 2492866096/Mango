import org.telegram.messenger.BatchLeaveQueue;
import java.util.*;

public final class BatchLeaveQueueTest {
    static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        ArrayList<Long> calls = new ArrayList<>();
        ArrayList<BatchLeaveQueue.Completion> pending = new ArrayList<>();
        int[] changed = {0};
        BatchLeaveQueue queue = new BatchLeaveQueue((id, done) -> { calls.add(id); pending.add(done); }, () -> changed[0]++);
        queue.start(Arrays.asList(1L, 1L, 2L, 3L));
        check(calls.equals(Arrays.asList(1L)), "Only one request in flight");
        check(queue.results().size() == 3, "Duplicate selections are deduplicated");
        queue.start(Arrays.asList(9L));
        check(calls.size() == 1, "Cannot replace running batch");
        pending.get(0).complete(null, false);
        check(calls.equals(Arrays.asList(1L, 2L)), "Second starts after first confirmation");
        pending.get(0).complete("LATE_CALLBACK", false);
        check(calls.size() == 2, "Duplicate callback ignored");
        pending.get(1).complete("CHANNEL_PRIVATE", false);
        check(calls.equals(Arrays.asList(1L, 2L, 3L)), "Ordinary failure continues batch");
        check(queue.result(2).state == BatchLeaveQueue.State.FAILED, "Failure recorded per target");
        pending.get(2).complete(null, false);
        check(!queue.isRunning(), "Batch completes");
        queue.retryFailures();
        check(calls.equals(Arrays.asList(1L, 2L, 3L, 2L)), "Retry only failures; successful exits not repeated");
        pending.get(3).complete(null, false);
        check(queue.result(2).state == BatchLeaveQueue.State.LEFT, "Successful retry replaces error");
        queue.start(Arrays.asList(4L, 5L, 6L));
        queue.stop();
        check(queue.result(5).state == BatchLeaveQueue.State.CANCELLED, "Stop cancels queued exits");
        check(queue.isRunning(), "In-flight request remains observed after stop");
        pending.get(4).complete(null, false);
        check(!queue.isRunning() && calls.size() == 5, "No later request sent after stop");
        queue.start(Arrays.asList(7L, 8L));
        pending.get(5).complete("FLOOD_WAIT_90", true);
        check(queue.result(7).state == BatchLeaveQueue.State.FAILED, "Flood wait retains failure");
        check(queue.result(8).state == BatchLeaveQueue.State.CANCELLED && !queue.isRunning(), "Flood wait stops remaining requests");
        pending.get(4).complete(null, false);
        check(calls.size() == 6, "Stale prior-batch callback ignored");
        check(changed[0] > 10, "Changes notify UI throughout batch");
        System.out.println("PASS: " + checks + " batch-exit queue checks");
    }
}

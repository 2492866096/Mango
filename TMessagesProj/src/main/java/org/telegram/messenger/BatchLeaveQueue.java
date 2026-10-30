package org.telegram.messenger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Serial, user-started operations; retries only requeue failed entries. UI-thread confined. */
public final class BatchLeaveQueue {
    public enum State { QUEUED, RUNNING, LEFT, FAILED, CANCELLED }
    public interface Completion { void complete(String error, boolean stopRemaining); }
    public interface Worker { void leave(long id, Completion completion); }
    public static final class Result {
        public final long id;
        public State state;
        public String error;
        Result(long id) { this.id = id; state = State.QUEUED; }
    }
    private final Worker worker;
    private final Runnable changed;
    private final LinkedHashMap<Long, Result> results = new LinkedHashMap<>();
    private boolean running;
    private boolean stopped;
    private int generation;

    public BatchLeaveQueue(Worker worker, Runnable changed) {
        this.worker = worker;
        this.changed = changed;
    }
    public boolean isRunning() { return running; }
    public List<Result> results() { return new ArrayList<>(results.values()); }
    public Result result(long id) { return results.get(id); }
    public void start(List<Long> ids) {
        if (running || ids.isEmpty()) return;
        generation++;
        results.clear();
        for (long id : ids) if (id > 0) results.put(id, new Result(id));
        stopped = false;
        running = !results.isEmpty();
        next();
    }
    public void retryFailures() {
        if (running) return;
        boolean any = false;
        for (Result result : results.values()) {
            if (result.state == State.FAILED) {
                result.state = State.QUEUED;
                result.error = null;
                any = true;
            }
        }
        if (!any) return;
        generation++;
        stopped = false;
        running = true;
        next();
    }
    public void stop() {
        stopped = true;
        for (Result result : results.values()) {
            if (result.state == State.QUEUED) result.state = State.CANCELLED;
        }
        changed.run();
    }
    private void next() {
        if (!running) { changed.run(); return; }
        Result candidate = null;
        if (!stopped) for (Result result : results.values()) {
            if (result.state == State.QUEUED) { candidate = result; break; }
        }
        if (candidate == null) {
            running = false;
            changed.run();
            return;
        }
        final Result current = candidate;
        final int token = generation;
        current.state = State.RUNNING;
        changed.run();
        worker.leave(current.id, (error, stopRemaining) -> {
            if (token != generation || current.state != State.RUNNING) return;
            current.error = error;
            current.state = error == null ? State.LEFT : State.FAILED;
            if (stopRemaining) stop();
            next();
        });
    }
}

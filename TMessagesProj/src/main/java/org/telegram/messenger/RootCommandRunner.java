package org.telegram.messenger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One explicit, root-only command; output and execution time are bounded. */
public final class RootCommandRunner {
    public interface Listener {
        void onOutput(String output);
        void onComplete(int exitCode, boolean rootGranted, boolean cancelled, boolean timedOut, boolean truncated);
    }
    public static final int OUTPUT_LIMIT = 65536;
    public static final long TIMEOUT_MS = 60000;
    private final String executable;
    private final long timeoutMillis;
    private volatile Process process;
    private volatile boolean cancelled, finished, rootGranted, truncated;
    private boolean started;
    private int emitted;

    public RootCommandRunner() { this("su", TIMEOUT_MS); }
    // Package-private seam for testing process failure, cancellation and timeout without invoking a real su.
    RootCommandRunner(String executable, long timeoutMillis) { this.executable = executable; this.timeoutMillis = timeoutMillis; }

    public synchronized void start(String command, Listener listener) {
        if (started) throw new IllegalStateException("Already started");
        started = true;
        new Thread(() -> execute(command, listener), "extension-root-command").start();
    }
    public void cancel() {
        cancelled = true;
        Process current = process;
        if (current != null) current.destroy();
    }
    private synchronized void output(String value, Listener listener) {
        if (finished || value.isEmpty()) return;
        int allowed = Math.min(value.length(), OUTPUT_LIMIT - emitted);
        if (allowed > 0) listener.onOutput(value.substring(0, allowed));
        emitted += allowed;
        if (allowed < value.length()) truncated = true;
    }
    private void read(Process target, String marker, Listener listener) {
        try (InputStreamReader reader = new InputStreamReader(target.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder handshake = new StringBuilder();
            int ch;
            while (!finished && !cancelled && (ch = reader.read()) != -1) {
                if (ch == '\n') {
                    if (handshake.toString().trim().equals(marker)) { rootGranted = true; break; }
                    output(handshake + "\n", listener);
                    handshake.setLength(0);
                } else if (handshake.length() < 2048) {
                    handshake.append((char) ch);
                } else truncated = true;
            }
            char[] buffer = new char[2048];
            int count;
            while (rootGranted && !finished && !cancelled && (count = reader.read(buffer)) != -1)
                output(new String(buffer, 0, count), listener);
        } catch (Exception ignored) { }
    }

    private void execute(String command, Listener listener) {
        String marker = "__MANGO_ROOT_" + UUID.randomUUID().toString().replace("-", "") + "__";
        int exitCode = -1;
        boolean timedOut = false;
        Thread reader = null;
        long startNanos = System.nanoTime();
        try {
            if (cancelled) return;
            // Verification and execution occur inside the SAME su shell; no unprivileged fallback.
            String script = "if [ \"$(id -u)\" != 0 ]; then exit 126; fi\nprintf '%s\\n' '" + marker + "'\n" + command;
            process = new ProcessBuilder(executable, "-c", script).redirectErrorStream(true).start();
            Process current = process;
            reader = new Thread(() -> read(current, marker, listener), "extension-root-output");
            // A command can fork and leave stdout open after its shell exits; it must never hold the UI task open.
            reader.setDaemon(true);
            reader.start();
            while (!cancelled) {
                try { exitCode = current.exitValue(); break; } catch (IllegalThreadStateException running) { }
                if ((System.nanoTime() - startNanos) / 1000000 >= timeoutMillis) { timedOut = true; break; }
                Thread.sleep(40);
            }
            if (cancelled || timedOut) current.destroy();
            reader.join(300);
        } catch (Exception e) {
            if (!cancelled) output(e.getClass().getSimpleName() + ": " + e.getMessage() + "\n", listener);
        } finally {
            synchronized (this) { finished = true; }
            Process current = process;
            if (current != null) {
                current.destroy();
                // Closing a pipe held by a descendant must not block the completion callback.
                new Thread(() -> {
                    try { current.getOutputStream().close(); } catch (Exception ignored) { }
                    try { current.getInputStream().close(); } catch (Exception ignored) { }
                    try { current.getErrorStream().close(); } catch (Exception ignored) { }
                }, "extension-root-close").start();
            }
            process = null;
            listener.onComplete(exitCode, rootGranted, cancelled, timedOut, truncated);
        }
    }
}

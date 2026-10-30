package org.telegram.messenger;

/** Five consecutive version taps; unrelated taps and long gaps reset the sequence. */
public final class DeviceToolsUnlockGate {
    private int count;
    private long previous = -1;
    public boolean tap(boolean versionRow, long elapsedMillis) {
        if (!versionRow) { count = 0; previous = -1; return false; }
        if (previous < 0 || elapsedMillis < previous || elapsedMillis - previous > 1500) count = 0;
        previous = elapsedMillis;
        if (++count == 5) { count = 0; previous = -1; return true; }
        return false;
    }
    public void reset() { count = 0; previous = -1; }
}

package org.telegram.messenger;

public final class MangoDownloadSpeedMonitorTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        MangoDownloadSpeedMonitor.resetForTests();
        FileLoadOperation account0 = new FileLoadOperation();
        FileLoadOperation account1 = new FileLoadOperation();

        MangoDownloadSpeedMonitor.received(account0, 1000);
        check(MangoDownloadSpeedMonitor.activeCount() == 0, "bytes before start must not create a task");

        MangoDownloadSpeedMonitor.started(account0, 0);
        MangoDownloadSpeedMonitor.started(account1, 1);
        check(MangoDownloadSpeedMonitor.activeCount(0) == 1, "account 0 task count");
        check(MangoDownloadSpeedMonitor.activeCount(1) == 1, "account 1 task count");
        check(MangoDownloadSpeedMonitor.activeCount() == 2, "global task count");

        MangoDownloadSpeedMonitor.sample(0); // establish interval baseline
        MangoDownloadSpeedMonitor.sample(1);
        MangoDownloadSpeedMonitor.received(account0, 10_240);
        MangoDownloadSpeedMonitor.received(account1, 20_480);
        Thread.sleep(30);
        MangoDownloadSpeedMonitor.Snapshot a0 = MangoDownloadSpeedMonitor.sample(0);
        MangoDownloadSpeedMonitor.Snapshot a1 = MangoDownloadSpeedMonitor.sample(1);
        check(a0.activeTasks == 1 && a1.activeTasks == 1, "samples are task-isolated");
        check(a0.bytesTotal == 10_240, "account 0 bytes isolated");
        check(a1.bytesTotal == 20_480, "account 1 bytes isolated");
        check(a0.bytesPerSecond > 0 && a1.bytesPerSecond > 0, "network-byte speed computed from deltas");

        MangoDownloadSpeedMonitor.finished(account0);
        check(MangoDownloadSpeedMonitor.activeCount(0) == 0, "completed task removed");
        check(MangoDownloadSpeedMonitor.activeCount(1) == 1, "finishing one account leaves other account task");
        MangoDownloadSpeedMonitor.received(account0, 5000);
        check(MangoDownloadSpeedMonitor.sample(0).bytesTotal == 10_240, "bytes after finish ignored");

        MangoDownloadSpeedMonitor.finished(account1);
        check(MangoDownloadSpeedMonitor.activeCount() == 0, "all tasks removed");
        MangoDownloadSpeedMonitor.resetForTests();
        check(MangoDownloadSpeedMonitor.activeCount() == 0, "reset clears active tasks");
        check(MangoDownloadSpeedMonitor.sample(0).bytesTotal == 0, "reset clears byte counters and sample baselines");
        System.out.println("MangoDownloadSpeedMonitorTest: 15 assertions passed");
    }
}

package org.telegram.messenger;

/** Regression coverage for sustained and concurrent download request limits. */
public final class DownloadRequestPolicyTest {
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int small = 128 * 1024;
        int large = 512 * 1024;
        check(DownloadRequestPolicy.getAdjustedChunkSize(1, small, false) == small,
                "normal preset keeps base chunk");
        check(DownloadRequestPolicy.getAdjustedChunkSize(50, small, false) == 512 * 1024,
                "boost is capped at 512 KiB for small base chunks");
        check(DownloadRequestPolicy.getAdjustedChunkSize(50, large, false) == 1024 * 1024,
                "boost is capped at 1 MiB for large base chunks");
        check(DownloadRequestPolicy.getAdjustedChunkSize(50, small, true) == small,
                "server-requested small chunks are never expanded");
        check(DownloadRequestPolicy.getMaxRequests(4) == 4,
                "single-file request fan-out remains bounded");
        check(DownloadRequestPolicy.getMaxActiveDownloads(2) == 2,
                "small queue remains at account baseline");
        check(DownloadRequestPolicy.getMaxActiveDownloads(5) == 5,
                "large queue remains at account baseline");

        // Exercise the production slot calculation with concurrent large-file
        // operations and repeated out-of-order responses held for writing.
        int activeFiles = DownloadRequestPolicy.getMaxActiveDownloads(5);
        int maxRequestsPerFile = DownloadRequestPolicy.getMaxRequests(4);
        int[] inFlight = new int[activeFiles];
        int[] delayed = new int[activeFiles];
        for (int file = 0; file < activeFiles; file++) {
            inFlight[file] = DownloadRequestPolicy.getAvailableRequestSlots(0, 0, maxRequestsPerFile);
        }

        for (int cycle = 0; cycle < 20_000; cycle++) {
            int globalInFlight = 0;
            for (int file = 0; file < activeFiles; file++) {
                if (cycle % 7 == 0 && inFlight[file] > 0) {
                    // A later chunk completed before the missing earlier one.
                    inFlight[file]--;
                    delayed[file]++;
                } else if (inFlight[file] > 0) {
                    // Completing an earlier chunk lets held responses drain.
                    inFlight[file]--;
                    if (delayed[file] > 0) delayed[file] = 0;
                }
                int freeSlots = DownloadRequestPolicy.getAvailableRequestSlots(
                        inFlight[file], delayed[file], maxRequestsPerFile);
                inFlight[file] += freeSlots;
                check(inFlight[file] + delayed[file] <= maxRequestsPerFile,
                        "per-file request window exceeded during sustained transfer");
                check(freeSlots == 0 || inFlight[file] + delayed[file] == maxRequestsPerFile,
                        "available slots do not refill the stable request window");
                globalInFlight += inFlight[file];
            }
            check(globalInFlight <= activeFiles * maxRequestsPerFile,
                    "concurrent operations exceeded account request budget");
        }
        System.out.println("DownloadRequestPolicyTest: " + assertions + " assertions passed");
    }
}

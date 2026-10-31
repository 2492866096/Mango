package org.telegram.messenger;

/**
 * Keeps Telegram file-download request windows bounded during sustained and
 * concurrent transfers. Larger chunks reduce per-request overhead; increasing
 * both chunk size and concurrency multiplicatively can overload the shared
 * download connections and cause throughput to collapse after an initial burst.
 */
public final class DownloadRequestPolicy {
    private static final int MAX_CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_REQUESTS = 16;
    private static final long MAX_IN_FLIGHT_BYTES = 8L * 1024 * 1024;

    private DownloadRequestPolicy() {
    }

    public static int getAdjustedChunkSize(int multiplier, int baseChunkSize, boolean forceSmallChunk) {
        if (baseChunkSize <= 0 || forceSmallChunk || multiplier <= 1) {
            return baseChunkSize;
        }
        int chunkMultiplier = multiplier >= 8 ? 4 : multiplier >= 5 ? 2 : 1;
        int maxChunkSize = baseChunkSize >= 512 * 1024 ? MAX_CHUNK_SIZE : 512 * 1024;
        long adjusted = (long) baseChunkSize * chunkMultiplier;
        return (int) Math.max(baseChunkSize, Math.min(adjusted, maxChunkSize));
    }

    /** Baseline request window for callers without a throughput preset. */
    public static int getMaxRequests(int baseMaxRequests) {
        return Math.max(1, baseMaxRequests);
    }

    /**
     * Apply presets to a bounded byte window, using the operation's actual chunk
     * size. In-progress operations cannot resize chunks without invalidating
     * offsets, preload data and encrypted-file state; changing this window makes
     * their speed setting effective immediately without restarting the file.
     */
    public static int getMaxRequests(int multiplier, int baseMaxRequests, int actualChunkSize,
                                     int baseChunkSize, boolean forceSmallChunk) {
        if (forceSmallChunk || actualChunkSize <= 0 || baseChunkSize <= 0) {
            return getMaxRequests(baseMaxRequests);
        }
        int windowMultiplier = multiplier >= 50 ? 6 : multiplier >= 20 ? 4 : multiplier >= 10 ? 3 : multiplier > 1 ? 2 : 1;
        long baselineBytes = (long) Math.max(1, baseMaxRequests) * baseChunkSize;
        long windowBytes = Math.min(MAX_IN_FLIGHT_BYTES, baselineBytes * windowMultiplier);
        return (int) Math.max(1, Math.min(MAX_REQUESTS, windowBytes / actualChunkSize));
    }

    /** Remaining safe slots, including out-of-order chunks held for writing. */
    public static int getAvailableRequestSlots(int requestsInFlight, int delayedResponses, int maxRequests) {
        int occupied = Math.max(0, requestsInFlight) + Math.max(0, delayedResponses);
        return Math.max(0, Math.max(1, maxRequests) - occupied);
    }

    /**
     * Do not multiply active files when a speed preset is enabled. File queues
     * already share the same two download transports and should retain the
     * per-account limits selected by Telegram.
     */
    public static int getMaxActiveDownloads(int baseMaxActiveDownloads) {
        return Math.max(1, baseMaxActiveDownloads);
    }
}

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LaunchActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches a small public text announcement and shows each content revision once per installation. */
public final class MangoAnnouncementController {
    public static final String SOURCE_URL = "https://1812652903.cdn.123clouddisk.com/1812652903/GY/%E5%85%AC%E5%91%8A.txt";
    public static final int MAX_BYTES = 64 * 1024;
    public static final long CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private static final String PREFS = "mango_remote_announcement";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_SHOWN_DIGEST = "shown_digest";
    private static final String KEY_PENDING_DIGEST = "pending_digest";
    private static final String KEY_PENDING_TEXT = "pending_text";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mango-announcement");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean checking = new AtomicBoolean();
    private static WeakReference<LaunchActivity> activeActivity = new WeakReference<>(null);
    private static Runnable scheduledCheck;
    private static String showingDigest;

    private MangoAnnouncementController() { }

    public static void onResume(LaunchActivity activity) {
        activeActivity = new WeakReference<>(activity);
        showPending(activity);
        check(activity);
        scheduleNext();
    }

    public static void onPause(LaunchActivity activity) {
        if (activeActivity.get() != activity) return;
        activeActivity.clear();
        if (scheduledCheck != null) {
            AndroidUtilities.cancelRunOnUIThread(scheduledCheck);
            scheduledCheck = null;
        }
    }

    private static void scheduleNext() {
        if (scheduledCheck != null) AndroidUtilities.cancelRunOnUIThread(scheduledCheck);
        scheduledCheck = () -> {
            scheduledCheck = null;
            LaunchActivity activity = activeActivity.get();
            if (activity != null && activity.isResumed) {
                showPending(activity);
                check(activity);
                scheduleNext();
            }
        };
        AndroidUtilities.runOnUIThread(scheduledCheck, CHECK_INTERVAL_MS);
    }

    private static void check(LaunchActivity activity) {
        if (activity == null || !activity.isResumed || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) return;
        SharedPreferences prefs = preferences();
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS || !checking.compareAndSet(false, true)) return;
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply();
        WeakReference<LaunchActivity> activityRef = new WeakReference<>(activity);
        executor.execute(() -> {
            try {
                String text = fetch();
                String digest = digest(text);
                if (!TextUtils.isEmpty(text) && !digest.equals(prefs.getString(KEY_SHOWN_DIGEST, ""))) {
                    prefs.edit().putString(KEY_PENDING_TEXT, text).putString(KEY_PENDING_DIGEST, digest).apply();
                    AndroidUtilities.runOnUIThread(() -> showIfActive(activityRef, text, digest));
                }
            } catch (Exception error) {
                FileLog.e(error);
            } finally {
                checking.set(false);
            }
        });
    }

    private static void showIfActive(WeakReference<LaunchActivity> activityRef, String text, String digest) {
        LaunchActivity activity = activityRef.get();
        if (activity == null || !activity.isResumed || activity.isFinishing() || activity.isDestroyed()
                || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter
                || UserConfig.getInstance(UserConfig.selectedAccount).unacceptedTermsOfService != null
                || SharedConfig.pendingAppUpdate != null && SharedConfig.pendingAppUpdate.can_not_skip
                || activity.getVisibleDialog() != null) return;
        SharedPreferences prefs = preferences();
        if (digest.equals(prefs.getString(KEY_SHOWN_DIGEST, "")) || showingDigest != null) return;
        showingDigest = digest;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(text);
        Runnable acknowledge = () -> {
            prefs.edit().putString(KEY_SHOWN_DIGEST, digest)
                    .remove(KEY_PENDING_DIGEST).remove(KEY_PENDING_TEXT).apply();
            showingDigest = null;
        };
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> acknowledge.run());
        android.app.Dialog dialog = activity.showAlertDialog(builder, dismissed -> {
            // Covers back, outside-tap, and programmatic dismissal without breaking activity dialog tracking.
            if (!digest.equals(prefs.getString(KEY_SHOWN_DIGEST, ""))) acknowledge.run();
        });
        if (dialog == null) {
            showingDigest = null;
            return;
        }
    }

    private static void showPending(LaunchActivity activity) {
        if (activity == null) return;
        SharedPreferences prefs = preferences();
        String digest = prefs.getString(KEY_PENDING_DIGEST, "");
        String text = prefs.getString(KEY_PENDING_TEXT, "");
        if (TextUtils.isEmpty(digest) || TextUtils.isEmpty(text)
                || digest.equals(prefs.getString(KEY_SHOWN_DIGEST, ""))) return;
        showIfActive(new WeakReference<>(activity), text, digest);
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String normalize(String text) throws IOException {
        if (text == null || text.length() > MAX_BYTES) throw new IOException("Invalid announcement size");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        text = text.trim();
        if (text.isEmpty() || text.startsWith("{") || text.startsWith("[")
                || text.regionMatches(true, 0, "<!doctype", 0, 9)
                || text.regionMatches(true, 0, "<html", 0, 5)) {
            throw new IOException("Expected a nonempty plain-text announcement");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                throw new IOException("Unexpected announcement control character");
            }
        }
        return text;
    }

    private static String fetch() throws IOException {
        URL url = new URL(SOURCE_URL);
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
                throw new IOException("Invalid announcement redirect");
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "text/plain, application/octet-stream");
                connection.setRequestProperty("Cache-Control", "no-cache");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == 3) throw new IOException("Invalid announcement redirect");
                    url = new URL(url, location);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Announcement HTTP status " + status);
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("text/plain")
                        && !type.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
                    throw new IOException("Unexpected announcement content type");
                }
                if (connection.getContentLength() > MAX_BYTES) throw new IOException("Announcement is too large");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) throw new IOException("Announcement is too large");
                        output.write(buffer, 0, count);
                    }
                }
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
                return normalize(text);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many announcement redirects");
    }

    public static String digest(String text) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalize(text).getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte part : hash) value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        return value.toString();
    }

    public static boolean shouldShow(String shownDigest, String currentText) throws Exception {
        return !digest(currentText).equals(shownDigest == null ? "" : shownDigest);
    }
}

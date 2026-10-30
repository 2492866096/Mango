package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.os.SystemClock;

import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Automatically joins the public channels published in the Mango feedback feed. */
public final class MangoAutoJoinChannels {
    public static final String SOURCE_URL = "https://1812652903.cdn.123clouddisk.com/1812652903/GY/MG.txt";
    public static final long REFRESH_INTERVAL_MS = 2 * 60 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;
    private static final int MAX_BYTES = 32 * 1024;
    private static final int MAX_CHANNELS = 100;
    private static final DispatchQueue queue = new DispatchQueue("mangoAutoJoinChannels");
    private static final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        final String ownerKey;
        final long ownerId;
        ArrayList<String> channels = new ArrayList<>();
        ArrayList<String> pendingChannels;
        int pendingIndex;
        long updated;
        long lastAttemptElapsed;
        long retryAtElapsed;
        long floodWaitUntilElapsed;
        long requestStartedElapsed;
        int operation;
        boolean loaded;
        boolean started;
        boolean fetching;
        boolean processing;
        State(String key, long owner) { ownerKey = key; ownerId = owner; }
    }

    private MangoAutoJoinChannels() {}

    /** Reset in-flight/cached session work after a fresh authentication, including the same user. */
    public static void onAccountActivated(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            long owner = UserConfig.getInstance(account).getClientUserId();
            if (owner == 0) return;
            states.remove(account + ":" + owner);
            State state = state(account);
            state.updated = 0; // Check the latest published feed at login, regardless of cached age.
            tick(account);
        }, 1000);
    }

    private static SharedPreferences preferences(int account, long owner) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mango_feedback_channels_" + account + "_" + owner, Context.MODE_PRIVATE);
    }

    private static State state(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        String key = account + ":" + owner;
        State state = states.get(key);
        if (state == null) {
            state = new State(key, owner);
            states.put(key, state);
        }
        if (!state.loaded) {
            state.loaded = true;
            SharedPreferences prefs = preferences(account, owner);
            state.updated = prefs.getLong("updated", 0);
            try {
                String cached = prefs.getString("channels", "");
                if (!TextUtils.isEmpty(cached)) state.channels = parse(cached);
            } catch (IOException error) {
                FileLog.e(error);
            }
        }
        return state;
    }

    /** Called from the app's existing lightweight extension ticker; all accounts are isolated. */
    public static void tick() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int account : UserConfig.getActivatedAccounts()) {
                if (UserConfig.getInstance(account).isClientActivated()) tick(account);
            }
        });
    }

    private static void tick(int account) {
        State state = state(account);
        if (!state.started) {
            state.started = true;
            if (!state.channels.isEmpty()) processChannels(account, state, new ArrayList<>(state.channels));
        }
        long age = System.currentTimeMillis() - state.updated;
        long elapsed = SystemClock.elapsedRealtime();
        if (state.processing && elapsed - state.requestStartedElapsed >= 60000) {
            state.operation++;
            state.processing = false;
            state.pendingChannels = null;
            state.retryAtElapsed = elapsed + RETRY_INTERVAL_MS;
        }
        if (!state.processing && state.retryAtElapsed > 0 && elapsed >= state.retryAtElapsed) {
            state.retryAtElapsed = 0;
            processChannels(account, state, new ArrayList<>(state.channels));
        }
        boolean stale = state.updated == 0 || age < 0 || age >= REFRESH_INTERVAL_MS;
        boolean retryAllowed = state.lastAttemptElapsed == 0 || elapsed - state.lastAttemptElapsed >= RETRY_INTERVAL_MS;
        if (!state.fetching && stale && retryAllowed) refresh(account, state);
    }

    private static void refresh(int account, State state) {
        if (state.fetching || !isCurrent(account, state)) return;
        state.fetching = true;
        state.lastAttemptElapsed = SystemClock.elapsedRealtime();
        queue.postRunnable(() -> {
            ArrayList<String> channels = null;
            try { channels = parse(fetch()); }
            catch (Exception error) { FileLog.e(error); }
            final ArrayList<String> result = channels;
            AndroidUtilities.runOnUIThread(() -> {
                state.fetching = false;
                if (!isCurrent(account, state) || result == null) return;
                long now = System.currentTimeMillis();
                if (!preferences(account, state.ownerId).edit()
                        .putString("channels", join(result)).putLong("updated", now).commit()) {
                    FileLog.e("Unable to persist Mango feedback channel feed");
                    return;
                }
                state.channels = result;
                state.updated = now;
                processChannels(account, state, new ArrayList<>(result));
            });
        });
    }

    private static boolean isCurrent(int account, State state) {
        return state.ownerId != 0 && state.ownerId == UserConfig.getInstance(account).getClientUserId()
                && states.get(state.ownerKey) == state;
    }

    private static void processChannels(int account, State state, ArrayList<String> channels) {
        if (!isCurrent(account, state)) return;
        state.pendingChannels = channels;
        state.pendingIndex = 0;
        if (!state.processing) joinNext(account, state);
    }

    private static void joinNext(int account, State state) {
        if (!isCurrent(account, state)) { state.processing = false; return; }
        if (SystemClock.elapsedRealtime() < state.floodWaitUntilElapsed) {
            state.processing = false;
            state.pendingChannels = null;
            return;
        }
        if (state.pendingChannels == null || state.pendingIndex >= state.pendingChannels.size()) {
            state.pendingChannels = null;
            state.processing = false;
            return;
        }
        state.processing = true;
        state.requestStartedElapsed = SystemClock.elapsedRealtime();
        final int operation = ++state.operation;
        final String username = state.pendingChannels.get(state.pendingIndex++);
        MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (!isCurrent(account, state) || operation != state.operation) return;
            if (peerId == null || peerId >= 0) {
                if (peerId == null) state.retryAtElapsed = SystemClock.elapsedRealtime() + RETRY_INTERVAL_MS;
                joinNext(account, state);
                return;
            }
            TLRPC.Chat chat = controller.getChat(-peerId);
            if (chat == null || !ChatObject.isChannelAndNotMegaGroup(chat) || !ChatObject.isNotInChat(chat)) {
                joinNext(account, state);
                return;
            }
            TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
            if (self == null) { joinNext(account, state); return; }
            controller.addUserToChat(chat.id, self, 0, null, null, true,
                    () -> {
                        if (isCurrent(account, state) && operation == state.operation) joinNext(account, state);
                    }, error -> {
                        // No modal error while running automatically (private/inaccessible channels can fail).
                        if (isCurrent(account, state) && operation == state.operation) {
                            long retryDelay = RETRY_INTERVAL_MS;
                            if (error != null && error.text != null && error.text.startsWith("FLOOD_WAIT_")) {
                                try { retryDelay = Math.max(retryDelay, Long.parseLong(error.text.substring(11)) * 1000L); }
                                catch (NumberFormatException ignored) { }
                                state.floodWaitUntilElapsed = SystemClock.elapsedRealtime() + retryDelay;
                            }
                            state.retryAtElapsed = Math.max(state.retryAtElapsed, SystemClock.elapsedRealtime() + retryDelay);
                            joinNext(account, state);
                        }
                        return false;
                    });
        });
    }

    public static ArrayList<String> parse(String text) throws IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Invalid feedback channel list size");
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        for (String raw : text.replace("\uFEFF", "").split("[\r\n]+")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("https://t.me/") || lower.startsWith("http://t.me/")
                    || lower.startsWith("https://telegram.me/") || lower.startsWith("http://telegram.me/")) {
                int start = line.indexOf("//") + 2;
                int slash = line.indexOf('/', start);
                if (slash < 0) throw new IOException("Missing channel username");
                line = line.substring(slash + 1).split("[?#]", 2)[0].trim();
            } else if (lower.startsWith("t.me/")) {
                line = line.substring(5).split("[?#]", 2)[0].trim();
            }
            if (line.startsWith("@")) line = line.substring(1);
            if (!line.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) throw new IOException("Invalid public channel username");
            channels.add(line.toLowerCase(Locale.ROOT));
            if (channels.size() > MAX_CHANNELS) throw new IOException("Too many feedback channels");
        }
        return new ArrayList<>(channels);
    }

    private static String join(ArrayList<String> channels) {
        StringBuilder out = new StringBuilder();
        for (String channel : channels) {
            if (out.length() > 0) out.append('\n');
            out.append('@').append(channel);
        }
        return out.toString();
    }

    private static String fetch() throws IOException {
        URL url = new URL(SOURCE_URL);
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) throw new IOException("Invalid feedback channel redirect");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "text/plain");
                connection.setRequestProperty("Cache-Control", "no-cache");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == 3) throw new IOException("Invalid feedback channel redirect");
                    url = new URL(url, location);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Feedback channel HTTP status " + status);
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("text/plain")
                        && !type.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) throw new IOException("Unexpected feedback channel content type");
                if (connection.getContentLength() > MAX_BYTES) throw new IOException("Feedback channel list too large");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) throw new IOException("Feedback channel list too large");
                        output.write(buffer, 0, count);
                    }
                }
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many feedback channel redirects");
    }
}

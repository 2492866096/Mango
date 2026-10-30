package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class ForcedRecentSearchConfig {

    private static final String CONFIG_URL = "https://1812652903.cdn.123clouddisk.com/1812652903/GY/fanwen.txt";
    private static final String PREF_USERNAME = "forcedRecentSearchUsername";
    private static final String PREF_DISMISSED_USERNAME = "forcedRecentSearchDismissedUsername";
    private static final String PREF_DISMISSED_UNTIL = "forcedRecentSearchDismissedUntil";
    public static final long DISMISS_DURATION_MS = 30L * 60L * 1000L;
    public static final String DEFAULT_USERNAME = "tiaoa";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{5,32}");
    private static final ArrayList<Utilities.Callback<String>> callbacks = new ArrayList<>();
    private static boolean loading;
    private static String currentUsername;

    public static String getUsername() {
        ensureLoaded();
        return TextUtils.isEmpty(currentUsername) ? DEFAULT_USERNAME : currentUsername;
    }

    public static long getDismissedUntil(String username) {
        if (TextUtils.isEmpty(username) || ApplicationLoader.applicationContext == null) {
            return 0L;
        }
        SharedPreferences preferences = getPreferences();
        if (!TextUtils.equals(username, preferences.getString(PREF_DISMISSED_USERNAME, null))) {
            return 0L;
        }
        long dismissedUntil = preferences.getLong(PREF_DISMISSED_UNTIL, 0L);
        if (dismissedUntil <= System.currentTimeMillis()) {
            preferences.edit().remove(PREF_DISMISSED_USERNAME).remove(PREF_DISMISSED_UNTIL).apply();
            return 0L;
        }
        return dismissedUntil;
    }

    public static void dismiss(String username) {
        if (TextUtils.isEmpty(username) || ApplicationLoader.applicationContext == null) {
            return;
        }
        getPreferences().edit()
                .putString(PREF_DISMISSED_USERNAME, username)
                .putLong(PREF_DISMISSED_UNTIL, System.currentTimeMillis() + DISMISS_DURATION_MS)
                .apply();
    }

    public static void fetch(Utilities.Callback<String> callback) {
        ensureLoaded();
        if (callback != null) {
            callbacks.add(callback);
        }
        if (loading) {
            return;
        }
        loading = true;
        Utilities.externalNetworkQueue.postRunnable(() -> {
            String username = null;
            try {
                username = parseUsername(readText(CONFIG_URL));
            } catch (Exception e) {
                FileLog.e(e);
            }
            final String finalUsername = username;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (!TextUtils.isEmpty(finalUsername)) {
                    setUsername(finalUsername);
                }
                String result = getUsername();
                for (int i = 0; i < callbacks.size(); i++) {
                    callbacks.get(i).run(result);
                }
                callbacks.clear();
            });
        });
    }

    public static String parseUsername(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String[] lines = text.replace("\uFEFF", "").split("\\r?\\n");
        for (String line : lines) {
            String username = normalizeUsername(line);
            if (!TextUtils.isEmpty(username)) {
                return username;
            }
        }
        return normalizeUsername(text);
    }

    public static String normalizeUsername(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("#") || candidate.startsWith("//")) {
            return null;
        }
        candidate = candidate.replace("https://", "").replace("http://", "");
        int tmeIndex = candidate.indexOf("t.me/");
        int telegramIndex = candidate.indexOf("telegram.me/");
        if (tmeIndex >= 0) {
            candidate = candidate.substring(tmeIndex + "t.me/".length());
        } else if (telegramIndex >= 0) {
            candidate = candidate.substring(telegramIndex + "telegram.me/".length());
        }
        if (candidate.startsWith("@")) {
            candidate = candidate.substring(1);
        }
        int cut = -1;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c == '/' || c == '?' || c == '&' || c == '#' || Character.isWhitespace(c)) {
                cut = i;
                break;
            }
        }
        if (cut >= 0) {
            candidate = candidate.substring(0, cut);
        }
        return USERNAME_PATTERN.matcher(candidate).matches() ? candidate : null;
    }

    private static void ensureLoaded() {
        if (currentUsername != null || ApplicationLoader.applicationContext == null) {
            return;
        }
        currentUsername = getPreferences().getString(PREF_USERNAME, DEFAULT_USERNAME);
    }

    private static void setUsername(String username) {
        currentUsername = username;
        if (ApplicationLoader.applicationContext != null) {
            getPreferences().edit().putString(PREF_USERNAME, username).apply();
        }
    }

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
    }

    private static String readText(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Bad response code " + responseCode);
        }
        try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
            byte[] buffer = new byte[1024];
            int read;
            StringBuilder builder = new StringBuilder();
            while ((read = inputStream.read(buffer)) >= 0) {
                builder.append(new String(buffer, 0, read, "UTF-8"));
                if (builder.length() > 2048) {
                    break;
                }
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }
}

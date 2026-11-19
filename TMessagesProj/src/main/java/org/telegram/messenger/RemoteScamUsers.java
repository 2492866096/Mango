package org.telegram.messenger;

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

/** Strict username/ID list parser; records match permanent IDs or exact usernames. */
public final class RemoteScamUsers {
    public static final int MAX_BYTES = 256 * 1024;
    public static final class Entries {
        public final java.util.Set<Long> ids = new java.util.HashSet<>();
        /** Telegram channel IDs occupy a separate namespace from user IDs. */
        public final java.util.Set<Long> channelIds = new java.util.HashSet<>();
        public final java.util.Set<String> usernames = new java.util.HashSet<>();
        public int size;
        public boolean contains(long id, String username) {
            return ids.contains(id) || username != null && usernames.contains(username.toLowerCase(Locale.ROOT));
        }
        public boolean containsChannel(long id, String username) {
            return channelIds.contains(id) || username != null && usernames.contains(username.toLowerCase(Locale.ROOT));
        }
    }
    private RemoteScamUsers() {}
    public static boolean validUrl(String source) {
        try { URL url = new URL(source); return "https".equalsIgnoreCase(url.getProtocol()) && !url.getHost().isEmpty() && url.getUserInfo() == null; }
        catch (Exception e) { return false; }
    }
    public static Entries parse(String text) throws IOException {
        if (text == null || text.length() > MAX_BYTES) throw new IOException("Invalid list size");
        Entries entries = new Entries();
        for (String line : text.replace("\uFEFF", "").split("[\r\n]+")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] pair = line.split("/", -1);
            if (pair.length > 2) throw new IOException("Expected username/ID");
            String username = pair[0].trim();
            String id = pair.length == 2 ? pair[1].trim() : "";
            if (pair.length == 1 && (username.matches("[0-9]+") || username.matches("-100[0-9]+")
                    || username.regionMatches(true, 0, "channel:", 0, 8))) { id = username; username = ""; }
            if (username.startsWith("@")) username = username.substring(1);
            if (username.equals("-")) username = "";
            if (!username.isEmpty() && !username.matches("[A-Za-z][A-Za-z0-9_]{0,31}")) throw new IOException("Invalid username");
            if (!id.isEmpty()) {
                boolean channel = id.startsWith("-100") || id.regionMatches(true, 0, "channel:", 0, 8);
                String valueText = id.startsWith("-100") ? id.substring(4) : channel ? id.substring(8).trim() : id;
                try {
                    long value = Long.parseLong(valueText);
                    if (value <= 0 || !valueText.matches("[0-9]+")) throw new NumberFormatException();
                    (channel ? entries.channelIds : entries.ids).add(value);
                } catch (NumberFormatException e) { throw new IOException("Invalid peer ID", e); }
            }
            if (username.isEmpty() && id.isEmpty()) throw new IOException("Empty record");
            if (!username.isEmpty()) entries.usernames.add(username.toLowerCase(Locale.ROOT));
            if (++entries.size > 5000) throw new IOException("Too many peers");
        }
        // An empty file intentionally clears a previously published list.
        return entries;
    }
    public static String fetch(String source) throws IOException {
        URL url = new URL(source);
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) {
                throw new IOException("Invalid keyword source redirect");
            }
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
                    if (location == null || redirect == 3) throw new IOException("Invalid keyword redirect");
                    url = new URL(url, location);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Keyword HTTP status " + status);
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("text/plain")
                        && !type.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
                    throw new IOException("Unexpected keyword content type");
                }
                if (connection.getContentLength() > MAX_BYTES) throw new IOException("Keyword file is too large");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) throw new IOException("Keyword file is too large");
                        output.write(buffer, 0, count);
                    }
                }
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
                parse(text);
                return text;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many keyword redirects");
    }
}

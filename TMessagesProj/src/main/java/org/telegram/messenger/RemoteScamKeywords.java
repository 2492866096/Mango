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

/** Downloads public keyword data only. Chat contents are never part of the request. */
public final class RemoteScamKeywords {
    public static final String SOURCE_URL = "https://1812652903.cdn.123clouddisk.com/1812652903/GY/%E5%8F%8D%E8%AF%88.txt";
    public static final int MAX_BYTES = 256 * 1024;
    public static final int MAX_KEYWORDS = 2000;

    private RemoteScamKeywords() { }

    public static ArrayList<String> parse(String text) throws IOException {
        if (text == null || text.length() > MAX_BYTES) throw new IOException("Invalid keyword size");
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("{") || trimmed.startsWith("[")
                || trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) {
            throw new IOException("Expected a nonempty plain text keyword list");
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String line : text.split("[\r\n]+")) {
            String word = MessageInsightsRules.normalize(line).trim();
            if (word.isEmpty()) continue;
            if (word.length() > 80) throw new IOException("Keyword is too long");
            for (int i = 0; i < word.length(); i++) {
                if (Character.isISOControl(word.charAt(i)) || word.charAt(i) == '\uFEFF') {
                    throw new IOException("Unexpected keyword control character");
                }
            }
            keywords.add(word);
            if (keywords.size() > MAX_KEYWORDS) throw new IOException("Too many keywords");
        }
        if (keywords.isEmpty()) throw new IOException("Empty keyword list");
        return new ArrayList<>(keywords);
    }

    public static ArrayList<String> fetch() throws IOException {
        URL url = new URL(SOURCE_URL);
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
                return parse(text);
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many keyword redirects");
    }
}

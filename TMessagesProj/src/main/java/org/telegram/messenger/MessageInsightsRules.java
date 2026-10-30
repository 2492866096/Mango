package org.telegram.messenger;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.TimeZone;

/** Pure rules shared by local message analysis and its regression tests. */
public final class MessageInsightsRules {
    private MessageInsightsRules() { }

    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    public static ArrayList<String> parseKeywords(String value) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String line : (value == null ? "" : value).split("[\\r\\n]+")) {
            String keyword = normalize(line).trim();
            if (!keyword.isEmpty() && keyword.length() <= 80) unique.add(keyword);
            if (unique.size() == 200) break;
        }
        return new ArrayList<>(unique);
    }

    public static String matchingKeyword(String message, Iterable<String> keywords) {
        String normalized = normalize(message);
        for (String keyword : keywords) {
            if (!keyword.isEmpty() && normalized.contains(normalize(keyword))) return keyword;
        }
        return null;
    }

    public static int dayKey(long seconds, TimeZone timeZone) {
        Calendar c = Calendar.getInstance(timeZone);
        c.setTimeInMillis(seconds * 1000L);
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    public static int inclusiveDays(long fromSeconds, long toSeconds, TimeZone timeZone) {
        if (toSeconds < fromSeconds) return 0;
        Calendar start = Calendar.getInstance(timeZone);
        start.setTimeInMillis(fromSeconds * 1000L);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        int last = dayKey(toSeconds, timeZone);
        int days = 1;
        while (dayKey(start.getTimeInMillis() / 1000L, timeZone) != last) {
            start.add(Calendar.DAY_OF_MONTH, 1);
            days++;
        }
        return days;
    }
}

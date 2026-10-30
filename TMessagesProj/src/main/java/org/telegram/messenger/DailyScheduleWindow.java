package org.telegram.messenger;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Wall-clock calculation kept independent of Android so boundary cases can be tested. */
public final class DailyScheduleWindow {
    private DailyScheduleWindow() { }

    public static String dayKey(long now, TimeZone zone) {
        Calendar date = Calendar.getInstance(zone);
        date.setTimeInMillis(now);
        return String.format(Locale.US, "%04d-%02d-%02d", date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    public static boolean inRandomWindow(long now, int startMinute, int endMinute, TimeZone zone) {
        if (startMinute < 0 || endMinute > 1439 || startMinute > endMinute) return false;
        Calendar local = Calendar.getInstance(zone);
        local.setTimeInMillis(now);
        int minute = local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE);
        return minute >= startMinute && minute <= endMinute;
    }

    /** Wait a random 15–90 seconds after becoming active within the chosen daily window. */
    public static long planRandomTime(long now, int startMinute, int endMinute, double sample, TimeZone zone) {
        if (!inRandomWindow(now, startMinute, endMinute, zone)) return 0;
        Calendar end = Calendar.getInstance(zone);
        end.setTimeInMillis(now);
        end.set(Calendar.HOUR_OF_DAY, endMinute / 60);
        end.set(Calendar.MINUTE, endMinute % 60);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        long delay = 15000 + (long) (Math.max(0, Math.min(1, sample)) * 75000);
        return Math.min(now + delay, end.getTimeInMillis());
    }

    public static boolean isRandomDue(long now, long due, int startMinute, int endMinute, String lastDay, TimeZone zone) {
        return due > 0 && now >= due && dayKey(now, zone).equals(dayKey(due, zone))
                && !dayKey(now, zone).equals(lastDay) && inRandomWindow(now, startMinute, endMinute, zone);
    }

    public static boolean isDue(long previousTime, long now, int hour, int minute,
                                long armedAt, String lastDay, TimeZone zone) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || previousTime <= 0
                || now <= previousTime || now - previousTime > 90000
                || dayKey(now, zone).equals(lastDay)) {
            return false;
        }
        Calendar local = Calendar.getInstance(zone);
        local.setTimeInMillis(now);
        // Match the actual minute traversed by the live timer. Reconstructing a
        // local date would select only the second occurrence during a DST overlap.
        if (local.get(Calendar.HOUR_OF_DAY) != hour || local.get(Calendar.MINUTE) != minute) {
            return false;
        }
        long due = now - local.get(Calendar.SECOND) * 1000L - local.get(Calendar.MILLISECOND);
        return previousTime < due && due <= now && now - due < 60000 && armedAt < due;
    }
}

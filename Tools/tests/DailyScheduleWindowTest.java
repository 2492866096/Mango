import org.telegram.messenger.DailyScheduleWindow;

import java.util.Calendar;
import java.util.TimeZone;

/** Run with javac/java; no Android runtime, network or account is used. */
public final class DailyScheduleWindowTest {
    private static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");
    private static int checks;

    private static long at(TimeZone zone, int year, int month, int day, int hour, int minute, int second) {
        Calendar value = Calendar.getInstance(zone);
        value.clear();
        value.set(year, month - 1, day, hour, minute, second);
        return value.getTimeInMillis();
    }

    private static void expect(String name, boolean expected, boolean actual) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        checks++;
    }

    public static void main(String[] args) {
        long due = at(SHANGHAI, 2026, 9, 17, 9, 0, 0);
        expect("active timer crosses scheduled minute", true, DailyScheduleWindow.isDue(due - 1000, due + 4000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("same day deduplication", false, DailyScheduleWindow.isDue(due - 1000, due + 4000, 9, 0, due - 3600000, "2026-09-17", SHANGHAI));
        expect("manual send yesterday permits today", true, DailyScheduleWindow.isDue(due - 1000, due + 4000, 9, 0, due - 3600000, "2026-09-16", SHANGHAI));
        expect("process restart cannot catch up", false, DailyScheduleWindow.isDue(0, due + 4000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("offline resume after due cannot catch up", false, DailyScheduleWindow.isDue(due + 1000, due + 6000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("second tick cannot repeat", false, DailyScheduleWindow.isDue(due + 4000, due + 9000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("new task within due minute cannot send immediately", false, DailyScheduleWindow.isDue(due - 1000, due + 4000, 9, 0, due + 1000, "", SHANGHAI));
        expect("long suspension cannot catch up", false, DailyScheduleWindow.isDue(due - 120000, due + 4000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("backward clock jump", false, DailyScheduleWindow.isDue(due + 1000, due - 1000, 9, 0, due - 3600000, "", SHANGHAI));
        expect("invalid time", false, DailyScheduleWindow.isDue(due - 1000, due + 4000, 24, 0, due - 3600000, "", SHANGHAI));
        long midnight = at(SHANGHAI, 2026, 9, 18, 0, 0, 0);
        expect("midnight uses new local date", true, DailyScheduleWindow.isDue(midnight - 2000, midnight + 3000, 0, 0, due - 3600000, "2026-09-17", SHANGHAI));
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        long afterSpringJump = at(ny, 2026, 3, 8, 3, 30, 0);
        expect("nonexistent DST time is skipped", false, DailyScheduleWindow.isDue(afterSpringJump - 2000, afterSpringJump + 3000, 2, 30, afterSpringJump - 86400000, "", ny));
        long fall = at(ny, 2026, 11, 1, 1, 30, 0);
        long firstFall = fall - 3600000;
        expect("first occurrence of DST minute can trigger", true, DailyScheduleWindow.isDue(firstFall - 2000, firstFall + 3000, 1, 30, fall - 86400000, "", ny));
        expect("second occurrence can trigger when first was missed", true, DailyScheduleWindow.isDue(fall - 2000, fall + 3000, 1, 30, fall - 86400000, "", ny));
        expect("repeated DST minute is deduplicated", false, DailyScheduleWindow.isDue(fall - 2000, fall + 3000, 1, 30, fall - 86400000, "2026-11-01", ny));
        expect("random full day accepts noon", true, DailyScheduleWindow.inRandomWindow(due, 0, 1439, SHANGHAI));
        expect("random before window waits", false, DailyScheduleWindow.inRandomWindow(due, 600, 720, SHANGHAI));
        expect("random invalid reversed window", false, DailyScheduleWindow.inRandomWindow(due, 600, 400, SHANGHAI));
        expect("random minimum wait", true, DailyScheduleWindow.planRandomTime(due, 0, 1439, 0, SHANGHAI) == due + 15000);
        expect("random maximum wait", true, DailyScheduleWindow.planRandomTime(due, 0, 1439, 1, SHANGHAI) == due + 90000);
        expect("random outside window unscheduled", true, DailyScheduleWindow.planRandomTime(due, 600, 720, .5, SHANGHAI) == 0);
        expect("random same-day duplicate", false, DailyScheduleWindow.isRandomDue(due + 30000, due + 15000, 0, 1439, "2026-09-17", SHANGHAI));
        expect("random due while online", true, DailyScheduleWindow.isRandomDue(due + 30000, due + 15000, 0, 1439, "", SHANGHAI));
        expect("random yesterday not caught up", false, DailyScheduleWindow.isRandomDue(midnight + 30000, due + 15000, 0, 1439, "", SHANGHAI));
        expect("random cannot run before due", false, DailyScheduleWindow.isRandomDue(due, due + 15000, 0, 1439, "", SHANGHAI));
        expect("random missed window cannot run", false, DailyScheduleWindow.isRandomDue(due + 3600000, due + 15000, 540, 540, "", SHANGHAI));
        long lastSecond = midnight - 1000;
        long finalDue = DailyScheduleWindow.planRandomTime(lastSecond, 0, 1439, 1, SHANGHAI);
        expect("random near midnight stays today", true, finalDue >= lastSecond && finalDue < midnight);
        System.out.println("DailyScheduleWindowTest: " + checks + " checks passed");
    }
}

import org.telegram.messenger.MessageInsightsRules;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public final class MessageInsightsRulesTest {
    private static int checks;
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    private static long at(TimeZone zone, int year, int month, int day, int hour) {
        Calendar c = Calendar.getInstance(zone); c.clear(); c.set(year, month - 1, day, hour, 0, 0); return c.getTimeInMillis() / 1000;
    }
    public static void main(String[] args) {
        check(MessageInsightsRules.parseKeywords("TEST\n test \nＴＥＳＴ\n\n助记词").equals(Arrays.asList("test", "助记词")), "normalized deduplication");
        check("seed phrase".equals(MessageInsightsRules.matchingKeyword("Please enter your ＳＥＥＤ ＰＨＲＡＳＥ now", Arrays.asList("seed phrase"))), "unicode width and case matching");
        check(MessageInsightsRules.matchingKeyword("regular conversation", Arrays.asList("助记词", "seed phrase")) == null, "benign negative control");
        check(MessageInsightsRules.matchingKeyword(null, Arrays.asList("")) == null, "empty keyword cannot match every message");
        StringBuilder many = new StringBuilder(); for (int i = 0; i < 240; i++) many.append("word").append(i).append('\n');
        check(MessageInsightsRules.parseKeywords(many.toString()).size() == 200, "keyword cap");
        char[] tooLong = new char[81]; Arrays.fill(tooLong, 'x');
        check(MessageInsightsRules.parseKeywords(new String(tooLong)).isEmpty(), "oversized keyword rejected");
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        check(MessageInsightsRules.inclusiveDays(at(ny, 2026, 3, 7, 0), at(ny, 2026, 3, 9, 23), ny) == 3, "spring DST calendar days");
        check(MessageInsightsRules.inclusiveDays(at(ny, 2026, 10, 31, 0), at(ny, 2026, 11, 2, 23), ny) == 3, "fall DST calendar days");
        check(MessageInsightsRules.inclusiveDays(100, 99, ny) == 0, "reversed range");
        TimeZone shanghai = TimeZone.getTimeZone("Asia/Shanghai");
        check(MessageInsightsRules.dayKey(at(shanghai, 2026, 10, 2, 0), shanghai) != MessageInsightsRules.dayKey(at(shanghai, 2026, 10, 2, 0) - 1, shanghai), "local midnight boundary");
        check(MessageInsightsRules.inclusiveDays(at(shanghai, 2024, 2, 28, 0), at(shanghai, 2024, 3, 1, 23), shanghai) == 3, "leap day");
        System.out.println("MessageInsightsRulesTest: " + checks + " checks passed");
    }
}

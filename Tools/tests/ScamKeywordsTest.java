package org.telegram.messenger;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/** Exercises production networking, persistence, account isolation, and both schedulers offline. */
public final class ScamKeywordsTest {
    private static int checks, requests;
    private static byte[] body;
    private static int status = 200;
    private static String contentType = "text/plain;charset=utf-8", location;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static void response(String text) {
        body = text.getBytes(StandardCharsets.UTF_8);
        status = 200; contentType = "text/plain;charset=utf-8"; location = null;
    }
    private static void set(String name, Object value) throws Exception {
        Field f = ScamProtectionManager.class.getDeclaredField(name);
        f.setAccessible(true); f.set(null, value);
    }
    private static void reset() throws Exception {
        ApplicationLoader.applicationContext = new Context();
        for (int i = 0; i < 3; i++) UserConfig.getInstance(i).owner = i == 0 ? 42 : 0;
        set("remoteLoaded", false); set("remoteKeywords", new ArrayList<String>());
        set("refreshing", false); set("refreshFailed", false); set("lastUpdated", 0L);
        set("lastAttemptElapsed", -1L); set("automaticRefreshStarted", false);
        Field listeners = ScamProtectionManager.class.getDeclaredField("listeners");
        listeners.setAccessible(true); ((ArrayList<?>) listeners.get(null)).clear();
        DispatchQueue.pending.clear(); AndroidUtilities.delayed.clear();
        SystemClock.now = 1000000; requests = 0;
        response("手机口\n返现\n稳赚\n不用押金");
    }
    private static void expire() throws Exception {
        set("lastUpdated", System.currentTimeMillis() - ScamProtectionManager.REFRESH_INTERVAL_MS - 1000);
        SystemClock.now += ScamProtectionManager.REFRESH_INTERVAL_MS;
    }
    private interface IOAction { void run() throws Exception; }
    private static void rejects(IOAction action, String description) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IOException expected) { rejected = true; }
        check(rejected, description);
    }
    private static SharedPreferences remotePrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("remote_scam_keywords", Context.MODE_PRIVATE);
    }
    private static void timer() {
        check(AndroidUtilities.delayed.size() == 1, "exactly one pending periodic timer");
        AndroidUtilities.delayed.remove(0).runnable.run();
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    @Override public int getResponseCode() { requests++; return status; }
                    @Override public String getContentType() { return contentType; }
                    @Override public int getContentLength() { return body.length; }
                    @Override public String getHeaderField(String key) { return "Location".equals(key) ? location : null; }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
                    @Override public void disconnect() { }
                    @Override public boolean usingProxy() { return false; }
                    @Override public void connect() { }
                };
            }
        } : null);
        reset();
        check(ScamProtectionManager.REFRESH_INTERVAL_MS == 7200000L, "two-hour interval");
        check(RemoteScamKeywords.SOURCE_URL.equals("https://1812652903.cdn.123clouddisk.com/1812652903/GY/%E5%8F%8D%E8%AF%88.txt"), "exact configured source without fragment");
        check(RemoteScamKeywords.parse("\uFEFF 手机口\r\n返现\n ＡＢＣ \rabc\n\n").equals(Arrays.asList("手机口", "返现", "abc")), "BOM CRLF Chinese NFKC case dedupe");
        check(RemoteScamKeywords.parse("最后一行").equals(Arrays.asList("最后一行")), "no trailing newline required");
        for (String bad : new String[] {"", " \r\n", "<html>failure</html>", "{\"error\":true}", "[\"error\"]", "bad\u0001word", "bad\uFEFFword"}) {
            rejects(() -> RemoteScamKeywords.parse(bad), "invalid response rejected");
        }
        rejects(() -> RemoteScamKeywords.parse(null), "null rejected");
        rejects(() -> RemoteScamKeywords.parse(new String(new char[81]).replace('\0', 'a')), "long keyword rejected");
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 2000; i++) large.append("word").append(i).append('\n');
        check(RemoteScamKeywords.parse(large.toString()).size() == 2000, "2000 remote words supported");
        large.append("overflow");
        rejects(() -> RemoteScamKeywords.parse(large.toString()), "remote word limit enforced");
        check(RemoteScamKeywords.fetch().size() == 4, "HTTP 200 UTF-8 download parsed");
        body = new byte[] {(byte)0xC3, (byte)0x28};
        rejects(RemoteScamKeywords::fetch, "invalid UTF-8 rejected");
        body = new byte[RemoteScamKeywords.MAX_BYTES + 1];
        rejects(RemoteScamKeywords::fetch, "oversize download rejected");
        response("safe"); status = 503;
        rejects(RemoteScamKeywords::fetch, "HTTP error rejected");
        status = 200; contentType = "text/html";
        rejects(RemoteScamKeywords::fetch, "HTML content type rejected");
        response("safe"); status = 302; location = "http://example.test/words";
        rejects(RemoteScamKeywords::fetch, "HTTP redirect downgrade rejected");
        location = "https://user:pass@example.test/words";
        rejects(RemoteScamKeywords::fetch, "redirect credentials rejected");
        location = "https://example.test/loop"; int before = requests;
        rejects(RemoteScamKeywords::fetch, "redirect loop bounded");
        check(requests - before == 4, "maximum of three redirect hops");

        reset();
        check(ScamProtectionManager.getKeywords(0).contains("seed phrase"), "bundled fallback before download");
        ScamProtectionManager.setKeywords(0, "我的补充\n返现");
        ScamProtectionManager.refresh(false);
        ScamProtectionManager.refresh(true);
        check(DispatchQueue.pending.size() == 1 && ScamProtectionManager.isRefreshing(), "concurrent refreshes coalesced");
        DispatchQueue.drain();
        check(ScamProtectionManager.getRemoteKeywordCount() == 4, "remote list replaces bundled list");
        check(ScamProtectionManager.getKeywords(0).size() == 5 && !ScamProtectionManager.getKeywords(0).contains("seed phrase"), "custom merge deduped with no bundled residue");
        check(remotePrefs().getLong("updated_at", 0) > 0 && RemoteScamKeywords.SOURCE_URL.equals(remotePrefs().getString("source", "")), "cache source and timestamp persisted");
        check(!ScamProtectionManager.isRefreshing() && !ScamProtectionManager.hasRefreshFailed(), "success state published");
        ScamProtectionManager.refresh(false);
        check(DispatchQueue.pending.isEmpty(), "fresh cache suppresses automatic HTTP");
        set("lastUpdated", System.currentTimeMillis() - ScamProtectionManager.REFRESH_INTERVAL_MS + 10000);
        SystemClock.now += ScamProtectionManager.REFRESH_INTERVAL_MS;
        ScamProtectionManager.refresh(false);
        check(DispatchQueue.pending.isEmpty(), "cache just before two-hour boundary retained");
        expire(); ScamProtectionManager.refresh(false);
        check(DispatchQueue.pending.size() == 1, "expired cache automatically refreshed");
        DispatchQueue.drain();
        response("新词"); ScamProtectionManager.refresh(true); DispatchQueue.drain();
        check(ScamProtectionManager.getKeywords(0).equals(Arrays.asList("新词", "我的补充", "返现")), "remote replacement preserves custom additions only");
        set("remoteLoaded", false); set("remoteKeywords", new ArrayList<String>());
        check(ScamProtectionManager.getRemoteKeywordCount() == 1, "valid cache restored after reload");
        long successfulTime = ScamProtectionManager.getLastUpdated();
        response("<html>bad gateway</html>"); ScamProtectionManager.refresh(true); DispatchQueue.drain();
        check(ScamProtectionManager.hasRefreshFailed() && ScamProtectionManager.getRemoteKeywordCount() == 1, "invalid download retains effective cache");
        check(successfulTime == ScamProtectionManager.getLastUpdated(), "failed download does not advance timestamp");
        response("磁盘失败"); ApplicationLoader.applicationContext.failCommit = true;
        ScamProtectionManager.refresh(true); DispatchQueue.drain();
        check(ScamProtectionManager.getKeywords(0).contains("新词") && !ScamProtectionManager.getKeywords(0).contains("磁盘失败"), "failed commit never publishes new keywords");
        ApplicationLoader.applicationContext.failCommit = false;
        response("重新更新"); ScamProtectionManager.refresh(true); DispatchQueue.drain();
        check(!ScamProtectionManager.hasRefreshFailed() && ScamProtectionManager.getKeywords(0).contains("重新更新"), "manual retry bypasses throttle and recovers");
        UserConfig.getInstance(1).owner = 99;
        check(ScamProtectionManager.getCustomKeywords(1).isEmpty(), "custom words isolated by account");
        UserConfig.getInstance(0).owner = 100;
        check(ScamProtectionManager.getCustomKeywords(0).isEmpty(), "reused account slot isolated by owner");
        UserConfig.getInstance(0).owner = 42;
        ScamProtectionManager.setKeywords(0, "");
        check(ScamProtectionManager.getKeywords(0).equals(Arrays.asList("重新更新")), "clearing additions retains remote");
        ScamProtectionManager.setKeywords(0, ScamProtectionManager.DEFAULT_KEYWORDS);
        check(ScamProtectionManager.getCustomKeywords(0).isEmpty(), "legacy default list is not migrated as custom");
        remotePrefs().edit().putString("source", "https://old.test/list").commit(); set("remoteLoaded", false); set("remoteKeywords", new ArrayList<String>());
        check(ScamProtectionManager.getRemoteKeywordCount() == 0, "cache from another source ignored");

        reset();
        ScamProtectionManager.startAutomaticRefresh();
        check(DispatchQueue.pending.size() == 1 && AndroidUtilities.delayed.size() == 1, "startup refresh and persistent timer without messages");
        DispatchQueue.drain();
        check(AndroidUtilities.delayed.get(0).delay > 7199000 && AndroidUtilities.delayed.get(0).delay <= 7200000, "next timer scheduled two hours after success");
        ScamProtectionManager.startAutomaticRefresh();
        check(AndroidUtilities.delayed.size() == 1 && DispatchQueue.pending.isEmpty(), "foreground repeat does not duplicate timers or fresh requests");
        expire(); timer();
        check(DispatchQueue.pending.size() == 1, "timer initiates refresh without incoming messages");
        response("<html>error</html>"); DispatchQueue.drain();
        check(AndroidUtilities.delayed.get(0).delay == 300000, "failure schedules five-minute retry");
        timer();
        check(DispatchQueue.pending.isEmpty(), "failure retry cannot run early");
        SystemClock.now += 300000; timer();
        check(DispatchQueue.pending.size() == 1, "retry runs at backoff boundary");
        response("恢复"); DispatchQueue.drain();
        ScamProtectionManager.setEnabled(0, false); expire(); timer();
        check(DispatchQueue.pending.isEmpty(), "disabled accounts do not auto-download");
        ScamProtectionManager.setEnabled(0, true);
        check(DispatchQueue.pending.size() == 1, "enabling resumes automatic refresh");
        DispatchQueue.drain();

        reset();
        ScamKeywordsUpdateService.schedule(); ScamKeywordsUpdateService.schedule();
        check(ApplicationLoader.applicationContext.scheduler.scheduleCalls == 1, "system job registration is idempotent");
        JobInfo job = ApplicationLoader.applicationContext.scheduler.getAllPendingJobs().get(0);
        check(job.getIntervalMillis() == 7200000 && job.isPersisted() && job.getNetworkType() == JobInfo.NETWORK_TYPE_ANY, "system job persists and requires network every two hours");
        check(job.getInitialBackoffMillis() == 300000, "background backoff configured");
        ScamKeywordsUpdateService service = new ScamKeywordsUpdateService(); JobParameters params = new JobParameters();
        check(service.onStartJob(params), "job waits for asynchronous download");
        DispatchQueue.drain();
        check(service.finishCalls == 1 && service.finished == params && !service.retry, "job finishes after successful cache commit");
        check(!service.onStartJob(new JobParameters()), "fresh-cache job finishes synchronously");
        expire(); response("<html>error</html>");
        check(service.onStartJob(new JobParameters()), "expired job starts network"); DispatchQueue.drain();
        check(service.retry, "failed background download requests reschedule");
        expire(); response("恢复"); service.onStartJob(new JobParameters()); int finishes = service.finishCalls;
        check(service.onStopJob(new JobParameters()), "stopped job requests retry"); DispatchQueue.drain();
        check(service.finishCalls == finishes, "stopped service listener removed");
        reset(); UserConfig.getInstance(0).owner = 0;
        check(!new ScamKeywordsUpdateService().onStartJob(new JobParameters()) && DispatchQueue.pending.isEmpty(), "no logged-in account means no background download");
        System.out.println("ScamKeywordsTest: " + checks + " offline checks passed");
    }
}

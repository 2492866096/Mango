package org.telegram.messenger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class BuiltinProxyRegression {
    private static final String SUBSCRIPTION =
            "Test - https://t.me/proxy?server=127.0.0.2&port=443&secret=testsecret\n"
            + "https://t.me/proxy?server=127.0.0.3&port=8443&secret=second";

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void deleteAll() {
        SharedConfig.proxyList.clear();
        SharedConfig.currentProxy = null;
        MessagesController.prefs.edit().putBoolean("proxy_enabled", false).putString("proxy_ip", "").apply();
    }

    private static final class SubscriptionServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final String url = "http://127.0.0.1:" + socket.getLocalPort() + "/subscription";
        final Thread worker;

        SubscriptionServer() throws Exception {
            worker = new Thread(() -> {
                try (Socket client = socket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    byte[] payload = SUBSCRIPTION.getBytes(StandardCharsets.UTF_8);
                    OutputStream output = client.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: " + payload.length
                            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    output.write(payload);
                    output.flush();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            worker.start();
        }

        @Override
        public void close() throws Exception {
            worker.join(2000);
            socket.close();
            check(!worker.isAlive(), "subscription server completed");
        }
    }

    public static void main(String[] args) throws Exception {
        String source;
        try (SubscriptionServer server = new SubscriptionServer()) {
            source = server.url;
            MessagesController.prefs.edit().putString("builtin_proxy_remote_source_url", source).apply();
            ArrayList<Boolean> first = new ArrayList<>();
            BuiltinProxyController.syncNow(true, true, first::add);
            Utilities.globalQueue.drain();
            check(first.equals(Arrays.asList(true)), "initial success callback");
            check(SharedConfig.proxyList.size() == 2, "import subscription");
            check(SharedConfig.currentProxy.address.equals("127.0.0.2"), "activate first subscription proxy");
        }

        deleteAll();
        ArrayList<Boolean> recovered = new ArrayList<>();
        BuiltinProxyController.syncNow(true, true, recovered::add);
        check(SharedConfig.proxyList.size() == 2, "restore before network completes");
        Utilities.globalQueue.drain();
        check(recovered.equals(Arrays.asList(true)), "offline restoration callback");
        check(BuiltinProxyController.wasLastSyncRestoredFromCache(), "offline restoration distinguishable");
        check(MessagesController.prefs.getBoolean("proxy_enabled", false), "restored proxy enabled");

        deleteAll();
        ArrayList<Boolean> background = new ArrayList<>(), manual = new ArrayList<>();
        BuiltinProxyController.syncNow(true, background::add);
        BuiltinProxyController.syncNow(true, true, manual::add);
        check(Utilities.globalQueue.tasks.size() == 1, "only one concurrent fetch");
        Utilities.globalQueue.drain();
        check(background.equals(Arrays.asList(true)) && manual.equals(Arrays.asList(true)), "join during fetch");

        // Keep the second managed proxy during the automatic refresh. Change local state
        // in its notification so application waits; the manual refresh then chooses the
        // first proxy. The poll must check the replacement result, not the old signature.
        try (SubscriptionServer server = new SubscriptionServer()) {
            MessagesController.prefs.edit().putString("builtin_proxy_remote_source_url", server.url)
                    .putString("proxy_ip", "127.0.0.3").putInt("proxy_port", 8443)
                    .putString("proxy_secret", "second").putBoolean("proxy_enabled", true).apply();
            NotificationCenter.nextChange = () -> MessagesController.prefs.edit().putString("proxy_ip", "").apply();
            ArrayList<Boolean> waiting = new ArrayList<>(), lateManual = new ArrayList<>();
            BuiltinProxyController.syncNow(true, waiting::add);
            Utilities.globalQueue.drain();
            check(BuiltinProxyController.isSyncInProgress() && waiting.isEmpty(), "waiting for application");
            BuiltinProxyController.syncNow(true, true, lateManual::add);
            check(SharedConfig.currentProxy.address.equals("127.0.0.2"), "late manual activation applied");
            check(Utilities.globalQueue.tasks.isEmpty(), "late manual reuses fetched subscription");
            ArrayList<Runnable> polls = new ArrayList<>(AndroidUtilities.polls);
            AndroidUtilities.polls.clear();
            for (Runnable poll : polls) poll.run();
            check(waiting.equals(Arrays.asList(true)) && lateManual.equals(Arrays.asList(true)), "latest application result checked");
        }

        deleteAll();
        MessagesController.prefs.edit().putString("builtin_proxy_remote_source_url", source + "/different").apply();
        ArrayList<Boolean> changed = new ArrayList<>();
        BuiltinProxyController.syncNow(true, true, changed::add);
        Utilities.globalQueue.drain();
        check(changed.equals(Arrays.asList(false)), "cache not reused across sources");
        check(SharedConfig.proxyList.isEmpty(), "unrelated source not restored");
        check(ExtensionMediaSettings.isAutoPlayNextEnabled(), "autoplay defaults enabled");
        ExtensionMediaSettings.setAutoPlayNextEnabled(false);
        check(!ExtensionMediaSettings.isAutoPlayNextEnabled(), "autoplay preference saved");
        System.out.println("PASS: subscription import, offline recovery, joining during fetch and application, callbacks, source isolation, preferences");
    }
}

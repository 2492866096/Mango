package org.telegram.messenger;

public class NotificationCenter {
    public static int proxySettingsChanged;
    public static Runnable nextChange;
    public static NotificationCenter getGlobalInstance() { return new NotificationCenter(); }
    public void postNotificationName(int id) {
        Runnable callback = nextChange;
        nextChange = null;
        if (callback != null) callback.run();
    }
}

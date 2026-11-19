package org.telegram.messenger;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import org.telegram.ui.LaunchActivity;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

// Apps targeting Android 17 need ACCESS_LOCAL_NETWORK for any LAN traffic,
// which proxies on the local network and Chromecast streaming depend on.
public class LocalNetworkAccess {

    private static final int REQUEST_CODE = 160;

    private static boolean pending;
    private static boolean requested;

    public static boolean isGranted() {
        return Build.VERSION.SDK_INT < 37
                || ApplicationLoader.applicationContext.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED;
    }

    public static void checkHost(String host) {
        if (isGranted() || host == null || host.isEmpty()) {
            return;
        }
        // Resolving may block, keep it off the shared queues.
        new Thread(() -> {
            if (isLocalHost(host)) {
                AndroidUtilities.runOnUIThread(LocalNetworkAccess::request);
            }
        }, "LocalNetworkCheck").start();
    }

    public static void requestFromAnyThread() {
        AndroidUtilities.runOnUIThread(LocalNetworkAccess::request);
    }

    public static void onActivityResumed() {
        if (pending) {
            AndroidUtilities.runOnUIThread(LocalNetworkAccess::request);
        }
    }

    private static void request() {
        if (isGranted() || requested) {
            pending = false;
            return;
        }
        LaunchActivity activity = LaunchActivity.instance;
        if (activity == null || activity.isFinishing() || ApplicationLoader.mainInterfacePaused) {
            pending = true;
            return;
        }
        pending = false;
        requested = true;
        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQUEST_CODE);
    }

    private static boolean isLocalHost(String host) {
        if (host.endsWith(".local")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isLocalAddress(address)) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    // Ranges from the Android local network permission guide.
    private static boolean isLocalAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return false;
        }
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            boolean cgnat = (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 0x40;
            boolean broadcast = b[0] == -1 && b[1] == -1 && b[2] == -1 && b[3] == -1;
            return cgnat || broadcast;
        }
        return address instanceof Inet6Address && (b[0] & 0xfe) == 0xfc;
    }
}

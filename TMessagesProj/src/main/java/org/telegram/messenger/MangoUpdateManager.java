package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.telegram.ui.Components.AlertsCreator;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MangoUpdateManager {

    private static final String VERSION_URL = "https://1812652903.v.123pan.cn/1812652903/GY/%E7%89%88%E6%9C%AC%E5%8F%B7.txt";
    private static final String APK_URL = "https://1812652903.v.123pan.cn/1812652903/GY/%E8%8A%92%E6%9E%9C%E5%AE%A2%E6%88%B7%E7%AB%AF.apk";

    public interface Listener {
        void onStateChanged();
    }

    public interface CheckCallback {
        void onResult(boolean hasUpdate, String remoteVersion, String error);
    }

    private static final ArrayList<Listener> listeners = new ArrayList<>();
    private static boolean checking;
    private static boolean downloading;
    private static boolean downloaded;
    private static float progress;
    private static String remoteVersion;
    private static String error;
    private static File downloadedFile;

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static boolean isChecking() {
        return checking;
    }

    public static boolean isDownloading() {
        return downloading;
    }

    public static boolean isDownloaded() {
        return downloaded && getDownloadedFile().exists();
    }

    public static float getProgress() {
        return progress;
    }

    public static String getRemoteVersion() {
        return remoteVersion;
    }

    public static String getError() {
        return error;
    }

    public static File getDownloadedFile() {
        if (downloadedFile == null) {
            File baseDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (baseDir == null) {
                baseDir = ApplicationLoader.applicationContext.getCacheDir();
            }
            File dir = new File(baseDir, "updates");
            downloadedFile = new File(dir, "mango_update.apk");
        }
        return downloadedFile;
    }

    public static void checkForUpdates(CheckCallback callback) {
        if (checking) {
            return;
        }
        checking = true;
        error = null;
        notifyListeners();
        Utilities.externalNetworkQueue.postRunnable(() -> {
            String version = null;
            String checkError = null;
            try {
                version = readText(VERSION_URL);
                if (version != null) {
                    version = version.trim();
                }
                if (TextUtils.isEmpty(version)) {
                    checkError = LocaleController.getString(R.string.MangoUpdateCheckFailed);
                }
            } catch (Exception e) {
                FileLog.e(e);
                checkError = LocaleController.getString(R.string.MangoUpdateCheckFailed);
            }
            final String finalVersion = version;
            final String finalError = checkError;
            final boolean hasUpdate = TextUtils.isEmpty(finalError) && isRemoteVersionNewer(finalVersion);
            AndroidUtilities.runOnUIThread(() -> {
                remoteVersion = finalVersion;
                error = finalError;
                checking = false;
                notifyListeners();
                if (callback != null) {
                    callback.onResult(hasUpdate, finalVersion, finalError);
                }
            });
        });
    }

    public static void downloadUpdate(Activity activity) {
        if (downloading) {
            return;
        }
        if (isDownloaded()) {
            installDownloadedUpdate(activity);
            return;
        }
        downloading = true;
        downloaded = false;
        progress = 0;
        error = null;
        notifyListeners();
        Utilities.externalNetworkQueue.postRunnable(() -> {
            File file = getDownloadedFile();
            try {
                File dir = file.getParentFile();
                if (dir != null && (!dir.exists() && !dir.mkdirs())) {
                    throw new IllegalStateException("Unable to create update directory");
                }
                if (file.exists() && !file.delete()) {
                    throw new IllegalStateException("Unable to replace old update file");
                }
                downloadToFile(APK_URL, file);
                AndroidUtilities.runOnUIThread(() -> {
                    downloading = false;
                    downloaded = true;
                    progress = 1f;
                    notifyListeners();
                    installDownloadedUpdate(activity);
                });
            } catch (Exception e) {
                FileLog.e(e);
                if (file.exists()) {
                    file.delete();
                }
                AndroidUtilities.runOnUIThread(() -> {
                    downloading = false;
                    downloaded = false;
                    error = LocaleController.getString(R.string.MangoUpdateDownloadFailed);
                    notifyListeners();
                });
            }
        });
    }

    public static void installDownloadedUpdate(Activity activity) {
        if (activity == null) {
            return;
        }
        File file = getDownloadedFile();
        if (!file.exists()) {
            error = LocaleController.getString(R.string.MangoUpdateDownloadFailed);
            notifyListeners();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                AlertsCreator.createApkRestrictedDialog(activity, null).show();
            } catch (Exception e) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
                } catch (Exception ignore) {
                    FileLog.e(e);
                }
            }
            return;
        }
        try {
            Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file)
                    : Uri.fromFile(file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            error = LocaleController.getString(R.string.MangoUpdateInstallFailed);
            notifyListeners();
        }
    }

    public static boolean isRemoteVersionNewer(String version) {
        if (TextUtils.isEmpty(version)) {
            return false;
        }
        try {
            return !SharedConfig.versionBiggerOrEqual(BuildVars.BUILD_VERSION_STRING, version);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static String readText(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Bad response code " + responseCode);
        }
        try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
            byte[] buffer = new byte[1024];
            int read;
            StringBuilder builder = new StringBuilder();
            while ((read = inputStream.read(buffer)) >= 0) {
                builder.append(new String(buffer, 0, read, "UTF-8"));
                if (builder.length() > 64) {
                    break;
                }
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadToFile(String urlString, File file) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new IllegalStateException("Bad response code " + responseCode);
        }
        int length = connection.getContentLength();
        try (BufferedInputStream inputStream = new BufferedInputStream(connection.getInputStream());
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024 * 64];
            int read;
            long total = 0;
            long lastProgressTime = 0;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
                total += read;
                long now = System.currentTimeMillis();
                if (length > 0 && (now - lastProgressTime > 250 || total == length)) {
                    progress = Math.min(1f, total / (float) length);
                    lastProgressTime = now;
                    AndroidUtilities.runOnUIThread(MangoUpdateManager::notifyListeners);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void notifyListeners() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onStateChanged();
        }
    }
}

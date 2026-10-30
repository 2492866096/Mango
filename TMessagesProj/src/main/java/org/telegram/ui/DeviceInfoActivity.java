package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DeviceToolsUnlockGate;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

public class DeviceInfoActivity extends BaseFragment {

    private static final int ITEM_ID_COPY_ALL = 1;
    private static final int ITEM_ID_VERSION = 2;
    private final DeviceToolsUnlockGate unlockGate = new DeviceToolsUnlockGate();

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.DeviceInfoTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ITEM_ID_COPY_ALL, R.drawable.msg_copy, getString(R.string.DeviceInfoCopyAll)).accent());

        items.add(UItem.asHeader(getString(R.string.DeviceInfoApp)));
        items.add(UItem.asButton(ITEM_ID_VERSION, getString(R.string.DeviceInfoAppVersion), getAppVersion()));
        if (isRootTerminalUnlocked()) {
            items.add(UItem.asButton(3, R.drawable.msg_settings, getString(R.string.DeviceInfoRootTerminal), getString(R.string.DeviceInfoRootTerminalUnlocked)).accent());
        }
        addInfo(items, getString(R.string.DeviceInfoPackage), ApplicationLoader.applicationContext.getPackageName());
        addInfo(items, getString(R.string.DeviceInfoBuild), BuildVars.BUILD_VERSION_STRING);
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.DeviceInfoDevice)));
        addInfo(items, getString(R.string.DeviceInfoManufacturer), safe(Build.MANUFACTURER));
        addInfo(items, getString(R.string.DeviceInfoModel), safe(Build.MODEL));
        addInfo(items, getString(R.string.DeviceInfoDeviceName), safe(Build.DEVICE));
        addInfo(items, getString(R.string.DeviceInfoProduct), safe(Build.PRODUCT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addInfo(items, getString(R.string.DeviceInfoSoc), safe(Build.SOC_MODEL));
        }
        addInfo(items, getString(R.string.DeviceInfoAbi), TextUtils.join(", ", Build.SUPPORTED_ABIS));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.DeviceInfoSystem)));
        addInfo(items, getString(R.string.DeviceInfoAndroid), Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT);
        addInfo(items, getString(R.string.DeviceInfoLocale), Locale.getDefault().toString());
        addInfo(items, getString(R.string.DeviceInfoTimezone), TimeZone.getDefault().getID());
        addInfo(items, getString(R.string.DeviceInfoGles), getGlesVersion());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.DeviceInfoDisplay)));
        addInfo(items, getString(R.string.DeviceInfoScreen), AndroidUtilities.displaySize.x + " x " + AndroidUtilities.displaySize.y);
        addInfo(items, getString(R.string.DeviceInfoDensity), String.format(Locale.US, "%.2f / %ddpi", AndroidUtilities.density, ApplicationLoader.applicationContext.getResources().getDisplayMetrics().densityDpi));
        addInfo(items, getString(R.string.DeviceInfoRefreshRate), String.format(Locale.US, "%.1f Hz", AndroidUtilities.screenRefreshRate));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.DeviceInfoPerformance)));
        addInfo(items, getString(R.string.DeviceInfoPerformanceClass), SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass()));
        addInfo(items, getString(R.string.DeviceInfoMeasuredClass), SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass()));
        addInfo(items, getString(R.string.DeviceInfoCpu), getCpuInfo());
        addInfo(items, getString(R.string.DeviceInfoMemory), getMemoryInfo());
        addInfo(items, getString(R.string.DeviceInfoStorage), getStorageInfo());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.DeviceInfoCodecs)));
        addInfo(items, "AVC/H.264", getCodecInfo("video/avc"));
        addInfo(items, "HEVC/H.265", getCodecInfo("video/hevc"));
        addInfo(items, "VP8", getCodecInfo("video/x-vnd.on2.vp8"));
        addInfo(items, "VP9", getCodecInfo("video/x-vnd.on2.vp9"));
        items.add(UItem.asShadow(getString(R.string.DeviceInfoInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        boolean unlocked = unlockGate.tap(item.id == ITEM_ID_VERSION, SystemClock.elapsedRealtime());
        if (item.id == ITEM_ID_COPY_ALL) {
            AndroidUtilities.addToClipboard(buildFullInfo());
            Toast.makeText(getParentActivity(), getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
        } else if (item.id == ITEM_ID_VERSION) {
            if (unlocked) {
                ApplicationLoader.applicationContext.getSharedPreferences("extension_device_tools", Context.MODE_PRIVATE).edit().putBoolean("root_terminal_unlocked", true).apply();
                Toast.makeText(getParentActivity(), getString(R.string.DeviceInfoRootTerminalUnlocked), Toast.LENGTH_LONG).show();
                listView.adapter.update(false);
            }
        } else if (item.id == 3) {
            presentFragment(new RootTerminalActivity());
        }
    }

    private boolean isRootTerminalUnlocked() {
        return ApplicationLoader.applicationContext.getSharedPreferences("extension_device_tools", Context.MODE_PRIVATE).getBoolean("root_terminal_unlocked", false);
    }

    @Override public void onPause() {
        unlockGate.reset();
        super.onPause();
    }

    private void addInfo(ArrayList<UItem> items, CharSequence title, CharSequence value) {
        items.add(UItem.asButton(0, title, TextUtils.isEmpty(value) ? getString(R.string.DeviceInfoUnknown) : value));
    }

    private String buildFullInfo() {
        StringBuilder info = new StringBuilder();
        append(info, getString(R.string.DeviceInfoAppVersion), getAppVersion());
        append(info, getString(R.string.DeviceInfoPackage), ApplicationLoader.applicationContext.getPackageName());
        append(info, getString(R.string.DeviceInfoBuild), BuildVars.BUILD_VERSION_STRING);
        append(info, getString(R.string.DeviceInfoManufacturer), safe(Build.MANUFACTURER));
        append(info, getString(R.string.DeviceInfoModel), safe(Build.MODEL));
        append(info, getString(R.string.DeviceInfoDeviceName), safe(Build.DEVICE));
        append(info, getString(R.string.DeviceInfoProduct), safe(Build.PRODUCT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            append(info, getString(R.string.DeviceInfoSoc), safe(Build.SOC_MODEL));
        }
        append(info, getString(R.string.DeviceInfoAbi), TextUtils.join(", ", Build.SUPPORTED_ABIS));
        append(info, getString(R.string.DeviceInfoAndroid), Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT);
        append(info, getString(R.string.DeviceInfoLocale), Locale.getDefault().toString());
        append(info, getString(R.string.DeviceInfoTimezone), TimeZone.getDefault().getID());
        append(info, getString(R.string.DeviceInfoGles), getGlesVersion());
        append(info, getString(R.string.DeviceInfoScreen), AndroidUtilities.displaySize.x + " x " + AndroidUtilities.displaySize.y);
        append(info, getString(R.string.DeviceInfoDensity), String.format(Locale.US, "%.2f / %ddpi", AndroidUtilities.density, ApplicationLoader.applicationContext.getResources().getDisplayMetrics().densityDpi));
        append(info, getString(R.string.DeviceInfoRefreshRate), String.format(Locale.US, "%.1f Hz", AndroidUtilities.screenRefreshRate));
        append(info, getString(R.string.DeviceInfoPerformanceClass), SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass()));
        append(info, getString(R.string.DeviceInfoMeasuredClass), SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass()));
        append(info, getString(R.string.DeviceInfoCpu), getCpuInfo());
        append(info, getString(R.string.DeviceInfoMemory), getMemoryInfo());
        append(info, getString(R.string.DeviceInfoStorage), getStorageInfo());
        append(info, "AVC/H.264", getCodecInfo("video/avc"));
        append(info, "HEVC/H.265", getCodecInfo("video/hevc"));
        append(info, "VP8", getCodecInfo("video/x-vnd.on2.vp8"));
        append(info, "VP9", getCodecInfo("video/x-vnd.on2.vp9"));
        return info.toString();
    }

    private void append(StringBuilder info, String title, String value) {
        info.append(title).append(": ").append(TextUtils.isEmpty(value) ? getString(R.string.DeviceInfoUnknown) : value).append('\n');
    }

    private String getAppVersion() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            FileLog.e(e);
        }
        return "";
    }

    private String getGlesVersion() {
        try {
            ActivityManager activityManager = (ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
            ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
            return configurationInfo.getGlEsVersion();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "";
    }

    private String getCpuInfo() {
        int cpuCount = ConnectionsManager.CPU_COUNT;
        long maxFreqSum = 0;
        int maxFreqCount = 0;
        for (int i = 0; i < cpuCount; i++) {
            try (RandomAccessFile reader = new RandomAccessFile(String.format(Locale.US, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r")) {
                String line = reader.readLine();
                if (!TextUtils.isEmpty(line)) {
                    maxFreqSum += Utilities.parseInt(line);
                    maxFreqCount++;
                }
            } catch (Exception ignore) {
            }
        }
        if (maxFreqCount > 0) {
            return cpuCount + " cores, avg max " + (maxFreqSum / maxFreqCount / 1000L) + " MHz";
        }
        return cpuCount + " cores";
    }

    private String getMemoryInfo() {
        try {
            ActivityManager activityManager = (ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            return "class=" + AndroidUtilities.formatFileSize(activityManager.getMemoryClass() * 1024L * 1024L)
                    + ", total=" + AndroidUtilities.formatFileSize(memoryInfo.totalMem)
                    + ", avail=" + AndroidUtilities.formatFileSize(memoryInfo.availMem)
                    + ", low=" + memoryInfo.lowMemory;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "";
    }

    private String getStorageInfo() {
        try {
            File dir = ApplicationLoader.applicationContext.getFilesDir();
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long total = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            long free = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
            return "total=" + AndroidUtilities.formatFileSize(total) + ", free=" + AndroidUtilities.formatFileSize(free);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "";
    }

    private String getCodecInfo(String mimeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return getString(R.string.DeviceInfoUnsupported);
        }
        int decoders = 0;
        int encoders = 0;
        try {
            int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo == null) {
                    continue;
                }
                String[] types = codecInfo.getSupportedTypes();
                if (types == null) {
                    continue;
                }
                for (String type : types) {
                    if (mimeType.equalsIgnoreCase(type)) {
                        if (codecInfo.isEncoder()) {
                            encoders++;
                        } else {
                            decoders++;
                        }
                        break;
                    }
                }
            }
            return decoders + " decoders / " + encoders + " encoders";
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

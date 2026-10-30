package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.BatteryManager;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MangoPerformanceMonitor;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.MangoDownloadSpeedHud;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Locale;

/** On-demand performance dashboard. It never keeps a polling loop while hidden. */
public class MangoPerformanceActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (listView != null && fragmentView != null) {
                listView.adapter.update(false);
                AndroidUtilities.runOnUIThread(this, 1500);
            }
        }
    };

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoPerformanceTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fill, this::click, null);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = frame;
    }

    private String size(long value) {
        if (value < 0) return getString(R.string.MangoPerformanceNoData);
        return android.text.format.Formatter.formatFileSize(org.telegram.messenger.ApplicationLoader.applicationContext, value);
    }

    private String signedSize(long value) {
        if (value == Long.MIN_VALUE) return getString(R.string.MangoPerformanceNoData);
        if (value == 0) return "0 B";
        return (value > 0 ? "+" : "−") + size(Math.abs(value));
    }

    private String batteryStatus(MangoPerformanceMonitor.Snapshot s) {
        if (s.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) return getString(R.string.MangoPerformanceChargingNow);
        if (s.batteryStatus == BatteryManager.BATTERY_STATUS_FULL) return getString(R.string.MangoPerformanceFullyCharged);
        if (s.batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || s.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) return getString(R.string.MangoPerformanceNotCharging);
        return getString(R.string.MangoPerformanceNoData);
    }

    private String current(MangoPerformanceMonitor.Snapshot s) {
        return s.batteryCurrentMicroAmps == Integer.MIN_VALUE ? getString(R.string.MangoPerformanceNoData)
                : String.format(Locale.US, "%.1f mA", s.batteryCurrentMicroAmps / 1000f);
    }

    private String temperature(MangoPerformanceMonitor.Snapshot s) {
        return s.batteryTemperatureTenthsC == Integer.MIN_VALUE ? getString(R.string.MangoPerformanceNoData)
                : String.format(Locale.US, "%.1f °C", s.batteryTemperatureTenthsC / 10f);
    }

    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        MangoPerformanceMonitor.Snapshot s = MangoPerformanceMonitor.sample(getParentActivity(), currentAccount);
        int percent = s.batteryLevel >= 0 && s.batteryScale > 0 ? Math.round(s.batteryLevel * 100f / s.batteryScale) : -1;
        items.add(UItem.asHeader(getString(R.string.MangoPerformanceTitle)));
        items.add(UItem.asSwitch(10, getString(R.string.MangoDownloadHudEnabled)).setChecked(MangoDownloadSpeedHud.isEnabled(currentAccount)));
        items.add(UItem.asHeader(getString(R.string.MangoPerformanceMemory)));
        items.add(UItem.asSettingsCell(1, getString(R.string.MangoPerformanceMemory), size(s.memoryBytes)));
        items.add(UItem.asSettingsCell(2, getString(R.string.MangoPerformanceCache), size(s.cacheBytes)));
        items.add(UItem.asShadow(getString(R.string.MangoPerformanceCacheGrowth) + ": " + signedSize(s.cacheGrowthBytes)));
        items.add(UItem.asHeader(getString(R.string.MangoPerformanceBattery)));
        items.add(UItem.asSettingsCell(3, getString(R.string.MangoPerformanceBatteryLevel), percent < 0 ? getString(R.string.MangoPerformanceNoData) : percent + "%"));
        items.add(UItem.asSettingsCell(4, getString(R.string.MangoPerformanceBatteryCurrent), current(s)));
        items.add(UItem.asSettingsCell(5, getString(R.string.MangoPerformanceBatteryTemperature), temperature(s)));
        items.add(UItem.asSettingsCell(6, getString(R.string.MangoPerformanceCharging), batteryStatus(s)));
        items.add(UItem.asShadow(getString(R.string.MangoPerformanceBatteryDisclaimer)));
        items.add(UItem.asHeader(getString(R.string.MangoPerformanceBackgroundTasks)));
        items.add(UItem.asSettingsCell(7, getString(R.string.MangoPerformanceDownloads), Integer.toString(s.activeDownloads)));
        items.add(UItem.asSettingsCell(8, getString(R.string.MangoPerformancePendingSends), Integer.toString(s.pendingSendTasks)));
        items.add(UItem.asSettingsCell(11, getString(R.string.MangoPerformanceBackgroundProcesses), Integer.toString(s.backgroundProcesses)));
        items.add(UItem.asShadow(getString(R.string.MangoPerformanceAppState) + ": " + getString(s.foreground ? R.string.MangoPerformanceForeground : R.string.MangoPerformanceBackground)));
        items.add(UItem.asButton(9, getString(R.string.MangoPerformanceRefresh)));
    }

    private void click(UItem item, View view, int position, float x, float y) {
        if (item.id == 9 && listView != null) listView.adapter.update(true);
        else if (item.id == 10) {
            boolean enabled = !MangoDownloadSpeedHud.isEnabled(currentAccount);
            MangoDownloadSpeedHud.setEnabled(currentAccount, enabled);
            if (view instanceof TextCheckCell2) ((TextCheckCell2) view).setChecked(enabled);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        MangoPerformanceMonitor.beginSession();
        AndroidUtilities.cancelRunOnUIThread(refresh);
        AndroidUtilities.runOnUIThread(refresh, 200);
    }

    @Override
    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(refresh);
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(refresh);
        listView = null;
        super.onFragmentDestroy();
    }
}

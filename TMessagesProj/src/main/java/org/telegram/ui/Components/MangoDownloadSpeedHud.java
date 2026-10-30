package org.telegram.ui.Components;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MangoDownloadSpeedMonitor;
import org.telegram.messenger.MangoSettings;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;

/** In-app (not system overlay) draggable download speed indicator. */
public final class MangoDownloadSpeedHud {
    public static final String SETTING_KEY = "mango_download_speed_hud";
    private static final int BG = 0xcc202124;
    private static TextView view;
    private static LaunchActivity activity;
    private static ViewGroup container;
    private static float downX, downY, startX, startY, savedX, savedY;
    private static boolean dragging;
    private static final View.OnLayoutChangeListener layoutListener = (v, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> {
        if (view != null) clampTranslation(view, view.getTranslationX(), view.getTranslationY());
    };
    private static final MangoDownloadSpeedMonitor.Listener taskListener = MangoDownloadSpeedHud::onTasksChanged;
    private static final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (view == null || activity == null) return;
            if (update() > 0) AndroidUtilities.runOnUIThread(this, 1000);
        }
    };

    private MangoDownloadSpeedHud() {}

    public static void attach(LaunchActivity host) {
        if (host == null) return;
        if (!isEnabled(UserConfig.selectedAccount)) {
            detach();
            return;
        }
        if (activity == host && view != null) {
            AndroidUtilities.cancelRunOnUIThread(ticker);
            if (MangoDownloadSpeedMonitor.activeCount(UserConfig.selectedAccount) > 0) {
                update();
                AndroidUtilities.runOnUIThread(ticker, 200);
            } else {
                update();
            }
            return;
        }
        detach();
        activity = host;
        ViewGroup root = host.findViewById(android.R.id.content);
        if (root == null) return;
        container = root;
        view = new TextView(host);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        bg.setCornerRadius(AndroidUtilities.dp(18));
        view.setBackground(bg);
        view.setElevation(AndroidUtilities.dp(8));
        view.setOnTouchListener(MangoDownloadSpeedHud::onTouch);
        view.addOnLayoutChangeListener(layoutListener);
        view.setTranslationX(savedX);
        view.setTranslationY(savedY);
        view.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = AndroidUtilities.dp(56);
        lp.setMarginEnd(AndroidUtilities.dp(12));
        root.addView(view, lp);
        root.addOnLayoutChangeListener(layoutListener);
        MangoDownloadSpeedMonitor.addListener(taskListener);
        if (MangoDownloadSpeedMonitor.activeCount(UserConfig.selectedAccount) > 0) {
            AndroidUtilities.runOnUIThread(ticker, 300);
        } else {
            update();
        }
    }

    private static boolean onTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY();
                startX = v.getTranslationX(); startY = v.getTranslationY(); dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (!dragging && dx * dx + dy * dy > AndroidUtilities.dp(8) * AndroidUtilities.dp(8)) dragging = true;
                if (dragging) {
                    clampTranslation(v, startX + dx, startY + dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return false;
    }

    private static void clampTranslation(View v, float translationX, float translationY) {
        if (!(v.getParent() instanceof ViewGroup) || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0) return;
        // Work from the resolved child bounds, so END anchoring works in both
        // layout directions and saved positions remain visible after resizing.
        float minX = parent.getPaddingLeft() - v.getLeft();
        float maxX = parent.getWidth() - parent.getPaddingRight() - v.getWidth() - v.getLeft();
        float minY = parent.getPaddingTop() - v.getTop();
        float maxY = parent.getHeight() - parent.getPaddingBottom() - v.getHeight() - v.getTop();
        savedX = Math.max(minX, Math.min(Math.max(minX, maxX), translationX));
        savedY = Math.max(minY, Math.min(Math.max(minY, maxY), translationY));
        v.setTranslationX(savedX);
        v.setTranslationY(savedY);
    }

    private static int update() {
        if (view == null || activity == null) return 0;
        int account = UserConfig.selectedAccount;
        boolean enabled = isEnabled(account);
        if (!enabled) { view.setVisibility(View.GONE); return 0; }
        MangoDownloadSpeedMonitor.Snapshot s = MangoDownloadSpeedMonitor.sample(account);
        if (s.activeTasks <= 0) { view.setVisibility(View.GONE); return 0; }
        view.setText(LocaleController.formatString(R.string.MangoDownloadHudSpeed, AndroidUtilities.formatFileSize(s.bytesPerSecond), s.activeTasks));
        view.setVisibility(View.VISIBLE);
        return s.activeTasks;
    }

    public static void detach() {
        AndroidUtilities.cancelRunOnUIThread(ticker);
        MangoDownloadSpeedMonitor.removeListener(taskListener);
        if (container != null) container.removeOnLayoutChangeListener(layoutListener);
        if (view != null) view.removeOnLayoutChangeListener(layoutListener);
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view = null;
        container = null;
        activity = null;
    }

    public static void refresh() {
        if (view != null && update() > 0) AndroidUtilities.runOnUIThread(ticker, 1000);
    }

    private static void onTasksChanged() {
        if (view == null || activity == null) return;
        int tasks = update();
        AndroidUtilities.cancelRunOnUIThread(ticker);
        if (tasks > 0) AndroidUtilities.runOnUIThread(ticker, 200);
    }

    /** Persists the switch and applies it immediately when this activity is visible. */
    public static void setEnabled(int account, boolean enabled) {
        MangoSettings.setBoolean(account, SETTING_KEY, enabled);
        if (!enabled) {
            detach();
        } else if (LaunchActivity.instance != null && LaunchActivity.isResumed) {
            attach(LaunchActivity.instance);
        }
    }

    public static boolean isEnabled(int account) {
        return MangoSettings.getBoolean(account, SETTING_KEY, false);
    }
}

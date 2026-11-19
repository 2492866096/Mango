package org.telegram.ui.Components;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private static boolean dragging, pinned;
    private static int activePointerId = -1;
    private static View gestureView;
    private static final Runnable longPress = () -> {
        if (gestureView == null || gestureView != view || activePointerId < 0 || dragging) return;
        View target = gestureView;
        pinned = !pinned;
        target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        update();
        if (gestureView == target) target.announceForAccessibility(LocaleController.getString(pinned
                ? R.string.MangoDownloadHudUnlockHint : R.string.MangoDownloadHudLockHint));
    };
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
                finishGesture();
                gestureView = v;
                activePointerId = event.getPointerId(0);
                downX = event.getRawX(); downY = event.getRawY();
                startX = v.getTranslationX(); startY = v.getTranslationY(); dragging = false;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                AndroidUtilities.runOnUIThread(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                int pointer = event.findPointerIndex(activePointerId);
                if (gestureView != v || pointer < 0) {
                    finishGesture();
                    return true;
                }
                float dx = event.getRawX() + event.getX(pointer) - event.getX(0) - downX;
                float dy = event.getRawY() + event.getY(pointer) - event.getY(0) - downY;
                int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                if (dx * dx + dy * dy > slop * slop) {
                    AndroidUtilities.cancelRunOnUIThread(longPress);
                    dragging = true;
                }
                if (dragging && !pinned) {
                    clampTranslation(v, startX + dx, startY + dy);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishGesture();
                return true;
        }
        return false;
    }

    private static void finishGesture() {
        AndroidUtilities.cancelRunOnUIThread(longPress);
        if (gestureView != null && gestureView.getParent() != null) {
            gestureView.getParent().requestDisallowInterceptTouchEvent(false);
        }
        gestureView = null;
        activePointerId = -1;
        dragging = false;
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
        if (!enabled) { finishGesture(); view.setVisibility(View.GONE); return 0; }
        MangoDownloadSpeedMonitor.Snapshot s = MangoDownloadSpeedMonitor.sample(account);
        if (s.activeTasks <= 0) { finishGesture(); view.setVisibility(View.GONE); return 0; }
        String text = LocaleController.formatString(R.string.MangoDownloadHudSpeed, AndroidUtilities.formatFileSize(s.bytesPerSecond), s.activeTasks);
        if (pinned) text += " · " + LocaleController.getString(R.string.MangoDownloadHudPinned);
        view.setText(text);
        view.setContentDescription(text + ". " + LocaleController.getString(pinned
                ? R.string.MangoDownloadHudUnlockHint : R.string.MangoDownloadHudLockHint));
        view.setVisibility(View.VISIBLE);
        return s.activeTasks;
    }

    public static void detach() {
        finishGesture();
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
    }

    /** Applies direct edits, synchronized settings and undo/reset without writing preferences again. */
    public static void onPreferenceChanged(int account) {
        AndroidUtilities.runOnUIThread(() -> {
            // Recheck after dispatch: the user may switch accounts or pause the activity meanwhile.
            if (account != UserConfig.selectedAccount || LaunchActivity.instance == null || !LaunchActivity.isResumed) return;
            attach(LaunchActivity.instance);
        });
    }

    public static boolean isEnabled(int account) {
        return MangoSettings.getBoolean(account, SETTING_KEY, false);
    }
}

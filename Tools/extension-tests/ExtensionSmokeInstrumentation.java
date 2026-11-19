package org.telegram.extensiontests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.CustomWallpaperStore;
import org.telegram.messenger.DailyMessageTasksManager;
import org.telegram.messenger.ExtensionMediaSettings;
import org.telegram.messenger.MessageActionSettings;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageTagsController;
import org.telegram.messenger.PresenceSimulationManager;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.DailyMessageTasksActivity;
import org.telegram.ui.ExtensionFeaturesActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageActionSettingsActivity;
import org.telegram.ui.MessageManagementActivity;
import org.telegram.ui.MessageTagEditor;
import org.telegram.ui.PresenceSimulationActivity;
import org.telegram.ui.WallpapersListActivity;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/** Run only in a disposable emulator with a fresh, signed-out installation. */
public class ExtensionSmokeInstrumentation extends Instrumentation {
    private final StringBuilder report = new StringBuilder();
    private int checks;
    private volatile LaunchActivity resumedActivity;
    private String testLocale;

    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity instanceof LaunchActivity) resumedActivity = (LaunchActivity) activity;
    }

    @Override public void onCreate(Bundle args) { super.onCreate(args); testLocale = args == null ? null : args.getString("locale"); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runOnMainSync(() -> ApplicationLoader.postInitApplication());
            check(UserConfig.getActivatedAccounts().length == 0, "Test requires signed-out accounts");
            runOnMainSync(this::testPreferencesAndTags);
            runOnMainSync(this::testV4LocalStores);
            report.append(MessageInsightsInstrumentationChecks.run(this));
            testWallpapers();
            Intent intent = new Intent(getTargetContext(), LaunchActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(intent);
            for (int i = 0; i < 100 && resumedActivity == null; i++) Thread.sleep(100);
            check(resumedActivity != null, "Launch activity resumed");
            LaunchActivity activity = resumedActivity;
            Thread.sleep(1000);
            show(activity, new ExtensionFeaturesActivity(), "extension-tools");
            show(activity, new DailyMessageTasksActivity(), "daily-messages");
            DailyMessageTasksManager.Task draft = new DailyMessageTasksManager.Task();
            draft.title = "签到机器人";
            draft.dialogId = 987654321;
            draft.text = "/checkin";
            java.lang.reflect.Constructor<?> editorConstructor = Class.forName("org.telegram.ui.DailyMessageTasksActivity$Editor")
                    .getDeclaredConstructor(DailyMessageTasksManager.Task.class);
            editorConstructor.setAccessible(true);
            show(activity, (BaseFragment) editorConstructor.newInstance(draft), "daily-task-editor");
            show(activity, new PresenceSimulationActivity(), "presence");
            show(activity, new org.telegram.ui.ScamProtectionActivity(), "scam-protection");
            show(activity, new org.telegram.ui.MessageEchoActivity(), "message-echo");
            show(activity, new org.telegram.ui.RecalledMessagesActivity(), "recalled-messages");
            show(activity, new org.telegram.ui.InteractionStatisticsActivity(), "interaction-statistics");
            show(activity, new org.telegram.ui.MessageMemorialActivity(), "message-memorial");
            show(activity, new org.telegram.ui.DuplicateFilesActivity(), "duplicate-files");
            show(activity, new org.telegram.ui.ChannelBatchLeaveActivity(), "batch-leave");
            show(activity, new org.telegram.ui.DeviceInfoActivity(), "device-info");
            runOnMainSync(() -> {
                getTargetContext().getSharedPreferences("extension_device_tools", 0).edit().putBoolean("root_terminal_unlocked", false).commit();
                check(!new org.telegram.ui.RootTerminalActivity().onFragmentCreate(), "Root terminal remains locked");
                getTargetContext().getSharedPreferences("extension_device_tools", 0).edit().putBoolean("root_terminal_unlocked", true).commit();
            });
            show(activity, new org.telegram.ui.RootTerminalActivity(), "root-terminal");
            runOnMainSync(() -> getTargetContext().getSharedPreferences("extension_device_tools", 0).edit().putBoolean("root_terminal_unlocked", false).commit());
            show(activity, new MessageActionSettingsActivity(), "message-actions");
            MessageTagsController.Entry preview = new MessageTagsController.Entry();
            preview.dialogId = -987654321;
            preview.messageId = 42;
            preview.title = "工作讨论群";
            preview.preview = "请在周五之前确认项目安排，这条消息需要稍后回复。";
            preview.date = (int) (System.currentTimeMillis() / 1000);
            runOnMainSync(() -> MessageTagsController.setTags(0, preview, MessageTagsController.IMPORTANT | MessageTagsController.REPLY));
            MessageManagementActivity center = new MessageManagementActivity();
            show(activity, center, "message-center");
            runOnMainSync(() -> MessageTagEditor.show(center, preview, null));
            Thread.sleep(700);
            screenshot("message-tags");
            runOnMainSync(() -> { center.dismissCurrentDialog(); MessageTagsController.setTags(0, preview, 0); });
            show(activity, new WallpapersListActivity(WallpapersListActivity.TYPE_ALL), "wallpapers");
            result.putString("stream", "\nPASS: " + checks + " assertions\n" + report);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "\nFAIL: " + android.util.Log.getStackTraceString(failure) + "\n" + report);
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        checks++;
    }

    private void testPreferencesAndTags() {
        boolean audio = ExtensionMediaSettings.isAutoPlayNextEnabled();
        ExtensionMediaSettings.setAutoPlayNextEnabled(!audio);
        check(ExtensionMediaSettings.isAutoPlayNextEnabled() != audio, "Audio preference round trip");
        ExtensionMediaSettings.setAutoPlayNextEnabled(audio);
        int[] saved = MessageActionSettings.getMenuOrder(0);
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++) {
            if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
            int[] order = {a, b, c, d};
            MessageActionSettings.setMenuOrder(0, order);
            check(Arrays.equals(order, MessageActionSettings.getMenuOrder(0)), "Menu persistence");
            ArrayList<Integer> options = new ArrayList<>(Arrays.asList(90, 10, 11, 91, 12, 13, 92));
            ArrayList<Integer> icons = new ArrayList<>(options);
            ArrayList<CharSequence> labels = new ArrayList<>();
            for (int option : options) labels.add(Integer.toString(option));
            MessageActionSettings.reorderMenu(0, icons, labels, options, 10, 11, 12, 13);
            int[] positions = {1, 2, 4, 5};
            for (int i = 0; i < 4; i++) {
                int pos = positions[i];
                check(options.get(pos) == 10 + order[i], "Menu permutation");
                check(icons.get(pos).equals(options.get(pos)) && labels.get(pos).equals(options.get(pos).toString()), "Menu icon/label alignment");
            }
            check(options.get(0) == 90 && options.get(3) == 91 && options.get(6) == 92, "Unrelated actions stay in place");
        }
        MessageActionSettings.setMenuOrder(0, saved);
        MessageActionSettings.setDoubleTapAction(0, MessageActionSettings.DOUBLE_TAP_REPLY);
        check(MessageActionSettings.getDoubleTapAction(0) == MessageActionSettings.DOUBLE_TAP_REPLY, "Double tap reply persists");
        check(MessageActionSettings.getDoubleTapAction(1) == MessageActionSettings.DOUBLE_TAP_REACTION, "Account action isolation");
        MessageActionSettings.setDoubleTapAction(0, MessageActionSettings.DOUBLE_TAP_REACTION);
        MessageActionSettings.setCenterEnabled(0, true);
        check(MessageActionSettings.isCenterEnabled(0) && !MessageActionSettings.isCenterEnabled(1), "Account tab isolation");
        MessageActionSettings.setCenterEnabled(0, false);
        MessageTagsController.Entry entry = new MessageTagsController.Entry();
        entry.dialogId = -987654321;
        entry.messageId = 42;
        entry.topicId = 1;
        entry.title = "Test group";
        entry.preview = "Local test message";
        for (int tags = 1; tags < 16; tags++) {
            MessageTagsController.setTags(0, entry, tags);
            check(MessageTagsController.getTags(0, entry) == tags, "Tags round trip");
            check(MessageTagsController.getTags(1, entry) == 0, "Account tag isolation");
            for (int tag : MessageTagsController.TAGS) {
                check(MessageTagsController.getEntries(0, tag).size() == ((tags & tag) == 0 ? 0 : 1), "Tag filter");
            }
        }
        MessageTagsController.setTags(0, entry, 0);
        check(MessageTagsController.getEntries(0, 0).isEmpty(), "Clear tags");
        DailyMessageTasksManager.Task task = new DailyMessageTasksManager.Task();
        task.dialogId = -987654321;
        task.text = "/checkin";
        check(!DailyMessageTasksManager.getInstance(0).save(task), "Signed-out account cannot save sends");
        check(!PresenceSimulationManager.getInstance(0).startTyping(0, "", 0, 1), "Invalid typing target rejected");
        report.append("Preference, menu, tag and account-isolation checks passed.\n");
    }

    private void testV4LocalStores() {
        org.telegram.messenger.UserConfig config = UserConfig.getInstance(0);
        java.lang.reflect.Field currentUser = null;
        try {
            currentUser = UserConfig.class.getDeclaredField("currentUser");
            currentUser.setAccessible(true);
            org.telegram.tgnet.TLRPC.TL_user owner = new org.telegram.tgnet.TLRPC.TL_user();
            owner.id = 777000101;
            // Memory-only identity in a signed-out emulator; no auth or sending is invoked.
            currentUser.set(config, owner);
            org.telegram.messenger.RecalledMessagesStore.clear(0);
            org.telegram.messenger.RecalledMessagesStore.setEnabled(0, true);
            org.telegram.tgnet.TLRPC.TL_message message = new org.telegram.tgnet.TLRPC.TL_message();
            message.id = 101;
            message.date = 1700000000;
            message.dialog_id = 777000102;
            message.peer_id = new org.telegram.tgnet.TLRPC.TL_peerUser();
            message.peer_id.user_id = message.dialog_id;
            message.message = "Local recalled message fixture";
            org.telegram.messenger.RecalledMessagesStore.save(0, message);
            ArrayList<org.telegram.tgnet.TLRPC.Message> restored = org.telegram.messenger.RecalledMessagesStore.list(0, message.dialog_id);
            check(restored.size() == 1 && restored.get(0).message.equals(message.message), "Recall contents persisted");
            check(restored.get(0).send_state == 4, "Recall state restored");
            org.telegram.messenger.RecalledMessagesStore.save(0, message);
            check(org.telegram.messenger.RecalledMessagesStore.list(0, 0).size() == 1, "Recall deduplication");
            check(org.telegram.messenger.RecalledMessagesStore.list(1, 0).isEmpty(), "Recall account isolation");
            owner.id++;
            check(org.telegram.messenger.RecalledMessagesStore.list(0, 0).isEmpty(), "Recall owner isolation");
            owner.id--;
            ArrayList<org.telegram.tgnet.TLRPC.Message> page = new ArrayList<>();
            org.telegram.messenger.RecalledMessagesStore.merge(0, message.dialog_id, page, 0, 20, 0, false);
            check(page.size() == 1 && page.get(0).id == 101, "Recall restores empty chat page");
            org.telegram.messenger.RecalledMessagesStore.merge(0, message.dialog_id, page, 0, 20, 0, false);
            check(page.size() == 1, "Recall merge is idempotent");
            page.clear();
            org.telegram.messenger.RecalledMessagesStore.merge(0, message.dialog_id, page, 0, 20, 44, false);
            check(page.isEmpty(), "Recall excludes other topics");
            message.id++;
            message.ttl = 30;
            org.telegram.messenger.RecalledMessagesStore.save(0, message);
            check(org.telegram.messenger.RecalledMessagesStore.list(0, 0).size() == 1, "Recall excludes expiring messages");
            message.ttl = 0;
            org.telegram.messenger.RecalledMessagesStore.setEnabled(0, false);
            org.telegram.messenger.RecalledMessagesStore.save(0, message);
            check(org.telegram.messenger.RecalledMessagesStore.list(0, 0).size() == 1, "Recall toggle stops new saves and retains archive");
            org.telegram.messenger.RecalledMessagesStore.clear(0);
            check(org.telegram.messenger.RecalledMessagesStore.list(0, 0).isEmpty(), "Recall clear is durable");
            org.telegram.messenger.MessageEchoManager.setTarget(0, message.dialog_id, true);
            check(org.telegram.messenger.MessageEchoManager.targets(0).contains("" + message.dialog_id), "Echo target round trip");
            check(org.telegram.messenger.MessageEchoManager.targets(1).isEmpty(), "Echo account isolation");
            owner.id++;
            check(org.telegram.messenger.MessageEchoManager.targets(0).isEmpty(), "Echo owner isolation");
            owner.id--;
            org.telegram.messenger.MessageEchoManager.setTarget(0, message.dialog_id, false);
            check(org.telegram.messenger.MessageEchoManager.targets(0).isEmpty(), "Echo target removal");
            org.telegram.messenger.ScamProtectionManager.setEnabled(0, true);
            org.telegram.messenger.ScamProtectionManager.setKeywords(0, "TEST SCAM");
            message.message = "This is a test scam fixture";
            org.telegram.messenger.MessageObject object = new org.telegram.messenger.MessageObject(0, message, false, false);
            String keyword = org.telegram.messenger.ScamProtectionManager.match(0, object);
            check(keyword != null, "Scam matches normalized keyword");
            check(org.telegram.messenger.ScamProtectionManager.markShown(0, object, keyword), "Scam first hint");
            check(!org.telegram.messenger.ScamProtectionManager.markShown(0, object, keyword), "Scam suppresses repeat hint");
            message.out = true;
            check(org.telegram.messenger.ScamProtectionManager.match(0, object) == null, "Scam excludes outgoing");
            check(!org.telegram.messenger.MessageEchoManager.canEcho(0, object), "Echo excludes outgoing");
            org.telegram.messenger.ScamProtectionManager.setKeywords(0, org.telegram.messenger.ScamProtectionManager.DEFAULT_KEYWORDS);
            report.append("V4 recall storage, account/owner isolation, echo preferences and scam hint checks passed.\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try { if (currentUser != null) currentUser.set(config, null); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    private void testWallpapers() throws Exception {
        File source = new File(getTargetContext().getCacheDir(), "extension-test.jpg");
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff146eac);
        try (FileOutputStream out = new FileOutputStream(source)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); }
        bitmap.recycle();
        File first = CustomWallpaperStore.importWallpaper(source);
        File second = CustomWallpaperStore.importWallpaper(source);
        check(first.equals(second), "Wallpaper deduplication");
        source.delete();
        check(first.exists() && first.length() > 0, "Wallpaper survives source deletion");
        String slug = CustomWallpaperStore.slugForFile(first);
        check(CustomWallpaperStore.getWallpapers().contains(first), "Wallpaper appears in library");
        check(!CustomWallpaperStore.deleteWallpaper("../../test"), "Wallpaper traversal rejected");
        check(CustomWallpaperStore.deleteWallpaper(slug), "Wallpaper deletion");
        check(!CustomWallpaperStore.containsWallpaper(slug), "Wallpaper removed from library");
        report.append("Wallpaper persistence checks passed.\n");
    }

    private void show(LaunchActivity activity, BaseFragment fragment, String name) throws Exception {
        runOnMainSync(() -> {
            if (testLocale != null) {
                LocaleController.LocaleInfo locale = new LocaleController.LocaleInfo();
                locale.shortName = locale.pluralLangCode = testLocale;
                locale.name = locale.nameEnglish = testLocale;
                locale.builtIn = true;
                LocaleController.getInstance().applyLanguage(locale, false, true, false, true, 0, null);
            }
            check(activity.presentFragment(fragment, false, true), "Open " + name);
        });
        Thread.sleep(700);
        runOnMainSync(() -> {
            check(fragment.getFragmentView() != null && fragment.getFragmentView().getWidth() > 0, "Laid out " + name);
            checkNoMissingStrings(fragment.getFragmentView());
        });
        screenshot(name);
        report.append("Rendered ").append(name).append(".\n");
    }

    private void screenshot(String name) throws Exception {
        File dir = new File(getTargetContext().getExternalFilesDir(null), "extension-smoke");
        dir.mkdirs();
        Bitmap screen = getUiAutomation().takeScreenshot();
        if (screen != null) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { screen.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screen.recycle();
        }
    }

    private void checkNoMissingStrings(View view) {
        if (view instanceof TextView) check(!((TextView) view).getText().toString().contains("LOC_ERR"), "No missing locale strings");
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkNoMissingStrings(group.getChildAt(i));
        }
    }
}

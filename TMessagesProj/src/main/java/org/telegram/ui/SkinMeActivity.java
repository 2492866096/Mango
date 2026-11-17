package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.skin.me.MeActions;
import org.telegram.skin.me.MeCard;
import org.telegram.skin.me.MeNotice;
import org.telegram.skin.me.MeRow;
import org.telegram.skin.me.MeRowAction;
import org.telegram.skin.me.MeState;
import org.telegram.skin.me.MeStrings;
import org.telegram.skin.me.MeToggle;
import org.telegram.skin.me.MeTool;
import org.telegram.skin.me.MeView;
import org.telegram.skin.motion.SkinMotion;
import org.telegram.skin.ui.Glyph;
import org.telegram.skin.ui.Peer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * 我: the last tab of the dock. It takes over from Mango's 设置 and 资料 tabs:
 * the account card opens the profile, the deck holds the four switches people
 * reach for most, and the rest are doors into Telegram's and Mango's pages.
 */
public class SkinMeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate {
    private static final int NOTICE_FROZEN = 0;
    private static final int NOTICE_SUGGESTION = 1;

    private static final int ROW_SAVED = 100;
    private static final int ROW_STORIES = 101;

    private static final int TOOL_CENTER = 300;
    private static final int TOOL_BROADCAST = 301;
    private static final int TOOL_RULES = 302;
    private static final int TOOL_SECURITY = 303;
    private static final int TOOL_PERFORMANCE = 304;
    private static final int TOOL_ALL = 305;

    private static final int SET_ACCOUNT = 200;
    private static final int SET_CHAT = 201;
    private static final int SET_PRIVACY = 202;
    private static final int SET_NOTIFICATIONS = 203;
    private static final int SET_DATA = 204;
    private static final int SET_FOLDERS = 205;
    private static final int SET_DEVICES = 206;
    private static final int SET_POWER = 207;
    private static final int SET_LANGUAGE = 208;
    private static final int SET_DOCK = 209;
    private static final int SET_MORE = 210;

    private final SkinDock dock;
    private MeView view;

    public SkinMeActivity(SkinDock dock) {
        this.dock = dock;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return null;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter center = getNotificationCenter();
        center.addObserver(this, NotificationCenter.mainUserInfoChanged);
        center.addObserver(this, NotificationCenter.updateInterfaces);
        center.addObserver(this, NotificationCenter.didUpdateConnectionState);
        center.addObserver(this, NotificationCenter.notificationsSettingsUpdated);
        center.addObserver(this, NotificationCenter.newSuggestionsAvailable);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter center = getNotificationCenter();
        center.removeObserver(this, NotificationCenter.mainUserInfoChanged);
        center.removeObserver(this, NotificationCenter.updateInterfaces);
        center.removeObserver(this, NotificationCenter.didUpdateConnectionState);
        center.removeObserver(this, NotificationCenter.notificationsSettingsUpdated);
        center.removeObserver(this, NotificationCenter.newSuggestionsAvailable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        if (view != null) {
            view.dispose();
            view = null;
        }
        super.onFragmentDestroy();
    }

    @Override
    public void clearViews() {
        if (view != null) {
            view.dispose();
            view = null;
        }
        super.clearViews();
    }

    @Override
    public View createView(Context context) {
        SkinBridge.install();
        view = new MeView(context, new MeStrings(
                LocaleController.getString(R.string.SkinMeQr),
                LocaleController.getString(R.string.SkinMeTools),
                SkinBridge.text(R.string.SkinMeSettings, R.string.Settings),
                LocaleController.getString(R.string.SkinMeOpenProfile),
                LocaleController.getString(R.string.SkinSwitchAccount)));
        view.setActions(actions);
        SkinBridge.applyTheme(view);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            MeState state = view.getState();
            state.setTopInset(bars.top);
            state.setBottomInset(bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        fill();
        return fragmentView = view;
    }

    @Override
    public void onResume() {
        super.onResume();
        SkinMotion.refresh();
        fill();
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    private void fill() {
        if (view == null) {
            return;
        }
        MeState state = view.getState();
        int account = currentAccount;
        TLRPC.User user = getUserConfig().getCurrentUser();
        if (user != null) {
            String name = SkinBridge.userName(user);
            state.setCard(new MeCard(
                    new Peer(account, user.id, name, dock.avatarVersion()),
                    name,
                    SkinBridge.handle(user),
                    SkinBridge.maskedPhone(user),
                    SkinBridge.connectionStatus(account),
                    SkinBridge.isConnected(account)));
        }
        int others = UserConfig.getActivatedAccountsCount() - 1;
        state.setOtherAccounts(Math.max(0, others));
        state.setAccountsHint(others > 0 ? LocaleController.formatString(R.string.SkinMeAccountsHint, others) : null);

        ArrayList<MeNotice> notices = new ArrayList<>();
        if (getMessagesController().isFrozen()) {
            notices.add(new MeNotice(NOTICE_FROZEN, LocaleController.getString(R.string.SkinNoticeFrozen), true));
        }
        Set<String> suggestions = getMessagesController().pendingSuggestions;
        if (suggestions.contains("PREMIUM_GRACE") || suggestions.contains("VALIDATE_PHONE_NUMBER") || suggestions.contains("VALIDATE_PASSWORD")) {
            notices.add(new MeNotice(NOTICE_SUGGESTION, LocaleController.getString(R.string.SkinNoticeSuggestion), false));
        }
        state.setNotices(notices);

        state.setToggles(Arrays.asList(
                toggle(SkinToggles.NIGHT, Glyph.Moon, R.string.SkinToggleNight, R.string.SkinToggleNight),
                toggle(SkinToggles.PROXY, Glyph.Globe, R.string.SkinToggleProxy, R.string.SkinToggleProxy),
                toggle(SkinToggles.DATA_SAVER, Glyph.DataSaver, R.string.SkinToggleDataSaver, R.string.SkinToggleDataSaverInfo),
                toggle(SkinToggles.QUIET, Glyph.Quiet, R.string.SkinToggleQuiet, R.string.SkinToggleQuietInfo)));

        state.setShortcuts(Arrays.asList(
                new MeRow(ROW_SAVED, Glyph.Bookmark, SkinBridge.text(R.string.SkinMeSaved, R.string.SavedMessages), null, null),
                new MeRow(ROW_STORIES, Glyph.Stories, LocaleController.getString(R.string.SkinMeStories), null,
                        new MeRowAction(Glyph.Camera, LocaleController.getString(R.string.SkinMePostStory)))));

        state.setTools(Arrays.asList(
                new MeTool(TOOL_CENTER, Glyph.Checklist, LocaleController.getString(R.string.SkinTabCenter)),
                new MeTool(TOOL_BROADCAST, Glyph.Broadcast, LocaleController.getString(R.string.SkinToolBroadcast)),
                new MeTool(TOOL_RULES, Glyph.Rules, LocaleController.getString(R.string.SkinToolRules)),
                new MeTool(TOOL_SECURITY, Glyph.ShieldCheck, LocaleController.getString(R.string.SkinToolSecurity)),
                new MeTool(TOOL_PERFORMANCE, Glyph.Gauge, LocaleController.getString(R.string.SkinToolPerformance)),
                new MeTool(TOOL_ALL, Glyph.Grid, LocaleController.getString(R.string.SkinToolAll))));

        state.setSettings(Arrays.asList(
                setting(SET_ACCOUNT, Glyph.Person, R.string.SkinSetAccount, R.string.SettingsAccount, null),
                setting(SET_CHAT, Glyph.Palette, R.string.SkinSetChat, R.string.SettingsChat, null),
                setting(SET_PRIVACY, Glyph.Lock, R.string.SkinSetPrivacy, R.string.SettingsPrivacySecurity, null),
                setting(SET_NOTIFICATIONS, Glyph.Bell, R.string.SkinSetNotifications, R.string.SettingsNotifications, null),
                setting(SET_DATA, Glyph.Database, R.string.SkinSetData, R.string.SettingsData, null),
                setting(SET_FOLDERS, Glyph.Folder, R.string.SkinSetFolders, R.string.SettingsFolders, null),
                setting(SET_DEVICES, Glyph.Devices, R.string.SkinSetDevices, R.string.SettingsDevices, null),
                setting(SET_POWER, Glyph.Leaf, R.string.SkinSetPower, R.string.SettingsPowerSaving, null),
                setting(SET_LANGUAGE, Glyph.Language, R.string.SkinSetLanguage, R.string.SettingsLanguage, LocaleController.getCurrentLanguageName()),
                setting(SET_DOCK, Glyph.Dock, R.string.SkinArrangeTitle, 0, null),
                setting(SET_MORE, Glyph.Gear, R.string.SkinSetMore, 0, null)));
    }

    private MeToggle toggle(int id, Glyph glyph, int label, int description) {
        return new MeToggle(id, glyph, LocaleController.getString(label), SkinToggles.isOn(id, currentAccount), LocaleController.getString(description));
    }

    private static MeRow setting(int id, Glyph glyph, int ours, int telegram, String value) {
        return new MeRow(id, glyph, SkinBridge.text(ours, telegram), value, null);
    }

    private void openProfile() {
        long selfId = getUserConfig().getClientUserId();
        TLRPC.User currentUser = getUserConfig().getCurrentUser();
        if (getMessagesController().getUser(selfId) == null && currentUser != null) {
            getMessagesController().putUser(currentUser, false);
        }
        Bundle args = new Bundle();
        args.putLong("user_id", selfId);
        args.putBoolean("my_profile", true);
        presentFragment(new ProfileActivity(args));
    }

    // As the chat list's story camera button does.
    private void postStory() {
        if (getParentActivity() == null) {
            return;
        }
        if (!getMessagesController().storiesEnabled()) {
            presentFragment(new PremiumPreviewFragment("stories"));
            return;
        }
        StoriesController.StoryLimit limit = getMessagesController().getStoriesController().checkStoryLimit();
        if (limit != null && limit.active(currentAccount, 1)) {
            showDialog(new LimitReachedBottomSheet(this, getContext(), limit.getLimitReachedType(), currentAccount, null));
            return;
        }
        StoryRecorder.getInstance(getParentActivity(), currentAccount).open(null, true);
    }

    /** Mango's pages work per account; tell each one which. */
    private void presentForAccount(BaseFragment page) {
        page.setCurrentAccount(currentAccount);
        presentFragment(page);
    }

    private final MeActions actions = new MeActions() {
        @Override
        public void onCard() {
            openProfile();
        }

        @Override
        public void onQr() {
            Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            presentFragment(new QrActivity(args));
        }

        @Override
        public void onAccounts() {
            dock.openAccounts();
        }

        @Override
        public void onNotice(int id) {
            if (id == NOTICE_FROZEN) {
                AccountFrozenAlert.show(currentAccount);
            } else {
                presentFragment(new SettingsActivity());
            }
        }

        @Override
        public void onToggle(int id, float x, float y) {
            int[] origin = new int[2];
            if (view != null) {
                view.getLocationInWindow(origin);
            }
            origin[0] += (int) x;
            origin[1] += (int) y;
            SkinToggles.toggle(SkinMeActivity.this, id, origin);
            fill();
        }

        @Override
        public void onToggleDetails(int id) {
            SkinToggles.openDetails(SkinMeActivity.this, id);
        }

        @Override
        public void onRow(int id) {
            switch (id) {
                case ROW_SAVED: {
                    Bundle args = new Bundle();
                    args.putLong("user_id", getUserConfig().getClientUserId());
                    presentFragment(new ChatActivity(args));
                    break;
                }
                case ROW_STORIES: {
                    Bundle args = new Bundle();
                    args.putLong("dialog_id", getUserConfig().getClientUserId());
                    args.putInt("type", MediaActivity.TYPE_STORIES);
                    presentFragment(new MediaActivity(args, null));
                    break;
                }
                case SET_ACCOUNT:
                    presentFragment(new UserInfoActivity());
                    break;
                case SET_CHAT:
                    presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                    break;
                case SET_PRIVACY:
                    presentFragment(new PrivacySettingsActivity());
                    break;
                case SET_NOTIFICATIONS:
                    presentFragment(new NotificationsSettingsActivity());
                    break;
                case SET_DATA:
                    presentFragment(new DataSettingsActivity());
                    break;
                case SET_FOLDERS:
                    presentFragment(new FiltersSetupActivity());
                    break;
                case SET_DEVICES:
                    presentFragment(new SessionsActivity(0));
                    break;
                case SET_POWER:
                    presentFragment(new LiteModeSettingsActivity());
                    break;
                case SET_LANGUAGE:
                    presentFragment(new LanguageSelectActivity());
                    break;
                case SET_DOCK:
                    dock.openArrange();
                    break;
                case SET_MORE:
                    presentFragment(new SettingsActivity());
                    break;
            }
        }

        @Override
        public void onRowAction(int id) {
            if (id == ROW_STORIES) {
                postStory();
            }
        }

        @Override
        public void onTool(int id) {
            switch (id) {
                case TOOL_CENTER:
                    presentForAccount(new MessageManagementActivity(false));
                    break;
                case TOOL_BROADCAST:
                    presentForAccount(new MangoUserBroadcastActivity());
                    break;
                case TOOL_RULES:
                    presentForAccount(new MangoNotificationRulesActivity());
                    break;
                case TOOL_SECURITY:
                    presentForAccount(new MangoSecurityCheckActivity());
                    break;
                case TOOL_PERFORMANCE:
                    presentForAccount(new MangoPerformanceActivity());
                    break;
                case TOOL_ALL:
                    presentForAccount(new ExtensionFeaturesActivity());
                    break;
            }
        }
    };

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            SkinBridge.applyTheme(view);
        }
        fill();
    }

    @Override
    public boolean canParentTabsSlide(android.view.MotionEvent ev, boolean forward) {
        return true;
    }

    @Override
    public void onParentScrollToTop() {
        if (view != null) {
            MeState state = view.getState();
            state.setScrollToTop(state.getScrollToTop() + 1);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        descriptions.add(new ThemeDescription(null, 0, null, null, null, () -> SkinBridge.applyTheme(view), Theme.key_windowBackgroundWhite));
        return descriptions;
    }
}

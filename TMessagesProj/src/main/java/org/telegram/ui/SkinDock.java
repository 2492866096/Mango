package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.skin.dock.DockAccount;
import org.telegram.skin.dock.DockActions;
import org.telegram.skin.dock.DockChoice;
import org.telegram.skin.dock.DockContact;
import org.telegram.skin.dock.DockKey;
import org.telegram.skin.dock.DockMode;
import org.telegram.skin.dock.DockState;
import org.telegram.skin.dock.DockStrings;
import org.telegram.skin.dock.DockTab;
import org.telegram.skin.dock.DockView;
import org.telegram.skin.ui.Glyph;
import org.telegram.skin.ui.Peer;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Runs the aluminium dock for {@link MainTabsActivity}: which tabs it shows,
 * which page each one is, and what its keys, panels and account fan do.
 *
 * With the dock the tab screen's pages sit in the order of the dock's keys,
 * so swiping between pages walks along the bar: 对话 first, 我 last, the
 * person's chosen tabs between, and 全部账号 whenever more than one account is
 * signed in.
 */
final class SkinDock implements NotificationCenter.NotificationCenterDelegate {
    static final int TAB_CHATS = 0;
    static final int TAB_CONTACTS = 1;
    static final int TAB_CALLS = 2;
    static final int TAB_ALL_ACCOUNTS = 3;
    static final int TAB_CENTER = 5;
    static final int TAB_ME = 6;

    /** What may go in the middle of the dock, in the arranger's order. */
    private static final int[] CHOICES = {TAB_CONTACTS, TAB_CALLS, TAB_CENTER};
    private static final String MIDDLE_KEY = "skin_dock_middle";
    private static final String MIDDLE_DEFAULT = TAB_CONTACTS + "," + TAB_CALLS;
    private static final int MAX_MIDDLE = 3;

    private static final int KEY_NEW_CHAT = 0;
    private static final int KEY_NEW_GROUP = 1;
    private static final int KEY_NEW_CHANNEL = 2;
    private static final int KEY_SECRET_CHAT = 3;

    private final MainTabsActivity host;
    private int[] order;
    private DockView view;
    private View menuAnchor;
    private boolean observing;
    private int avatarVersion;
    private int scrolled;

    private Runnable pendingSearch;
    private int searchGeneration;
    private int searchRequest;

    SkinDock(MainTabsActivity host) {
        this.host = host;
        order = buildOrder();
    }

    // ---- Pages ----

    int count() {
        return order.length;
    }

    int chatsPosition() {
        return 0;
    }

    int kindAt(int position) {
        return position >= 0 && position < order.length ? order[position] : TAB_CHATS;
    }

    /**
     * The old fixed page index for tabs the glass bar also had, so their
     * pages are built exactly as before; -1 for tabs only the dock has.
     */
    int legacyPosition(int position) {
        switch (kindAt(position)) {
            case TAB_CHATS: return MainTabsActivity.POSITION_CHATS;
            case TAB_CONTACTS: return MainTabsActivity.POSITION_CONTACTS;
            case TAB_CENTER: return MainTabsActivity.POSITION_MESSAGE_CENTER;
            case TAB_ALL_ACCOUNTS: return MainTabsActivity.POSITION_UNIFIED_INBOX;
            default: return -1;
        }
    }

    BaseFragment createFragment(int position) {
        if (kindAt(position) == TAB_CALLS) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            return new CallLogActivity(args);
        }
        return new SkinMeActivity(this);
    }

    boolean canScroll(MotionEvent event, boolean forward) {
        ViewPagerFixed pager = pager();
        if (pager == null) {
            return false;
        }
        int position = pager.getCurrentPosition();
        if (forward ? position >= order.length - 1 : position <= 0) {
            return false;
        }
        BaseFragment fragment = host.getCurrentVisibleFragment();
        if (fragment instanceof MainTabsActivity.TabFragmentDelegate) {
            return ((MainTabsActivity.TabFragmentDelegate) fragment).canParentTabsSlide(event, forward);
        }
        return true;
    }

    private int[] buildOrder() {
        int account = host.getCurrentAccount();
        ArrayList<Integer> tabs = new ArrayList<>();
        tabs.add(TAB_CHATS);
        for (int kind : middle()) {
            tabs.add(kind);
        }
        if (UserConfig.getActivatedAccountsCount() > 1 && CrossAccountInboxActivity.isEnabled(account)) {
            tabs.add(TAB_ALL_ACCOUNTS);
        }
        tabs.add(TAB_ME);
        int[] result = new int[tabs.size()];
        for (int i = 0; i < result.length; i++) result[i] = tabs.get(i);
        return result;
    }

    private static int[] middle() {
        String stored = MessagesController.getGlobalMainSettings().getString(MIDDLE_KEY, MIDDLE_DEFAULT);
        ArrayList<Integer> kinds = new ArrayList<>();
        if (!TextUtils.isEmpty(stored)) {
            for (String part : stored.split(",")) {
                try {
                    int kind = Integer.parseInt(part.trim());
                    if (indexOf(CHOICES, kind) >= 0 && !kinds.contains(kind) && kinds.size() < MAX_MIDDLE) {
                        kinds.add(kind);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int[] result = new int[kinds.size()];
        for (int i = 0; i < result.length; i++) result[i] = kinds.get(i);
        return result;
    }

    /**
     * Moves the pages to a new tab order. Pages whose tab went away are
     * destroyed; the rest keep their state and follow their tab.
     */
    private void applyOrder(int[] next) {
        if (Arrays.equals(order, next)) {
            return;
        }
        int[] previous = order;
        ViewPagerFixed pager = pager();
        int currentKind = pager != null ? kindAt(pager.getCurrentPosition()) : TAB_CHATS;
        if (indexOf(next, currentKind) < 0) {
            if (pager != null) pager.setPosition(indexOf(previous, TAB_CHATS));
            currentKind = TAB_CHATS;
        }
        SparseArray<ViewPagerActivity.FragmentState> pages = host.fragmentsArr;
        for (int position = 0; position < previous.length; position++) {
            if (indexOf(next, previous[position]) < 0 && pages.get(position) != null) {
                host.dropFragmentAtPosition(position);
            }
        }
        SparseArray<ViewPagerActivity.FragmentState> moved = new SparseArray<>();
        for (int i = 0; i < pages.size(); i++) {
            int position = pages.keyAt(i);
            if (position < previous.length) {
                moved.put(indexOf(next, previous[position]), pages.valueAt(i));
            }
        }
        pages.clear();
        for (int i = 0; i < moved.size(); i++) {
            pages.put(moved.keyAt(i), moved.valueAt(i));
        }
        order = next;
        int target = indexOf(next, currentKind);
        if (pager != null) {
            pager.setPosition(target);
        }
        if (view != null) {
            view.getState().setPosition(target);
        }
        refreshTabs();
    }

    void refreshOrder() {
        applyOrder(buildOrder());
    }

    // ---- Views ----

    void createViews(Context context, FrameLayout contentView) {
        SkinBridge.install();
        if (view != null) {
            view.dispose();
        }
        menuAnchor = new View(context);
        contentView.addView(menuAnchor, new FrameLayout.LayoutParams(dp(48), dp(48)));
        view = new DockView(context, strings());
        view.setActions(actions);
        SkinBridge.applyTheme(view);
        DockState state = view.getState();
        state.setKeys(Arrays.asList(
                new DockKey(KEY_NEW_CHAT, Glyph.NewChat, LocaleController.getString(R.string.SkinDockNewChat)),
                new DockKey(KEY_NEW_GROUP, Glyph.NewGroup, LocaleController.getString(R.string.SkinDockNewGroup)),
                new DockKey(KEY_NEW_CHANNEL, Glyph.Feed, LocaleController.getString(R.string.SkinDockNewChannel)),
                new DockKey(KEY_SECRET_CHAT, Glyph.Secret, LocaleController.getString(R.string.SkinDockSecretChat))));
        state.setMaxChosen(MAX_MIDDLE);
        ViewPagerFixed pager = pager();
        state.setPosition(pager != null ? pager.getCurrentPosition() : 0);
        contentView.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshTabs();
        if (!observing) {
            observing = true;
            int account = host.getCurrentAccount();
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.reloadHints);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.dialogsUnreadCounterChanged);
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        }
    }

    void destroy() {
        cancelSearch();
        if (observing) {
            observing = false;
            int account = host.getCurrentAccount();
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.reloadHints);
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.dialogsUnreadCounterChanged);
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        }
        if (view != null) {
            view.dispose();
            view = null;
        }
    }

    private DockStrings strings() {
        return new DockStrings(
                LocaleController.getString(R.string.SkinDockCreate),
                LocaleController.getString(R.string.SkinDockClose),
                LocaleController.getString(R.string.SkinDockSearchHint),
                LocaleController.getString(R.string.SkinDockRecent),
                LocaleController.getString(R.string.SkinDockNoRecent),
                LocaleController.getString(R.string.SkinDockSearching),
                LocaleController.getString(R.string.SkinDockNoResults),
                LocaleController.getString(R.string.SkinFanReleaseToSwitch),
                LocaleController.getString(R.string.SkinFanReleaseToAdd),
                LocaleController.getString(R.string.SkinFanReleaseToMore),
                LocaleController.getString(R.string.SkinFanSlideToPick),
                LocaleController.getString(R.string.SkinFanTapToPick),
                LocaleController.getString(R.string.SkinFanAddAccount),
                LocaleController.getString(R.string.SkinFanMoreAccounts),
                LocaleController.getString(R.string.SkinArrangeTitle),
                LocaleController.getString(R.string.SkinArrangeHint),
                LocaleController.getString(R.string.SkinArrangeFull),
                LocaleController.getString(R.string.SkinSwitchAccount),
                LocaleController.getString(R.string.SkinArrangeTitle));
    }

    int avatarVersion() {
        return avatarVersion;
    }

    void onResume() {
        refreshOrder();
        refreshTabs();
    }

    void onThemeChanged() {
        SkinBridge.applyTheme(view);
    }

    void refreshTabs() {
        if (view == null) {
            return;
        }
        ArrayList<DockTab> tabs = new ArrayList<>(order.length);
        for (int i = 0; i < order.length; i++) {
            tabs.add(tab(order[i]));
        }
        view.getState().setTabs(tabs);
    }

    private DockTab tab(int kind) {
        int account = host.getCurrentAccount();
        switch (kind) {
            case TAB_CHATS: {
                String label = SkinBridge.text(R.string.SkinTabChats, R.string.MainTabsChats);
                int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
                if (unread <= 0) {
                    return new DockTab(kind, Glyph.Chats, label, label, null, null, false);
                }
                String description = LocaleController.formatString(R.string.SkinTabUnread, label, LocaleController.formatNumber(unread, ','));
                return new DockTab(kind, Glyph.Chats, label, description, LocaleController.formatShortNumber(unread, null), null, false);
            }
            case TAB_ME: {
                TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
                String name = SkinBridge.userName(user);
                Peer peer = new Peer(account, user != null ? user.id : 0, name, avatarVersion);
                String label = LocaleController.getString(R.string.SkinTabMe);
                return new DockTab(kind, Glyph.Person, label, LocaleController.getString(R.string.SkinTabMeDescription), null, peer, false);
            }
            case TAB_ALL_ACCOUNTS: {
                String label = LocaleController.getString(R.string.SkinTabAllAccounts);
                return new DockTab(kind, Glyph.Inboxes, label, label, null, null, false);
            }
            default: {
                String label = choiceLabel(kind);
                return new DockTab(kind, choiceGlyph(kind), label, label, null, null, true);
            }
        }
    }

    private static Glyph choiceGlyph(int kind) {
        switch (kind) {
            case TAB_CONTACTS: return Glyph.Contacts;
            case TAB_CALLS: return Glyph.Calls;
            default: return Glyph.Checklist;
        }
    }

    private static String choiceLabel(int kind) {
        switch (kind) {
            case TAB_CONTACTS: return SkinBridge.text(R.string.SkinTabContacts, R.string.Contacts);
            case TAB_CALLS: return SkinBridge.text(R.string.SkinTabCalls, R.string.Calls);
            default: return LocaleController.getString(R.string.SkinTabCenter);
        }
    }

    void updateUnread() {
        refreshTabs();
    }

    // ---- Following the pages ----

    void onPagerMoved(float position) {
        if (view == null) {
            return;
        }
        DockState state = view.getState();
        if (Math.round(position) != Math.round(state.getPosition())) {
            state.setCollapsed(false);
            scrolled = 0;
        }
        state.setPosition(position);
    }

    void onPageSettled() {
        ViewPagerFixed pager = pager();
        if (view != null && pager != null) {
            view.getState().setPosition(pager.getCurrentPosition());
        }
    }

    /** Called with the page's own scrolling: down folds the dock, up unfolds it. */
    void onListScrolled(int dyConsumed, int dyUnconsumed) {
        if (view == null) {
            return;
        }
        DockState state = view.getState();
        if (state.isOpen() || isRail()) {
            return;
        }
        if (dyUnconsumed < 0) {
            // Pulled past the top.
            state.setCollapsed(false);
            scrolled = 0;
            return;
        }
        if (dyConsumed == 0) {
            return;
        }
        if ((dyConsumed > 0) != (scrolled > 0)) {
            scrolled = 0;
        }
        scrolled += dyConsumed;
        if (scrolled > dp(48)) {
            state.setCollapsed(true);
        } else if (scrolled < -dp(24)) {
            state.setCollapsed(false);
        }
    }

    /** The chat list hides the dock while it searches. */
    void setShown(boolean shown) {
        if (view != null) {
            view.getState().setShown(shown);
        }
    }

    boolean isRail() {
        View content = host.contentView;
        return content != null && content.getWidth() >= dp(600);
    }

    void onInsets(WindowInsetsCompat insets, int bottom) {
        if (view == null) {
            return;
        }
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        DockState state = view.getState();
        state.setTopInset(bars.top);
        state.setBottomInset(bottom);
        state.setStartInset(LocaleController.isRTL ? bars.right : bars.left);
        state.setImeInset(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
    }

    /** On a wide screen the pages step aside for the rail. */
    void onLayout() {
        if (host.viewPager == null || view == null) {
            return;
        }
        int inset = isRail() ? view.getState().getStartInset() + dp(12 + 76 + 8) : 0;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) host.viewPager.getLayoutParams();
        int left = LocaleController.isRTL ? 0 : inset;
        int right = LocaleController.isRTL ? inset : 0;
        if (lp.leftMargin != left || lp.rightMargin != right) {
            lp.leftMargin = left;
            lp.rightMargin = right;
            host.viewPager.setLayoutParams(lp);
        }
    }

    // ---- Panels ----

    boolean isOpen() {
        return view != null && view.getState().isOpen();
    }

    void close() {
        setMode(DockMode.Rail);
    }

    void openArrange() {
        setMode(DockMode.Arrange);
    }

    void openAccounts() {
        setMode(DockMode.Accounts);
    }

    private void setMode(DockMode mode) {
        if (view == null) {
            return;
        }
        DockState state = view.getState();
        if (state.getMode() != mode) {
            state.setMode(mode);
            if (mode != DockMode.Rail) state.setCollapsed(false);
            actions.onModeChanged(mode);
        }
    }

    private void fillRecents() {
        int account = host.getCurrentAccount();
        MessagesController messages = MessagesController.getInstance(account);
        MediaDataController media = MediaDataController.getInstance(account);
        ArrayList<DockContact> recents = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (int i = 0; i < media.hints.size() && recents.size() < 10; i++) {
            TLRPC.Peer peer = media.hints.get(i).peer;
            if (peer != null && peer.user_id != 0 && seen.add(peer.user_id)) {
                TLRPC.User user = messages.getUser(peer.user_id);
                if (user != null && !UserObject.isDeleted(user)) recents.add(contact(account, user));
            }
        }
        if (recents.isEmpty()) {
            // No top peers yet (a fresh account, or hints still loading): use recent private chats.
            ArrayList<TLRPC.Dialog> dialogs = messages.getDialogs(0);
            for (int i = 0; i < dialogs.size() && recents.size() < 10; i++) {
                long id = dialogs.get(i).id;
                if (id > 0 && seen.add(id)) {
                    TLRPC.User user = messages.getUser(id);
                    if (user != null && !UserObject.isUserSelf(user) && !UserObject.isDeleted(user) && !user.bot) {
                        recents.add(contact(account, user));
                    }
                }
            }
            media.loadHints(true);
        }
        view.getState().setRecents(recents);
    }

    private DockContact contact(int account, TLRPC.User user) {
        String name = UserObject.getFirstName(user);
        boolean online = user.status != null && user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime();
        String handle = SkinBridge.handle(user);
        return new DockContact(new Peer(account, user.id, SkinBridge.userName(user), 0), name, online && !user.bot, handle.isEmpty() ? null : handle);
    }

    private DockContact contact(int account, TLRPC.Chat chat) {
        String handle = TextUtils.isEmpty(chat.username) ? null : "@" + chat.username;
        return new DockContact(new Peer(account, -chat.id, chat.title, 0), chat.title, false, handle);
    }

    private void fillAccounts() {
        int current = host.getCurrentAccount();
        ArrayList<DockAccount> accounts = new ArrayList<>();
        for (int account : accountsInOrder()) {
            TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
            if (user == null) continue;
            String name = SkinBridge.userName(user);
            String handle = SkinBridge.handle(user);
            if (handle.isEmpty()) handle = SkinBridge.maskedPhone(user);
            String meta;
            if (account == current) {
                meta = LocaleController.getString(R.string.SkinFanCurrent);
            } else {
                int unread = MessagesStorage.getInstance(account).getMainUnreadCount();
                meta = unread > 0 ? LocaleController.formatString(R.string.SkinFanUnread, LocaleController.formatNumber(unread, ',')) : "";
            }
            DockAccount item = new DockAccount(account, new Peer(account, user.id, name, avatarVersion), name, handle, meta, account == current);
            if (account == current) accounts.add(0, item); else accounts.add(item);
        }
        DockState state = view.getState();
        state.setAccounts(accounts);
        state.setCanAddAccount(UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT);
    }

    private static int[] accountsInOrder() {
        return UserConfig.getActivatedAccounts();
    }

    private void fillChoices() {
        ArrayList<DockChoice> choices = new ArrayList<>();
        for (int kind : CHOICES) {
            choices.add(new DockChoice(kind, choiceGlyph(kind), choiceLabel(kind)));
        }
        ArrayList<Integer> chosen = new ArrayList<>();
        for (int kind : middle()) chosen.add(kind);
        DockState state = view.getState();
        state.setChoices(choices);
        state.setChosen(chosen);
    }

    // ---- Search in the 起点 panel ----

    private void search(String query) {
        cancelSearch();
        DockState state = view.getState();
        String q = query.trim();
        if (q.isEmpty()) {
            state.setResults(null);
            state.setSearching(false);
            return;
        }
        int account = host.getCurrentAccount();
        ArrayList<DockContact> local = searchContacts(account, q);
        state.setResults(local);
        state.setSearching(true);
        final int generation = ++searchGeneration;
        pendingSearch = () -> {
            pendingSearch = null;
            TLRPC.TL_contacts_search request = new TLRPC.TL_contacts_search();
            request.q = q;
            request.limit = 20;
            searchRequest = ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (generation != searchGeneration || view == null) return;
                searchRequest = 0;
                DockState current = view.getState();
                current.setSearching(false);
                if (!(response instanceof TLRPC.TL_contacts_found)) return;
                TLRPC.TL_contacts_found found = (TLRPC.TL_contacts_found) response;
                MessagesController messages = MessagesController.getInstance(account);
                messages.putUsers(found.users, false);
                messages.putChats(found.chats, false);
                ArrayList<DockContact> merged = new ArrayList<>(local);
                HashSet<Long> seen = new HashSet<>();
                for (DockContact c : local) seen.add(c.getPeer().getId());
                ArrayList<TLRPC.Peer> peers = new ArrayList<>(found.my_results);
                peers.addAll(found.results);
                for (TLRPC.Peer peer : peers) {
                    if (peer.user_id != 0 && seen.add(peer.user_id)) {
                        TLRPC.User user = messages.getUser(peer.user_id);
                        if (user != null) merged.add(contact(account, user));
                    } else {
                        long chatId = peer.channel_id != 0 ? peer.channel_id : peer.chat_id;
                        if (chatId != 0 && seen.add(-chatId)) {
                            TLRPC.Chat chat = messages.getChat(chatId);
                            if (chat != null) merged.add(contact(account, chat));
                        }
                    }
                }
                current.setResults(merged);
            }));
        };
        AndroidUtilities.runOnUIThread(pendingSearch, 300);
    }

    private ArrayList<DockContact> searchContacts(int account, String query) {
        String q = query.toLowerCase();
        String digits = q.replaceAll("[^0-9]", "");
        if (q.startsWith("@")) q = q.substring(1);
        MessagesController messages = MessagesController.getInstance(account);
        ArrayList<TLRPC.TL_contact> contacts = ContactsController.getInstance(account).contacts;
        ArrayList<DockContact> result = new ArrayList<>();
        for (int i = 0; i < contacts.size() && result.size() < 30; i++) {
            TLRPC.User user = messages.getUser(contacts.get(i).user_id);
            if (user == null || UserObject.isUserSelf(user)) continue;
            String name = SkinBridge.userName(user).toLowerCase();
            String username = UserObject.getPublicUsername(user);
            boolean hit = name.contains(q)
                    || (username != null && username.toLowerCase().contains(q))
                    || (digits.length() >= 3 && user.phone != null && user.phone.contains(digits));
            if (hit) result.add(contact(account, user));
        }
        return result;
    }

    private void cancelSearch() {
        searchGeneration++;
        if (pendingSearch != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingSearch);
            pendingSearch = null;
        }
        if (searchRequest != 0) {
            ConnectionsManager.getInstance(host.getCurrentAccount()).cancelRequest(searchRequest, true);
            searchRequest = 0;
        }
    }

    // ---- What the keys do ----

    private boolean frozen() {
        if (MessagesController.getInstance(host.getCurrentAccount()).isFrozen()) {
            AccountFrozenAlert.show(host.getCurrentAccount());
            return true;
        }
        return false;
    }

    private void startSomething(int key) {
        if (frozen()) return;
        switch (key) {
            case KEY_NEW_CHAT: {
                Bundle args = new Bundle();
                args.putBoolean("destroyAfterSelect", true);
                host.presentFragment(new ContactsActivity(args));
                break;
            }
            case KEY_NEW_GROUP:
                host.presentFragment(new GroupCreateActivity(new Bundle()), false);
                break;
            case KEY_NEW_CHANNEL: {
                // First time through, explain channels; after that go straight in.
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    host.presentFragment(new ChannelCreateActivity(args));
                } else {
                    host.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                    preferences.edit().putBoolean("channel_intro", true).commit();
                }
                break;
            }
            case KEY_SECRET_CHAT: {
                Bundle args = new Bundle();
                args.putBoolean("onlyUsers", true);
                args.putBoolean("destroyAfterSelect", true);
                args.putBoolean("createSecretChat", true);
                args.putBoolean("allowBots", false);
                args.putBoolean("allowSelf", false);
                host.presentFragment(new ContactsActivity(args));
                break;
            }
        }
    }

    private void openChat(DockContact contact) {
        long id = contact.getPeer().getId();
        Bundle args = new Bundle();
        if (id > 0) args.putLong("user_id", id); else args.putLong("chat_id", -id);
        if (MessagesController.getInstance(host.getCurrentAccount()).checkCanOpenChat(args, host)) {
            host.presentFragment(new ChatActivity(args));
        }
    }

    private void placeAnchor(float x, float y) {
        if (menuAnchor == null) return;
        menuAnchor.setTranslationX(x - dp(24));
        menuAnchor.setTranslationY(y - dp(24));
    }

    private final DockActions actions = new DockActions() {
        @Override
        public void onTab(int index) {
            ViewPagerFixed pager = pager();
            if (pager == null || pager.isManualScrolling() || pager.isTouch()) {
                return;
            }
            if (pager.getCurrentPosition() == index) {
                BaseFragment fragment = host.getCurrentVisibleFragment();
                if (fragment instanceof MainTabsActivity.TabFragmentDelegate) {
                    ((MainTabsActivity.TabFragmentDelegate) fragment).onParentScrollToTop();
                }
                view.getState().setCollapsed(false);
                return;
            }
            host.viewPager.scrollToPosition(index);
        }

        @Override
        public void onTabMenu(int index, float x, float y) {
            if (kindAt(index) == TAB_CHATS) {
                placeAnchor(x, y);
                host.showReadAllMessagesOptions(menuAnchor);
            }
        }

        @Override
        public void onModeChanged(DockMode mode) {
            if (view == null) return;
            if (mode == DockMode.Create) {
                fillRecents();
            } else if (mode == DockMode.Accounts) {
                fillAccounts();
            } else if (mode == DockMode.Arrange) {
                fillChoices();
            } else {
                cancelSearch();
                view.getState().setResults(null);
                view.getState().setSearching(false);
            }
        }

        @Override
        public void onKey(int id) {
            close();
            startSomething(id);
        }

        @Override
        public void onQuery(String query) {
            if (view != null) search(query);
        }

        @Override
        public void onContact(DockContact contact) {
            close();
            openChat(contact);
        }

        @Override
        public void onAccount(int account) {
            if (LaunchActivity.instance != null && account != host.getCurrentAccount()) {
                LaunchActivity.instance.switchToAccount(account, true);
            }
        }

        @Override
        public void onAddAccount() {
            int free = UserConfig.getFirstFreeAccountIndex();
            if (free >= 0) {
                host.presentFragment(new LoginActivity(free));
            }
        }

        @Override
        public void onMoreAccounts() {
            View content = host.contentView;
            if (content == null) return;
            placeAnchor(LocaleController.isRTL ? dp(60) : content.getWidth() - dp(60), content.getHeight() - dp(140));
            host.openAccountSelector(menuAnchor);
        }

        @Override
        public void onCycleAccount(boolean forward) {
            int[] accounts = accountsInOrder();
            if (accounts.length < 2 || LaunchActivity.instance == null) return;
            int index = Math.max(0, indexOf(accounts, host.getCurrentAccount()));
            int next = accounts[(index + (forward ? 1 : accounts.length - 1)) % accounts.length];
            LaunchActivity.instance.switchToAccount(next, true);
        }

        @Override
        public void onArrange(List<Integer> choices) {
            StringBuilder stored = new StringBuilder();
            for (Integer kind : choices) {
                if (stored.length() > 0) stored.append(',');
                stored.append(kind);
            }
            MessagesController.getGlobalMainSettings().edit().putString(MIDDLE_KEY, stored.toString()).apply();
            refreshOrder();
        }
    };

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (view == null) return;
        if (id == NotificationCenter.mainUserInfoChanged) {
            avatarVersion++;
            refreshTabs();
        } else if (id == NotificationCenter.reloadHints) {
            if (view.getState().getMode() == DockMode.Create) fillRecents();
        } else if (id == NotificationCenter.dialogsUnreadCounterChanged) {
            refreshTabs();
        } else if (id == NotificationCenter.didSetNewTheme) {
            onThemeChanged();
        }
    }

    // ---- Helpers ----

    private ViewPagerFixed pager() {
        return host.viewPager;
    }

    static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return -1;
    }
}

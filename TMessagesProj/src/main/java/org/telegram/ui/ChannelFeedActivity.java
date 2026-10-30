package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;

public class ChannelFeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private static final int ITEM_ID_CHANNEL = 1;
    private static final int ITEM_ID_RELOAD = 2;
    private static final int ITEM_ID_ADD = 3;

    private final ArrayList<ChannelEntry> entries = new ArrayList<>();
    private final ArrayList<Runnable> resolveCancellations = new ArrayList<>();
    private UniversalRecyclerView listView;
    private boolean loading;
    private String errorText;
    private boolean loadedOnce;
    private int loadGeneration;

    @Override
    public View createView(Context context) {
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.ChannelFeedTitle));

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        listView.setClipToPadding(false);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0), 0, 0);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!loadedOnce) {
            loadChannelFeed();
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ITEM_ID_ADD, "+ " + getString(R.string.ChannelFeedAdd)).accent());
        if (loading) {
            for (int i = 0; i < 6; i++) {
                items.add(UItem.asFlicker(i, FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
            return;
        }
        if (!TextUtils.isEmpty(errorText) && entries.isEmpty()) {
            items.add(UItem.asButton(ITEM_ID_RELOAD, "+ " + getString(R.string.ChannelFeedReload)).accent());
            items.add(UItem.asShadow(errorText));
            return;
        }
        if (entries.isEmpty()) {
            items.add(UItem.asButton(ITEM_ID_RELOAD, "+ " + getString(R.string.ChannelFeedReload)).accent());
            items.add(UItem.asShadow(getString(R.string.ChannelFeedEmpty)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            ChannelEntry entry = entries.get(i);
            UItem item = UItem.asProfileCell(entry.getDisplayObject());
            item.id = ITEM_ID_CHANNEL;
            item.object2 = entry;
            item.withUsername = entry.resolvedObject != null;
            items.add(item);
        }
        if (!TextUtils.isEmpty(errorText)) {
            items.add(UItem.asButton(ITEM_ID_RELOAD, "+ " + getString(R.string.ChannelFeedReload)).accent());
            items.add(UItem.asShadow(errorText));
        } else {
            items.add(UItem.asShadow(getString(R.string.ChannelFeedInfo)));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ITEM_ID_ADD) {
            showAddChannelDialog();
        } else if (item.id == ITEM_ID_RELOAD) {
            loadChannelFeed();
        } else if (item.id == ITEM_ID_CHANNEL && item.object2 instanceof ChannelEntry && getParentActivity() != null) {
            ChannelEntry entry = (ChannelEntry) item.object2;
            if (!TextUtils.isEmpty(entry.username)) {
                getMessagesController().openByUserName(entry.username, this, 1);
            } else {
                Browser.openUrl(getParentActivity(), entry.url);
            }
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id != ITEM_ID_CHANNEL || !(item.object2 instanceof ChannelEntry) || getParentActivity() == null) {
            return false;
        }
        ChannelEntry entry = (ChannelEntry) item.object2;
        if (!entry.custom) {
            return false;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourceProvider);
        builder.setTitle(getString(R.string.ChannelFeedRemoveTitle));
        builder.setMessage(entry.title);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            SharedConfig.removeCustomChannelFeedEntry(entry.url);
            loadChannelFeed();
        });
        showDialog(builder.create());
        return true;
    }

    private void showAddChannelDialog() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        editText.setHintText(getString(R.string.ChannelFeedAddHint));
        editText.setSingleLine(true);
        editText.setMaxLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12));
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourceProvider);
        builder.setTitle(getString(R.string.ChannelFeedAddTitle));
        builder.setMessage(getString(R.string.ChannelFeedAddInfo));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Add), (dialog, which) -> {
            ChannelEntry entry = parseEntry(editText.getText().toString(), true);
            if (entry == null) {
                Toast.makeText(context, getString(R.string.ChannelFeedAddInvalid), Toast.LENGTH_SHORT).show();
                return;
            }
            if (SharedConfig.addCustomChannelFeedEntry(entry.url)) {
                Toast.makeText(context, getString(R.string.ChannelFeedAdded), Toast.LENGTH_SHORT).show();
                loadChannelFeed();
            } else {
                Toast.makeText(context, getString(R.string.ChannelFeedAlreadyAdded), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 120);
    }

    private void loadChannelFeed() {
        if (loading) {
            return;
        }
        cancelResolveRequests();
        final int generation = ++loadGeneration;
        loading = true;
        errorText = null;
        refreshList();
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<ChannelEntry> loaded = new ArrayList<>();
            String error = null;
            HttpURLConnection connection = null;
            HashSet<String> seenUrls = new HashSet<>();
            ArrayList<String> customEntries = SharedConfig.getCustomChannelFeedEntries();
            for (int i = 0; i < customEntries.size(); i++) {
                ChannelEntry entry = parseEntry(customEntries.get(i), true);
                if (entry == null || seenUrls.contains(entry.url)) {
                    continue;
                }
                seenUrls.add(entry.url);
                loaded.add(entry);
            }
            try {
                URL url = new URL(SharedConfig.CHANNEL_FEED_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setUseCaches(false);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    ChannelEntry entry = parseEntry(line);
                    if (entry == null || seenUrls.contains(entry.url)) {
                        continue;
                    }
                    seenUrls.add(entry.url);
                    loaded.add(entry);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
                error = getString(R.string.ChannelFeedLoadFailed);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            String finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != loadGeneration) {
                    return;
                }
                entries.clear();
                entries.addAll(loaded);
                loading = false;
                loadedOnce = true;
                errorText = finalError;
                resolveChannelEntries(generation);
                refreshList();
            });
        });
    }

    private void resolveChannelEntries(int generation) {
        for (int i = 0; i < entries.size(); i++) {
            ChannelEntry entry = entries.get(i);
            if (TextUtils.isEmpty(entry.username) || entry.resolvedObject != null || entry.resolving) {
                continue;
            }
            TLObject cachedObject = getMessagesController().getUserOrChat(entry.username);
            if (cachedObject != null) {
                entry.resolvedObject = cachedObject;
                continue;
            }
            entry.resolving = true;
            Runnable cancel = getMessagesController().getUserNameResolver().resolve(entry.username, peerId -> {
                if (generation != loadGeneration) {
                    return;
                }
                entry.resolving = false;
                if (peerId != null) {
                    entry.resolvedObject = getMessagesController().getUserOrChat(peerId);
                }
                refreshList();
            });
            if (cancel != null) {
                resolveCancellations.add(cancel);
            }
        }
    }

    private void cancelResolveRequests() {
        for (int i = 0; i < resolveCancellations.size(); i++) {
            resolveCancellations.get(i).run();
        }
        resolveCancellations.clear();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        cancelResolveRequests();
        loadGeneration++;
    }

    private ChannelEntry parseEntry(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String line = rawLine.replace("\uFEFF", "").trim();
        if (TextUtils.isEmpty(line) || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\s+");
        String url = parts[parts.length - 1].trim();
        if (url.startsWith("@")) {
            url = "https://t.me/" + url.substring(1);
        } else if (url.startsWith("t.me/")) {
            url = "https://" + url;
        }
        if (!url.startsWith("https://t.me/") && !url.startsWith("http://t.me/")) {
            return null;
        }
        String username = extractUsername(url);
        String title = extractTitle(url);
        if (parts.length > 1 && !parts[0].startsWith("http") && !parts[0].startsWith("t.me/") && !parts[0].startsWith("@")) {
            int linkStart = line.lastIndexOf(parts[parts.length - 1]);
            if (linkStart > 0) {
                String customTitle = line.substring(0, linkStart).trim();
                if (!TextUtils.isEmpty(customTitle)) {
                    title = customTitle;
                }
            }
        }
        return new ChannelEntry(title, url, username);
    }

    private ChannelEntry parseEntry(String rawLine, boolean custom) {
        ChannelEntry entry = parseEntry(rawLine);
        if (entry != null) {
            entry.custom = custom;
        }
        return entry;
    }

    private String extractUsername(String url) {
        String cleanUrl = url;
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }
        int fragmentIndex = cleanUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, fragmentIndex);
        }
        cleanUrl = cleanUrl.replace("http://", "").replace("https://", "");
        if (cleanUrl.startsWith("t.me/")) {
            cleanUrl = cleanUrl.substring("t.me/".length());
        } else if (cleanUrl.startsWith("telegram.me/")) {
            cleanUrl = cleanUrl.substring("telegram.me/".length());
        } else {
            return null;
        }
        while (cleanUrl.startsWith("/")) {
            cleanUrl = cleanUrl.substring(1);
        }
        int slashIndex = cleanUrl.indexOf('/');
        if (slashIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, slashIndex);
        }
        if (TextUtils.isEmpty(cleanUrl) || cleanUrl.startsWith("+") || "joinchat".equalsIgnoreCase(cleanUrl) || "c".equalsIgnoreCase(cleanUrl)) {
            return null;
        }
        return cleanUrl;
    }

    private String extractTitle(String url) {
        String cleanUrl = url;
        int queryIndex = cleanUrl.indexOf('?');
        if (queryIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }
        while (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        int slashIndex = cleanUrl.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < cleanUrl.length() - 1) {
            String name = cleanUrl.substring(slashIndex + 1);
            if (!TextUtils.isEmpty(name) && !name.startsWith("+")) {
                return "@" + name;
            }
        }
        return cleanUrl;
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return false;
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        return null;
    }

    private static class ChannelEntry {
        final String title;
        final String url;
        final String username;
        TLObject resolvedObject;
        boolean resolving;
        boolean custom;
        private TLRPC.Chat placeholderChat;

        ChannelEntry(String title, String url, String username) {
            this.title = title;
            this.url = url;
            this.username = username;
        }

        TLObject getDisplayObject() {
            if (resolvedObject != null) {
                return resolvedObject;
            }
            if (placeholderChat == null) {
                TLRPC.TL_channel chat = new TLRPC.TL_channel();
                chat.id = Math.abs(username != null ? username.toLowerCase().hashCode() : url.hashCode()) + 1L;
                chat.title = TextUtils.isEmpty(title) ? url : title;
                chat.username = username;
                chat.broadcast = true;
                placeholderChat = chat;
            }
            return placeholderChat;
        }
    }
}

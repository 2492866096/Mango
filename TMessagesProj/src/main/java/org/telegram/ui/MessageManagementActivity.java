package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageTagsController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class MessageManagementActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {
    private static final int ITEM_FILTER = 1;
    private static final int ITEM_MESSAGE = 2;
    private final boolean inMainTabs;
    private UniversalRecyclerView listView;
    private int filter;
    private int openGeneration;
    private boolean opening;

    public MessageManagementActivity() {
        this(false);
    }

    public MessageManagementActivity(boolean inMainTabs) {
        this.inMainTabs = inMainTabs;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MessageManagementTitle));
        if (!inMainTabs) actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setSections();
        listView.setClipToPadding(false);
        updatePadding(AndroidUtilities.navigationBarHeight);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        actionBar.setAdaptiveBackground(listView);
        if (inMainTabs) ViewCompat.setOnApplyWindowInsetsListener(content, this::onInsetsInternal);
        return fragmentView = content;
    }

    private void updatePadding(int bottomInset) {
        if (listView == null) return;
        int top = inMainTabs ? ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) : 0;
        int bottom = bottomInset + (inMainTabs ? AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0);
        listView.setPadding(0, top, 0, bottom);
    }

    @NonNull
    @Override
    public WindowInsetsCompat onInsetsInternal(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        updatePadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
        return super.onInsetsInternal(v, insets);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    public void onFragmentDestroy() {
        openGeneration++;
        super.onFragmentDestroy();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSettingsCell(ITEM_FILTER, getString(R.string.MessageManagementFilter),
                getString(filter == 0 ? R.string.MessageManagementAll : MessageTagsController.tagName(filter))));
        items.add(UItem.asShadow(getString(R.string.MessageManagementInfo)));
        ArrayList<MessageTagsController.Entry> entries = MessageTagsController.getEntries(currentAccount, filter);
        if (entries.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.MessageManagementEmpty)));
        }
        for (MessageTagsController.Entry entry : entries) {
            String title = entry.title + " · " + MessageTagsController.tagsText(entry.tags);
            UItem item = TextDetailCell.Factory.of(ITEM_MESSAGE, entry.preview, title + " · " + LocaleController.formatDateChat(entry.date));
            item.object = entry;
            items.add(item);
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ITEM_FILTER) {
            CharSequence[] labels = new CharSequence[5];
            labels[0] = getString(R.string.MessageManagementAll);
            for (int i = 0; i < 4; i++) labels[i + 1] = getString(MessageTagsController.tagName(MessageTagsController.TAGS[i]));
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MessageManagementFilter))
                    .setItems(labels, (dialog, which) -> {
                        filter = which == 0 ? 0 : MessageTagsController.TAGS[which - 1];
                        listView.adapter.update(true);
                    }).create());
        } else if (item.id == ITEM_MESSAGE && item.object instanceof MessageTagsController.Entry) {
            openMessage((MessageTagsController.Entry) item.object);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id != ITEM_MESSAGE || !(item.object instanceof MessageTagsController.Entry)) return false;
        MessageTagEditor.show(this, (MessageTagsController.Entry) item.object, () -> listView.adapter.update(true));
        return true;
    }

    private boolean isOpening(int generation) {
        return generation == openGeneration && !isFinished && getParentActivity() != null;
    }

    private void openMessage(MessageTagsController.Entry entry) {
        if (opening || getParentActivity() == null) return;
        opening = true;
        final int generation = ++openGeneration;
        Toast.makeText(getParentActivity(), getString(R.string.MessageManagementLoading), Toast.LENGTH_SHORT).show();
        // Storage helpers are blocking; never invoke them on either the UI or storage queue.
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.Message cached = getMessagesStorage().getMessage(entry.dialogId, entry.messageId);
            TLRPC.Chat chat = entry.dialogId < 0 ? getMessagesStorage().getChatSync(-entry.dialogId) : null;
            TLRPC.User user = entry.dialogId > 0 && !DialogObject.isEncryptedDialog(entry.dialogId) ? getMessagesStorage().getUserSync(entry.dialogId) : null;
            AndroidUtilities.runOnUIThread(() -> {
                if (!isOpening(generation)) return;
                if (chat != null) getMessagesController().putChat(chat, true);
                if (user != null) getMessagesController().putUser(user, true);
                if (cached != null && !(cached instanceof TLRPC.TL_messageEmpty)) {
                    navigateToMessage(entry, generation);
                } else if (DialogObject.isEncryptedDialog(entry.dialogId) || entry.messageId <= 0) {
                    unavailable(false);
                } else {
                    fetchMessage(entry, generation);
                }
            });
        });
    }

    private void fetchMessage(MessageTagsController.Entry entry, int generation) {
        TLRPC.Chat chat = entry.dialogId < 0 ? getMessagesController().getChat(-entry.dialogId) : null;
        TLObject request;
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInputChannel(chat);
            req.id.add(entry.messageId);
            request = req;
        } else {
            TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
            req.id.add(entry.messageId);
            request = req;
        }
        int requestId = getConnectionsManager().sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!isOpening(generation)) return;
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                getMessagesController().putUsers(messages.users, false);
                getMessagesController().putChats(messages.chats, false);
                getMessagesStorage().putUsersAndChats(messages.users, messages.chats, true, true);
                for (TLRPC.Message message : messages.messages) {
                    if (!(message instanceof TLRPC.TL_messageEmpty) && message.id == entry.messageId && MessageObject.getDialogId(message) == entry.dialogId) {
                        navigateToMessage(entry, generation);
                        return;
                    }
                }
                unavailable(false);
            } else {
                boolean missing = error != null && ("MESSAGE_ID_INVALID".equals(error.text) || "CHANNEL_PRIVATE".equals(error.text) || "CHAT_ID_INVALID".equals(error.text));
                unavailable(!missing);
            }
        }));
        getConnectionsManager().bindRequestToGuid(requestId, classGuid);
    }

    private void navigateToMessage(MessageTagsController.Entry entry, int generation) {
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(entry.dialogId)) args.putInt("enc_id", DialogObject.getEncryptedChatId(entry.dialogId));
        else if (entry.dialogId < 0) args.putLong("chat_id", -entry.dialogId);
        else args.putLong("user_id", entry.dialogId);
        args.putInt("message_id", entry.messageId);
        if (!getMessagesController().checkCanOpenChat(args, this)) {
            opening = false;
            return;
        }
        if (entry.topicId != 0 && entry.dialogId == getUserConfig().getClientUserId()) {
            args.putInt("chatMode", ChatActivity.MODE_SAVED);
            ChatActivity chat = new ChatActivity(args);
            chat.setCurrentAccount(currentAccount);
            chat.setSavedDialog(entry.topicId);
            opening = false;
            if (!presentFragment(chat)) unavailable(false);
            return;
        }
        if (entry.topicId != 0 && entry.topicId != -1 && !DialogObject.isEncryptedDialog(entry.dialogId)) {
            TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(-entry.dialogId, entry.topicId);
            if (topic == null || topic.topicStartMessage == null) {
                ArrayList<TLRPC.TL_forumTopic> topics = new ArrayList<>();
                topic = new TLRPC.TL_forumTopic();
                topic.id = (int) entry.topicId;
                if (getMessagesController().isMonoForum(entry.dialogId)) topic.from_id = getMessagesController().getPeer(entry.topicId);
                topics.add(topic);
                // The existing topic loader does not invoke callbacks on failure.
                AndroidUtilities.runOnUIThread(() -> {
                    if (isOpening(generation) && opening) {
                        openGeneration++;
                        unavailable(true);
                    }
                }, 15000);
                getMessagesController().getTopicsController().reloadTopics(-entry.dialogId, topics, () -> {
                    if (!isOpening(generation)) return;
                    openTopicMessage(entry, args);
                });
                return;
            }
            openTopicMessage(entry, args);
            return;
        }
        opening = false;
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(currentAccount);
        if (!presentFragment(chat)) unavailable(false);
    }

    private void openTopicMessage(MessageTagsController.Entry entry, Bundle args) {
        TLRPC.TL_forumTopic topic = getMessagesController().getTopicsController().findTopic(-entry.dialogId, entry.topicId);
        if (topic == null || topic.topicStartMessage == null) {
            unavailable(false);
            return;
        }
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(currentAccount);
        ForumUtilities.applyTopic(chat, org.telegram.messenger.MessagesStorage.TopicKey.of(entry.dialogId, entry.topicId));
        chat.highlightMessageId = entry.messageId;
        opening = false;
        if (!presentFragment(chat)) unavailable(false);
    }

    private void unavailable(boolean networkError) {
        opening = false;
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.MessageManagementTitle))
                .setMessage(getString(networkError ? R.string.MessageManagementNetworkError : R.string.MessageManagementUnavailable))
                .setPositiveButton(getString(R.string.OK), null).create());
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent event, boolean forward) {
        return true;
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) listView.smoothScrollToPosition(0);
    }
}

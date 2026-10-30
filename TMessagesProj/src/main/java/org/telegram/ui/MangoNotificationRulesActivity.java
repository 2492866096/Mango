package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.FrameLayout;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.*;
import java.util.ArrayList;

public class MangoNotificationRulesActivity extends BaseFragment {
    private final long dialogId;
    private UniversalRecyclerView list;
    public MangoNotificationRulesActivity() { this(0); }
    public MangoNotificationRulesActivity(long dialogId) { this.dialogId = dialogId; }

    @Override public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoNotificationRulesTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        list = new UniversalRecyclerView(this, this::fill, this::click, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(list);
        return fragmentView = frame;
    }
    private String modeName(int mode) {
        int[] names = {R.string.MangoNotifyAll, R.string.MangoNotifyMentions, R.string.MangoNotifyKeywords, R.string.MangoNotifyEither};
        return getString(mode < 0 ? R.string.MangoNotifyInherit : names[mode]);
    }
    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        String title = dialogId == 0 ? getString(R.string.MangoNotifyGlobal) : Long.toString(dialogId);
        if (dialogId > 0 && getMessagesController().getUser(dialogId) != null)
            title = UserObject.getUserName(getMessagesController().getUser(dialogId));
        else if (dialogId < 0 && getMessagesController().getChat(-dialogId) != null)
            title = getMessagesController().getChat(-dialogId).title;
        items.add(UItem.asHeader(title));
        items.add(UItem.asSettingsCell(1, getString(R.string.MangoNotifyMode), modeName(MangoNotificationRules.ownMode(currentAccount, dialogId))));
        items.add(UItem.asButton(2, getString(R.string.MangoNotifyKeywordsEdit)));
        items.add(UItem.asShadow(getString(R.string.MangoNotifyInfo) + "\n\n" + MangoNotificationRules.keywords(currentAccount, dialogId)));
        if (dialogId == 0) items.add(UItem.asButton(3, getString(R.string.MangoNotifyChatRule)));
    }
    private void click(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            int first = dialogId == 0 ? 0 : -1;
            CharSequence[] choices = new CharSequence[4 - first];
            for (int i = 0; i < choices.length; i++) choices[i] = modeName(i + first);
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MangoNotifyMode)).setItems(choices, (d, which) -> {
                        MangoNotificationRules.setMode(currentAccount, dialogId, which + first); list.adapter.update(true);
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (item.id == 2) {
            EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
            input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack)); input.setTextSize(16);
            input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16200)}); input.setMinLines(4); input.setMaxLines(10);
            input.setText(MangoNotificationRules.keywords(currentAccount, dialogId));
            showDialog(new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.MangoNotifyKeywordsEdit)).setMessage(getString(R.string.MangoNotifyKeywordInfo)).setView(input)
                    .setPositiveButton(getString(R.string.Save), (d, w) -> {
                        MangoNotificationRules.setKeywords(currentAccount, dialogId, input.getText().toString()); list.adapter.update(true);
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (item.id == 3) {
            Bundle args = new Bundle(); args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            args.putBoolean("checkCanWrite", false); args.putBoolean("allowSwitchAccount", false);
            args.putBoolean("allowGroups", true); args.putBoolean("allowChannels", true); args.putBoolean("allowUsers", true); args.putBoolean("allowBots", true);
            args.putBoolean("canSelectTopics", false);
            DialogsActivity picker = new DialogsActivity(args); picker.setCurrentAccount(currentAccount);
            picker.setDelegate((fragment, dids, text, param, notify, date, repeat, topics) -> {
                if (dids == null || dids.size() != 1) return false;
                MangoNotificationRulesActivity target = new MangoNotificationRulesActivity(dids.get(0).dialogId);
                target.setCurrentAccount(currentAccount); fragment.presentFragment(target, true); return true;
            });
            presentFragment(picker);
        }
    }
}

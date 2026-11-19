package org.telegram.ui;

import android.os.Bundle;
import android.widget.Toast;

import org.telegram.messenger.DailyMessageTasksManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

final class ExtensionAutomationTargetPicker {
    interface Callback {
        void onSelected(long dialogId, String title);
    }

    static void open(BaseFragment parent, int account, boolean allowUsers, Callback callback) {
        open(parent, account, allowUsers, false, callback);
    }

    static void openGroups(BaseFragment parent, int account, Callback callback) {
        open(parent, account, false, true, callback);
    }

    private static boolean isAllowed(int account, long dialogId, boolean allowUsers, boolean groupsOnly) {
        return (!groupsOnly || dialogId < 0) && DailyMessageTasksManager.isValidTarget(account, dialogId, allowUsers);
    }

    private static void open(BaseFragment parent, int account, boolean allowUsers, boolean groupsOnly, Callback callback) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("allowGroups", true);
        args.putBoolean("allowChannels", false);
        args.putBoolean("allowUsers", allowUsers);
        args.putBoolean("allowBots", !groupsOnly);
        args.putBoolean("checkCanWrite", true);
        args.putBoolean("allowGlobalSearch", false);
        args.putBoolean("allowSwitchAccount", false);
        args.putBoolean("canSelectTopics", false);
        DialogsActivity picker = new DialogsActivity(args) {
            @Override
            public ArrayList<TLRPC.Dialog> getDialogsArray(int currentAccount, int dialogsType, int folderId, boolean frozen) {
                ArrayList<TLRPC.Dialog> result = new ArrayList<>();
                for (TLRPC.Dialog dialog : super.getDialogsArray(currentAccount, DialogsActivity.DIALOGS_TYPE_DEFAULT, folderId, frozen)) {
                    if (dialog != null && isAllowed(account, dialog.id, allowUsers, groupsOnly)) {
                        result.add(dialog);
                    }
                }
                return result;
            }
        };
        picker.setCurrentAccount(account);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, repeat, topicsFragment) -> {
            if (dids == null || dids.size() != 1 || !isAllowed(account, dids.get(0).dialogId, allowUsers, groupsOnly)) {
                if (fragment.getParentActivity() != null) {
                    Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.ExtensionAutomationSelectOne), Toast.LENGTH_SHORT).show();
                }
                return false;
            }
            long dialogId = dids.get(0).dialogId;
            String title = dialogId > 0 ? UserObject.getUserName(fragment.getMessagesController().getUser(dialogId))
                    : fragment.getMessagesController().getChat(-dialogId).title;
            callback.onSelected(dialogId, title);
            fragment.finishFragment();
            return true;
        });
        parent.presentFragment(picker);
    }
}

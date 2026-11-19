package org.telegram.ui.Components;

import android.os.Bundle;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

public final class MangoScamUserWarning {
    public static boolean show(BaseFragment fragment, TLRPC.User user) {
        return show(fragment, user, null);
    }

    public static boolean show(BaseFragment fragment, TLRPC.User user, Runnable onDismiss) {
        if (fragment == null || fragment.isFinished || user == null || user.id <= 0 || fragment.getParentActivity() == null) {
            return false;
        }
        String username = UserObject.getPublicUsername(user);
        String name = username == null || username.isEmpty() ? UserObject.getUserName(user) : "@" + username;
        if (name == null || name.isEmpty()) name = Long.toString(user.id);
        return fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MangoScamUsersWarningTitle))
                .setMessage(LocaleController.formatString(R.string.MangoScamUsersWarning, name, Long.toString(user.id)))
                .setNegativeButton(LocaleController.getString(R.string.Close), null)
                .setPositiveButton(LocaleController.getString(R.string.MangoScamUsersProfile), (d, w) -> {
                    if (fragment.isFinished || fragment.getParentActivity() == null || fragment.getParentLayout() == null) {
                        return;
                    }
                    // The scam list can match a sender before Telegram has cached its full user object.
                    // ProfileActivity requires the cached object for its initial load; never pass a
                    // synthetic user with only an id, as that leaves the profile in an invalid state.
                    TLRPC.User currentUser = MessagesController.getInstance(fragment.getCurrentAccount()).getUser(user.id);
                    if (currentUser == null || currentUser instanceof TLRPC.TL_userEmpty || UserObject.isDeleted(currentUser)) {
                        MessagesController.getInstance(fragment.getCurrentAccount()).reloadUser(user.id);
                        return;
                    }
                    Bundle args = new Bundle(); args.putLong("user_id", user.id);
                    ProfileActivity profile = new ProfileActivity(args); profile.setCurrentAccount(fragment.getCurrentAccount());
                    fragment.presentFragment(profile);
                }).create(), onDismiss == null ? null : dialog -> onDismiss.run()) != null;
    }

    /** Same warning surface for broadcast channels listed by the Mango risk feed. */
    public static boolean showChannel(BaseFragment fragment, TLRPC.Chat chat, Runnable onDismiss) {
        if (fragment == null || fragment.isFinished || chat == null || chat.id <= 0 || fragment.getParentActivity() == null) {
            return false;
        }
        String username = ChatObject.getPublicUsername(chat);
        String name = username == null || username.isEmpty() ? chat.title : "@" + username;
        if (name == null || name.isEmpty()) name = Long.toString(chat.id);
        return fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MangoScamChannelWarningTitle))
                .setMessage(LocaleController.formatString(R.string.MangoScamChannelWarning, name, Long.toString(chat.id)))
                .setNegativeButton(LocaleController.getString(R.string.Close), null)
                .setPositiveButton(LocaleController.getString(R.string.MangoScamChannelProfile), (d, w) -> {
                    if (!fragment.isFinished && fragment.getParentActivity() != null && fragment.getParentLayout() != null) {
                        Bundle args = new Bundle(); args.putLong("chat_id", chat.id);
                        ProfileActivity profile = new ProfileActivity(args); profile.setCurrentAccount(fragment.getCurrentAccount());
                        fragment.presentFragment(profile);
                    }
                }).create(), onDismiss == null ? null : dialog -> onDismiss.run()) != null;
    }
    private MangoScamUserWarning() {}
}

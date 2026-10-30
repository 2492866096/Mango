package org.telegram.messenger;

import android.os.Looper;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

/** Confirmation belongs to one send action, never to a time window or a recipient. */
public final class MangoSendGuard {
    private static final ThreadLocal<Boolean> confirmed = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> largeFileConfirmed = new ThreadLocal<>();

    private MangoSendGuard() {}

    public static boolean isConfirmed() {
        return Boolean.TRUE.equals(confirmed.get());
    }

    /** Explicit bypass for an already confirmed action or an opted-in automation. */
    public static void runConfirmed(Runnable action) {
        Boolean old = confirmed.get();
        confirmed.set(true);
        try {
            action.run();
        } finally {
            if (old == null) confirmed.remove(); else confirmed.set(old);
        }
    }

    public static Runnable carryConfirmation(Runnable action) {
        boolean approved = isConfirmed();
        boolean largeApproved = isLargeFileConfirmed();
        return () -> {
            Runnable next = largeApproved ? () -> runLargeFileConfirmed(action) : action;
            if (approved) runConfirmed(next); else next.run();
        };
    }

    public static boolean isLargeFileConfirmed() {
        return Boolean.TRUE.equals(largeFileConfirmed.get());
    }

    public static void runLargeFileConfirmed(Runnable action) {
        Boolean old = largeFileConfirmed.get();
        largeFileConfirmed.set(true);
        try {
            action.run();
        } finally {
            if (old == null) largeFileConfirmed.remove(); else largeFileConfirmed.set(old);
        }
    }

    public static boolean isBotCommand(CharSequence text) {
        return text != null && text.toString().trim().matches("(?s)^/[A-Za-z0-9_]+(?:@[A-Za-z0-9_]+)?(?:\\s.*)?$");
    }

    /** Returns true when the action was intercepted. Cancellation never executes it. */
    public static boolean confirm(int account, long dialogId, boolean voice, boolean botCommand, Runnable action) {
        if (isConfirmed()) return false;
        boolean bot = botCommand && MangoSettings.getBoolean(account, "confirm_bot_command");
        boolean audio = voice && MangoSettings.getBoolean(account, "confirm_voice");
        if (!bot && !audio && !MangoSettings.getBoolean(account, "confirm_send")) return false;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!confirm(account, dialogId, voice, botCommand, action)) action.run();
            });
            return true;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) return true;
        String recipient = Long.toString(dialogId);
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user != null) recipient = UserObject.getUserName(user);
        } else if (!DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null) recipient = chat.title;
        }
        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(bot ? R.string.MangoConfirmBotTitle : audio ? R.string.MangoConfirmVoiceTitle : R.string.MangoConfirmSendTitle))
                .setMessage(LocaleController.formatString(R.string.MangoConfirmSendRecipient, recipient))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Send), (dialog, which) -> runConfirmed(action))
                .show();
        return true;
    }
}

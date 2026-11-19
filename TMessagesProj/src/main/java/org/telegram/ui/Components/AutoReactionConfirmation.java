package org.telegram.ui.Components;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/** The manual enable path shared by the switch and settings-history restoration. */
public final class AutoReactionConfirmation {
    private AutoReactionConfirmation() {}

    public static void show(BaseFragment fragment, Runnable onEnabled) {
        if (fragment.getParentActivity() == null || fragment.isFinished) return;
        final int account = fragment.getCurrentAccount();
        final long owner = UserConfig.getInstance(account).getClientUserId();
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.MangoReactionConfirmTitle))
                .setMessage(LocaleController.getString(R.string.MangoReactionConfirmInfo))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Enable), (dialog, which) -> {
                    if (fragment.isFinished || fragment.getCurrentAccount() != account
                            || !UserConfig.getInstance(account).isClientActivated()
                            || UserConfig.getInstance(account).getClientUserId() != owner) return;
                    SharedConfig.setAutoReactionEnabled(true);
                    if (onEnabled != null) onEnabled.run();
                }).create());
    }
}

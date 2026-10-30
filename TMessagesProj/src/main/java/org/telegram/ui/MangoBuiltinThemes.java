package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/** Fixed theme shortcuts that reuse Telegram's native import, preview and apply flow. */
public final class MangoBuiltinThemes {
    private static final String[] SLUGS = {
            "GhX6bJqa8WHT2A9e", "XKXYD2evQUEJ9wny", "LHDGwCahO1LpvN2l"
    };

    private MangoBuiltinThemes() {}

    public static void show(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
        CharSequence[] options = new CharSequence[SLUGS.length];
        for (int i = 0; i < options.length; i++) {
            options[i] = LocaleController.formatString(R.string.MangoBuiltinThemeOption, i + 1);
        }
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.MangoBuiltinThemes))
                .setMessage(getString(R.string.MangoBuiltinThemesInfo))
                .setItems(options, (dialog, which) -> AndroidUtilities.runOnUIThread(() -> open(fragment, which)))
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private static void open(BaseFragment fragment, int index) {
        if (fragment.getParentActivity() == null || index < 0 || index >= SLUGS.length) return;
        int connection = ConnectionsManager.getInstance(fragment.getCurrentAccount()).getConnectionState();
        if (connection != ConnectionsManager.ConnectionStateConnected
                && connection != ConnectionsManager.ConnectionStateUpdating) {
            fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                    .setTitle(getString(R.string.MangoBuiltinThemes))
                    .setMessage(getString(R.string.MangoBuiltinThemesOffline))
                    .setPositiveButton(getString(R.string.OK), null).create());
            return;
        }
        // t.me/addtheme is recognized by Browser as an internal LaunchActivity intent.
        Browser.openUrl(fragment.getParentActivity(), "https://t.me/addtheme/" + SLUGS[index]);
    }
}

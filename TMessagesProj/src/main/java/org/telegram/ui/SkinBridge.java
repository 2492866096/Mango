package org.telegram.ui;

import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.skin.SkinHostView;
import org.telegram.skin.motion.SkinMotion;
import org.telegram.skin.ui.SkinHost;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.Locale;

/**
 * The thin layer the skin reads Telegram through: theme and language, words,
 * faces. Screens built on the skin go through here (and through SkinDock and
 * SkinMeActivity for their own data) rather than reaching into the engine, so
 * an upstream change touches one place.
 */
public final class SkinBridge {
    private static boolean installed;

    private SkinBridge() {
    }

    /** Hands the skin a way to draw real avatars, and what its motion follows. Safe to call more than once. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        SkinMotion.install(ApplicationLoader.applicationContext, SkinBridge::chatAnimationsOff);
        LiteMode.addOnPowerSaverAppliedListener(applied -> SkinMotion.refresh());
        SkinHost.setAvatarViews((context, account, peerId) -> {
            BackupImageView view = new BackupImageView(context);
            view.getImageReceiver().setCurrentAccount(account);
            TLObject peer = findPeer(account, peerId);
            if (peer != null) {
                AvatarDrawable placeholder = new AvatarDrawable();
                placeholder.setInfo(account, peer);
                view.setForUserOrChat(peer, placeholder);
            }
            return view;
        });
    }

    private static TLObject findPeer(int account, long peerId) {
        if (peerId < 0) {
            return MessagesController.getInstance(account).getChat(-peerId);
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(peerId);
        if (user == null && peerId == UserConfig.getInstance(account).getClientUserId()) {
            // Other accounts' own users may not be loaded into their controllers yet.
            user = UserConfig.getInstance(account).getCurrentUser();
        }
        return user;
    }

    /**
     * Telegram's "Animations in Chats" switch, read the way its power saving
     * page shows it (on tablets the two-column forum is not part of it). Battery
     * saving turns the whole group off.
     */
    private static boolean chatAnimationsOff() {
        int flags = LiteMode.FLAGS_CHAT;
        if (AndroidUtilities.isTablet()) {
            flags &= ~LiteMode.FLAG_CHAT_FORUM_TWOCOLUMN;
        }
        return !LiteMode.isEnabled(flags);
    }

    /** Skin screens follow Telegram's theme and language, not the system's. */
    public static void applyTheme(SkinHostView view) {
        if (view != null) {
            view.setTheme(Theme.isCurrentThemeDark(), LocaleController.isRTL);
        }
    }

    /**
     * Our own wording in English and Chinese; in any other language, Telegram's
     * string for the same thing, which its language packs translate.
     */
    public static String text(int ours, int telegram) {
        if (telegram != 0 && !speaksOurLanguages()) {
            return LocaleController.getString(telegram);
        }
        return LocaleController.getString(ours);
    }

    private static boolean speaksOurLanguages() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        String language = locale != null ? locale.getLanguage() : "en";
        return "zh".equals(language) || "en".equals(language);
    }

    public static String userName(TLRPC.User user) {
        return user == null ? "" : UserObject.getUserName(user);
    }

    /** "@name" when the user has a public username, else empty. */
    public static String handle(TLRPC.User user) {
        String username = user == null ? null : UserObject.getPublicUsername(user);
        return TextUtils.isEmpty(username) ? "" : "@" + username;
    }

    /**
     * The phone number with the four digits before the last four hidden, the
     * way it reads on a card someone might glance over: +86 138 **** 2046.
     */
    public static String maskedPhone(TLRPC.User user) {
        if (user == null || TextUtils.isEmpty(user.phone)) {
            return "";
        }
        String formatted = PhoneFormat.getInstance().format("+" + user.phone);
        int digits = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) digits++;
        }
        int from = digits - 8;
        int to = digits - 4;
        StringBuilder out = new StringBuilder(formatted.length());
        int seen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            char ch = formatted.charAt(i);
            if (Character.isDigit(ch)) {
                out.append(seen >= from && seen < to ? '*' : ch);
                seen++;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    public static boolean isConnected(int account) {
        return ConnectionsManager.getInstance(account).getConnectionState() == ConnectionsManager.ConnectionStateConnected;
    }

    /** The connection state in words, for the account card. */
    public static String connectionStatus(int account) {
        switch (ConnectionsManager.getInstance(account).getConnectionState()) {
            case ConnectionsManager.ConnectionStateConnected:
                return LocaleController.getString(R.string.SkinStatusOnline);
            case ConnectionsManager.ConnectionStateWaitingForNetwork:
                return LocaleController.getString(R.string.SkinStatusWaiting);
            case ConnectionsManager.ConnectionStateConnectingToProxy:
                return LocaleController.getString(R.string.SkinStatusProxy);
            case ConnectionsManager.ConnectionStateUpdating:
                return LocaleController.getString(R.string.SkinStatusUpdating);
            default:
                return LocaleController.getString(R.string.SkinStatusConnecting);
        }
    }
}

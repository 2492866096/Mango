package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/** Account-scoped controls shared by message avatars and media viewers. */
public final class MangoVisualPreferences {
    public static final String SHOW_ONLINE_BADGE = "show_online_badge";
    public static final String DISABLE_TAP_MEDIA_SWITCH = "disable_tap_media_switch";

    private MangoVisualPreferences() {}

    public static boolean isTapMediaSwitchDisabled(int account) {
        return MangoSettings.getBoolean(account, DISABLE_TAP_MEDIA_SWITCH, false);
    }

    /** Server-time expiry, or zero when the avatar has no currently visible online state. */
    public static long getOnlineUntil(int account, TLRPC.User avatarUser) {
        if (!MangoSettings.getBoolean(account, SHOW_ONLINE_BADGE, true) || avatarUser == null) {
            return 0;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(avatarUser.id);
        if (user == null) {
            user = avatarUser;
        }
        if (user.id <= 0 || UserObject.isDeleted(user) || UserObject.isUserSelf(user)
                || user.id == UserConfig.getInstance(account).getClientUserId() || user.bot
                || UserObject.isService(user.id) || UserObject.isReplyUser(user) || UserObject.isAnonymous(user)
                || user.id == UserObject.VERIFY || user.id == UserObject.OAUTH || MessagesController.isSupportUser(user)
                || user.status == null) {
            return 0;
        }
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        if (user.status.expires > now) {
            return user.status.expires;
        }
        // Telegram exposes a brief online state after activity even when exact last-seen is private.
        // Do not infer online from a coarse recently/last-week status alone.
        if (user.status.expires <= 0) {
            Integer seen = controller.onlinePrivacy.get(user.id);
            if (seen != null && (long) seen + 30 >= now) {
                return (long) seen + 31;
            }
        }
        return 0;
    }
}

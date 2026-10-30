package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

/**
 * Local-only author notification filters. The server is never told which
 * author is starred; the value is used only by the local notification path.
 */
public final class StarredAuthorManager {

    private static final String PREFS_PREFIX = "starred_authors_";
    private static final String USER_PREFIX = "u:";
    private static final String SIGNATURE_PREFIX = "s:";

    private StarredAuthorManager() {
    }

    private static SharedPreferences getPreferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_PREFIX + account, Context.MODE_PRIVATE);
    }

    private static String getKey(long dialogId) {
        return "author_" + dialogId;
    }

    public static boolean hasStarredAuthor(int account, long dialogId) {
        return !TextUtils.isEmpty(getPreferences(account).getString(getKey(dialogId), null));
    }

    public static void setStarredUser(int account, long dialogId, long userId) {
        if (dialogId == 0 || userId == 0) {
            return;
        }
        getPreferences(account).edit().putString(getKey(dialogId), USER_PREFIX + userId).apply();
    }

    public static void setStarredSignature(int account, long dialogId, String signature) {
        if (dialogId == 0 || TextUtils.isEmpty(signature)) {
            return;
        }
        getPreferences(account).edit().putString(getKey(dialogId), SIGNATURE_PREFIX + signature.trim()).apply();
    }

    public static void clear(int account, long dialogId) {
        getPreferences(account).edit().remove(getKey(dialogId)).apply();
    }

    public static long getAuthorUserId(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        TLRPC.Peer peer = messageObject.messageOwner.from_id;
        return peer instanceof TLRPC.TL_peerUser ? peer.user_id : 0;
    }

    public static String getAuthorSignature(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        String signature = messageObject.messageOwner.post_author;
        if (TextUtils.isEmpty(signature) && messageObject.messageOwner.fwd_from != null) {
            signature = messageObject.messageOwner.fwd_from.post_author;
        }
        return TextUtils.isEmpty(signature) ? null : signature.trim();
    }

    public static boolean isMessageFromStarredAuthor(int account, MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        String value = getPreferences(account).getString(getKey(messageObject.getDialogId()), null);
        if (TextUtils.isEmpty(value)) {
            return true;
        }
        if (value.startsWith(USER_PREFIX)) {
            try {
                return getAuthorUserId(messageObject) == Long.parseLong(value.substring(USER_PREFIX.length()));
            } catch (Exception ignore) {
                return false;
            }
        }
        if (value.startsWith(SIGNATURE_PREFIX)) {
            return TextUtils.equals(value.substring(SIGNATURE_PREFIX.length()), getAuthorSignature(messageObject));
        }
        return false;
    }
}

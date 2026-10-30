package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** User-owned search shortcuts, independent of recent history and server dialog pins. */
public final class MangoPinnedSearchChannels {
    private MangoPinnedSearchChannels() {}

    private static boolean isOwner(int account, long owner) {
        return owner != 0 && UserConfig.getInstance(account).isClientActivated()
                && UserConfig.getInstance(account).getClientUserId() == owner;
    }

    private static SharedPreferences preferences(int account, long owner) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mango_pinned_search_" + account + "_" + owner, Context.MODE_PRIVATE);
    }

    public static boolean canPin(TLRPC.Chat chat) {
        return chat != null && chat.id > 0 && ChatObject.isChannelAndNotMegaGroup(chat);
    }

    public static synchronized ArrayList<TLRPC.Chat> load(int account, long owner) {
        ArrayList<TLRPC.Chat> result = new ArrayList<>();
        if (!isOwner(account, owner)) return result;
        SharedPreferences prefs = preferences(account, owner);
        HashSet<Long> seen = new HashSet<>();
        for (String item : prefs.getString("order", "").split(",")) {
            if (item.isEmpty()) continue;
            SerializedData data = null;
            try {
                long id = Long.parseLong(item);
                String encoded = prefs.getString("chat_" + id, null);
                if (encoded == null || !seen.add(id)) continue;
                data = new SerializedData(Base64.decode(encoded, Base64.NO_WRAP));
                TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(true), true);
                if (canPin(chat) && chat.id == id) result.add(chat);
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (data != null) data.cleanup();
            }
        }
        return result;
    }

    public static synchronized boolean setPinned(int account, long owner, TLRPC.Chat chat, boolean pinned) {
        if (!isOwner(account, owner) || !canPin(chat)) return false;
        SharedPreferences prefs = preferences(account, owner);
        SharedPreferences.Editor editor = prefs.edit();
        ArrayList<TLRPC.Chat> channels = load(account, owner);
        channels.removeIf(item -> item.id == chat.id);
        if (pinned) {
            SerializedData data = new SerializedData();
            try {
                chat.serializeToStream(data);
                editor.putString("chat_" + chat.id, Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP));
            } catch (Exception e) {
                FileLog.e(e);
                return false;
            } finally {
                data.cleanup();
            }
            channels.add(0, chat);
        } else {
            editor.remove("chat_" + chat.id);
        }
        StringBuilder order = new StringBuilder();
        for (TLRPC.Chat channel : channels) {
            if (order.length() > 0) order.append(',');
            order.append(channel.id);
        }
        if (!isOwner(account, owner)) return false;
        editor.putString("order", order.toString()).apply();
        return true;
    }

    public static boolean matches(TLRPC.Chat chat, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) return true;
        if (chat.title != null && chat.title.toLowerCase(Locale.ROOT).contains(normalized)) return true;
        if (chat.username != null && chat.username.toLowerCase(Locale.ROOT).contains(normalized)) return true;
        if (chat.usernames != null) {
            for (TLRPC.TL_username username : chat.usernames) {
                if (username.active && username.username != null
                        && username.username.toLowerCase(Locale.ROOT).contains(normalized)) return true;
            }
        }
        return false;
    }
}

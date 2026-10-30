package org.telegram.messenger;

/** Preferences shared by the extension settings screen and media playback. */
public final class ExtensionMediaSettings {
    private static final String AUTO_PLAY_NEXT = "extension_audio_auto_play_next";

    private ExtensionMediaSettings() {
    }

    public static boolean isAutoPlayNextEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(AUTO_PLAY_NEXT, true);
    }

    public static void setAutoPlayNextEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(AUTO_PLAY_NEXT, enabled).apply();
    }
}

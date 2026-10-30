package org.telegram.messenger;

import java.util.List;

/** Eligibility shared by the inline player and the full-screen video's next-item queue. */
public final class MediaAutoNextQueue {
    private MediaAutoNextQueue() {
    }

    public static boolean isPlayableMessage(MessageObject message) {
        return message != null && message.messageOwner != null && !message.deleted
                && (message.isVoice() || message.isRoundVideo() || message.isVideo())
                && !message.isVoiceOnce() && !message.isRoundOnce()
                && !message.shouldEncryptPhotoOrVideo() && !message.isHiddenSensitive()
                && !message.isSponsored();
    }

    /** Follow the viewer's displayed order, skip photos and never wrap to an earlier video. */
    public static int findNextVideoIndex(List<MessageObject> messages, int currentIndex) {
        if (messages == null || currentIndex < 0 || currentIndex >= messages.size()) {
            return -1;
        }
        MessageObject current = messages.get(currentIndex);
        if (!isPlayableMessage(current) || !current.isVideo()) {
            return -1;
        }
        for (int i = currentIndex + 1; i < messages.size(); i++) {
            MessageObject next = messages.get(i);
            if (isPlayableMessage(next) && next.isVideo()
                    && next.currentAccount == current.currentAccount
                    && next.getDialogId() == current.getDialogId()
                    && next.getId() != current.getId()) {
                return i;
            }
        }
        return -1;
    }
}

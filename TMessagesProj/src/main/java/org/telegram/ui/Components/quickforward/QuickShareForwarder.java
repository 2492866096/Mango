package org.telegram.ui.Components.quickforward;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

/** Account-safe send path for the quick-forward gesture. */
public final class QuickShareForwarder {
    public static final int NOT_SENT = -1;

    private QuickShareForwarder() { }

    /**
     * Sends through the destination account only. Telegram file references are
     * scoped to their source account, so cross-account forwards are copied as
     * text or uploaded from an existing local photo/document file.
     */
    public static int send(QuickShareDestination destination, ArrayList<MessageObject> messages) {
        if (destination == null || destination.dialogId == 0 || messages == null || messages.isEmpty()) {
            return NOT_SENT;
        }

        final int sourceAccount = messages.get(0).currentAccount;
        boolean sameSourceAccount = true;
        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.currentAccount != sourceAccount) {
                return NOT_SENT;
            }
            // Cross-account media forwarding may upload a cached file rather than
            // issue a Telegram forward request. Preserve the source chat's
            // no-forward restrictions before taking either send path.
            if (message.messageOwner.noforwards
                    || MessagesController.getInstance(sourceAccount).isPeerNoForwards(message.getDialogId())) {
                return NOT_SENT;
            }
            sameSourceAccount &= message.currentAccount == destination.account;
        }

        SendMessagesHelper targetHelper = SendMessagesHelper.getInstance(destination.account);
        if (sameSourceAccount) {
            return targetHelper.sendMessage(messages, destination.dialogId, false, false, true, 0, 0, null, -1, 0, 0, null);
        }

        // Preflight all entries before scheduling any uploads, avoiding partial
        // sends followed by an error when one member of a media group is absent.
        final ArrayList<File> localFiles = new ArrayList<>();
        final boolean textOnly = isTextOnly(messages);
        if (!textOnly) {
            for (MessageObject message : messages) {
                TLRPC.MessageMedia media = message.messageOwner.media;
                if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) {
                    localFiles.add(null);
                    continue;
                }
                if (message.messageOwner.media.ttl_seconds != 0 || DialogObject.isEncryptedDialog(message.getDialogId())) {
                    return NOT_SENT;
                }
                boolean canUpload = media instanceof TLRPC.TL_messageMediaPhoto && media.photo != null
                        || media instanceof TLRPC.TL_messageMediaDocument && media.document != null;
                if (!canUpload) {
                    return NOT_SENT;
                }
                File localFile = getLocalMediaFile(message);
                if (localFile == null || !localFile.isFile() || localFile.length() <= 0) {
                    return NOT_SENT;
                }
                localFiles.add(localFile);
            }
        }

        if (textOnly) {
            for (MessageObject message : messages) {
                if (TextUtils.isEmpty(message.messageOwner.message)) {
                    continue;
                }
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        message.messageOwner.message,
                        destination.dialogId,
                        null,
                        null,
                        null,
                        true,
                        copyEntities(message.messageOwner.entities),
                        null,
                        null,
                        true,
                        0,
                        0,
                        null,
                        false
                );
                targetHelper.sendMessage(params);
            }
            return 0;
        }

        AccountInstance targetInstance = AccountInstance.getInstance(destination.account);
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            TLRPC.MessageMedia media = message.messageOwner.media;
            if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) {
                if (!TextUtils.isEmpty(message.messageOwner.message)) {
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            message.messageOwner.message, destination.dialogId, null, null, null, true,
                            copyEntities(message.messageOwner.entities), null, null, true, 0, 0, null, false
                    );
                    targetHelper.sendMessage(params);
                }
                continue;
            }

            String caption = message.messageOwner.message;
            ArrayList<TLRPC.MessageEntity> entities = copyEntities(message.messageOwner.entities);
            String path = localFiles.get(i).getAbsolutePath();
            if (media instanceof TLRPC.TL_messageMediaPhoto) {
                SendMessagesHelper.prepareSendingPhoto(
                        targetInstance, path, null, destination.dialogId, null, null, null,
                        caption, entities, null, null, 0, null, true, 0, 0, null, 0
                );
            } else {
                ArrayList<String> paths = new ArrayList<>();
                paths.add(path);
                ArrayList<String> originalPaths = new ArrayList<>();
                originalPaths.add(path);
                SendMessagesHelper.prepareSendingDocuments(
                        targetInstance, paths, originalPaths, null, caption, entities, null,
                        destination.dialogId, null, null, null, null, null, true, 0, 0,
                        null, null, 0, 0, false, 0, 0, null
                );
            }
        }
        return 0;
    }

    private static boolean isTextOnly(ArrayList<MessageObject> messages) {
        for (MessageObject message : messages) {
            TLRPC.MessageMedia media = message.messageOwner.media;
            if (media != null && !(media instanceof TLRPC.TL_messageMediaEmpty) && !(media instanceof TLRPC.TL_messageMediaWebPage)) {
                return false;
            }
        }
        return true;
    }

    private static File getLocalMediaFile(MessageObject message) {
        String attachPath = message.messageOwner.attachPath;
        if (!TextUtils.isEmpty(attachPath)) {
            File attachFile = new File(attachPath);
            if (attachFile.isFile() && attachFile.length() > 0) {
                return attachFile;
            }
        }
        try {
            File cacheFile = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
            return cacheFile != null && cacheFile.isFile() && cacheFile.length() > 0 ? cacheFile : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static ArrayList<TLRPC.MessageEntity> copyEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        // SendMessagesHelper only reads the entity list. A shallow list copy
        // keeps the source message immutable while preserving all entity
        // offsets and metadata (including custom emoji and mention entities).
        return new ArrayList<>(entities);
    }
}

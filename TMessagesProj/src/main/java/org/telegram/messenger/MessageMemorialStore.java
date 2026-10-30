package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/** Private, immutable local snapshots. No method writes a Telegram database or calls the server. */
public final class MessageMemorialStore {
    public static final String CAPTURE_SQL = "SELECT mid, date, out, data FROM messages_v2 WHERE uid = ? ORDER BY date, mid";
    private static final DispatchQueue queue = new DispatchQueue("messageMemorial");

    public static final class Memorial {
        public String id;
        public String title;
        public long dialogId;
        public long created;
        public int count;
        public int attachments;
    }

    public static final class Entry {
        public int messageId;
        public int date;
        public boolean outgoing;
        public String sender;
        public String text;
        public String mediaName;
        public String mediaFile;
        public String mime;
        public String mediaType;
    }

    private static File root(int account, long owner) {
        return new File(ApplicationLoader.getFilesDirFixed(), "message_memorial/" + account + "_" + owner);
    }

    private static boolean sameOwner(int account, long owner) {
        return UserConfig.getInstance(account).getClientUserId() == owner;
    }

    private static File snapshot(int account, long owner, String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid memorial id");
        return new File(root(account, owner), id);
    }

    public static void capture(int account, long dialogId, String title, LocalMessageInsightsRepository.Callback<Memorial> callback) {
        final long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0 || dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, true));
            return;
        }
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            Memorial result = new Memorial();
            result.id = UUID.randomUUID().toString();
            result.title = title == null ? Long.toString(dialogId) : title;
            result.dialogId = dialogId;
            result.created = System.currentTimeMillis() / 1000L;
            File pending = new File(root(account, owner), result.id + ".pending");
            SQLiteCursor cursor = null;
            boolean failed = false;
            HashMap<Long, String> names = new HashMap<>();
            HashMap<String, String> copied = new HashMap<>();
            try {
                if (!sameOwner(account, owner) || !pending.mkdirs()) throw new IllegalStateException("Cannot create snapshot");
                File mediaDir = new File(pending, "media");
                if (!mediaDir.mkdir()) throw new IllegalStateException("Cannot create media directory");
                cursor = storage.getDatabase().queryFinalized(CAPTURE_SQL, dialogId);
                try (FileOutputStream output = new FileOutputStream(new File(pending, "messages.jsonl"));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(3);
                        if (data == null) continue;
                        TLRPC.Message message;
                        try {
                            message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            if (message != null) message.readAttachPath(data, owner);
                        } finally { data.reuse(); }
                        if (message == null || message instanceof TLRPC.TL_messageEmpty) continue;
                        message.id = cursor.intValue(0);
                        message.date = cursor.intValue(1);
                        message.dialog_id = dialogId;
                        long sender = DialogObject.getPeerDialogId(message.from_id);
                        if (sender == 0) sender = cursor.intValue(2) != 0 ? owner : dialogId;
                        String senderName = names.get(sender);
                        if (senderName == null) {
                            if (sender > 0) {
                                TLRPC.User user = storage.getUser(sender);
                                senderName = user == null ? Long.toString(sender) : UserObject.getUserName(user);
                            } else {
                                TLRPC.Chat chat = storage.getChat(-sender);
                                senderName = chat == null ? result.title : chat.title;
                            }
                            names.put(sender, senderName);
                        }
                        String text = message.message == null ? "" : message.message;
                        if (text.isEmpty()) {
                            MessageObject object = new MessageObject(account, message, false, false);
                            if (object.messageText != null) text = object.messageText.toString();
                        }
                        JSONObject row = new JSONObject();
                        row.put("mid", message.id).put("date", message.date).put("out", cursor.intValue(2) != 0)
                                .put("sender", senderName).put("text", text);
                        String type = "", mediaName = "", mime = "application/octet-stream";
                        TLRPC.Document document = MessageObject.getDocument(message);
                        if (document != null) {
                            mediaName = FileLoader.getDocumentFileName(document);
                            mime = document.mime_type == null ? mime : document.mime_type;
                            type = mime;
                        } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
                            type = "image/jpeg";
                            mediaName = "photo.jpg";
                            mime = type;
                        } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPoll) {
                            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) MessageObject.getMedia(message)).poll;
                            if (poll != null && poll.question != null) row.put("text", poll.question.text);
                            type = "poll";
                        } else if (message instanceof TLRPC.TL_messageService) {
                            type = "service";
                        } else if (MessageObject.getMedia(message) != null && !(MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaEmpty)) {
                            type = "media";
                        }
                        row.put("mediaType", type).put("mediaName", mediaName).put("mime", mime);
                        File attachment = message.attachPath == null || message.attachPath.isEmpty() ? null : new File(message.attachPath);
                        if (attachment == null || !attachment.isFile()) attachment = FileLoader.getInstance(account).getPathToMessage(message);
                        if (!type.isEmpty() && attachment != null && attachment.isFile() && attachment.length() > 0) {
                            String source = attachment.getCanonicalPath();
                            String local = copied.get(source);
                            if (local == null) {
                                local = Integer.toString(copied.size()) + "_" + message.id;
                                copied.put(source, local);
                            }
                            row.put("mediaFile", local);
                            result.attachments++;
                        }
                        writer.write(row.toString());
                        writer.newLine();
                        result.count++;
                    }
                    writer.flush();
                    output.getFD().sync();
                }
            } catch (Exception e) {
                FileLog.e(e);
                failed = true;
                removeDirectory(pending);
            } finally { if (cursor != null) cursor.dispose(); }
            if (failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (sameOwner(account, owner)) callback.onResult(null, true);
                });
                return;
            }
            // Large media copies must not stall Telegram's database queue.
            queue.postRunnable(() -> {
                boolean error = false;
                try {
                    for (java.util.Map.Entry<String, String> attachment : copied.entrySet()) {
                        if (!AndroidUtilities.copyFile(new File(attachment.getKey()), new File(new File(pending, "media"), attachment.getValue())))
                            throw new IllegalStateException("Attachment copy failed");
                    }
                    JSONObject meta = new JSONObject();
                    meta.put("id", result.id).put("title", result.title).put("dialog", result.dialogId).put("created", result.created)
                            .put("count", result.count).put("attachments", result.attachments).put("owner", owner).put("account", account);
                    try (FileOutputStream output = new FileOutputStream(new File(pending, "manifest.json"))) {
                        output.write(meta.toString().getBytes(StandardCharsets.UTF_8));
                        output.getFD().sync();
                    }
                    if (result.count == 0 || !sameOwner(account, owner) || !pending.renameTo(snapshot(account, owner, result.id)))
                        throw new IllegalStateException("Empty or incomplete snapshot");
                } catch (Exception e) {
                    FileLog.e(e);
                    error = true;
                    removeDirectory(pending);
                }
                boolean finalError = error;
                AndroidUtilities.runOnUIThread(() -> {
                    if (sameOwner(account, owner)) callback.onResult(finalError ? null : result, finalError);
                });
            });
        });
    }

    public static void list(int account, LocalMessageInsightsRepository.Callback<ArrayList<Memorial>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<Memorial> result = new ArrayList<>();
            boolean failed = false;
            try {
                File[] directories = root(account, owner).listFiles();
                if (directories != null) for (File directory : directories) {
                    if (!directory.getName().matches("[0-9a-f-]{36}")) continue;
                    try (BufferedReader reader = reader(new File(directory, "manifest.json"))) {
                        JSONObject json = new JSONObject(reader.readLine());
                        if (json.optLong("owner") != owner || json.optInt("account") != account) continue;
                        Memorial entry = new Memorial();
                        entry.id = directory.getName();
                        entry.title = json.getString("title");
                        entry.dialogId = json.getLong("dialog");
                        entry.created = json.getLong("created");
                        entry.count = json.getInt("count");
                        entry.attachments = json.optInt("attachments");
                        result.add(entry);
                    }
                }
                result.sort((a, b) -> Long.compare(b.created, a.created));
            } catch (Exception e) { FileLog.e(e); failed = true; }
            boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> { if (sameOwner(account, owner)) callback.onResult(result, error); });
        });
    }

    public static void read(int account, String id, int offset, int limit, LocalMessageInsightsRepository.Callback<ArrayList<Entry>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<Entry> result = new ArrayList<>();
            boolean failed = false;
            try (BufferedReader reader = reader(new File(snapshot(account, owner, id), "messages.jsonl"))) {
                String line;
                int index = 0;
                while ((line = reader.readLine()) != null && result.size() < limit) {
                    if (index++ < offset) continue;
                    JSONObject row = new JSONObject(line);
                    Entry entry = new Entry();
                    entry.messageId = row.getInt("mid");
                    entry.date = row.getInt("date");
                    entry.outgoing = row.getBoolean("out");
                    entry.sender = row.getString("sender");
                    entry.text = row.getString("text");
                    entry.mediaName = row.optString("mediaName");
                    entry.mediaType = row.optString("mediaType");
                    entry.mediaFile = row.optString("mediaFile");
                    entry.mime = row.optString("mime", "application/octet-stream");
                    result.add(entry);
                }
            } catch (Exception e) { FileLog.e(e); failed = true; }
            boolean error = failed;
            AndroidUtilities.runOnUIThread(() -> { if (sameOwner(account, owner)) callback.onResult(result, error); });
        });
    }

    public static void mediaForViewing(int account, String id, Entry entry, LocalMessageInsightsRepository.Callback<File> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            File result = null;
            try {
                if (entry.mediaFile == null || !entry.mediaFile.matches("[0-9]+_-?[0-9]+")) throw new IllegalArgumentException("Invalid attachment");
                File original = new File(new File(snapshot(account, owner, id), "media"), entry.mediaFile);
                File directory = new File(ApplicationLoader.getFilesDirFixed(), "cache/memorial_" + account + "_" + owner);
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot prepare attachment");
                result = new File(directory, id + "_" + entry.mediaFile);
                if (!AndroidUtilities.copyFile(original, result)) result = null;
            } catch (Exception e) { FileLog.e(e); }
            File file = result;
            AndroidUtilities.runOnUIThread(() -> { if (sameOwner(account, owner)) callback.onResult(file, file == null); });
        });
    }

    public static void delete(int account, String id, LocalMessageInsightsRepository.Callback<Boolean> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            File directory = snapshot(account, owner, id);
            removeDirectory(directory);
            File viewCache = new File(ApplicationLoader.getFilesDirFixed(), "cache/memorial_" + account + "_" + owner);
            File[] copies = viewCache.listFiles();
            if (copies != null) for (File copy : copies) {
                if (copy.getName().startsWith(id + "_")) copy.delete();
            }
            boolean deleted = !directory.exists();
            AndroidUtilities.runOnUIThread(() -> { if (sameOwner(account, owner)) callback.onResult(deleted, !deleted); });
        });
    }

    private static BufferedReader reader(File file) throws Exception {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static void removeDirectory(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeDirectory(child);
        if (file.exists()) file.delete();
    }
}

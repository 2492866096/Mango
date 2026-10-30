package org.telegram.extensiontests;

import android.app.Instrumentation;

import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocalMessageInsightsRepository;
import org.telegram.messenger.MessageMemorialStore;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Instrumentation-thread only. Fixtures are written only in a disposable signed-out emulator. */
public final class MessageInsightsInstrumentationChecks {
    private static final long OWNER = 777000301, PEER = 777000302;
    private static int checks;
    private interface Start<T> { void run(LocalMessageInsightsRepository.Callback<T> callback); }

    private static <T> T result(Start<T> start) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        boolean[] failed = new boolean[1];
        start.run((answer, error) -> { value.set(answer); failed[0] = error; done.countDown(); });
        check(done.await(25, TimeUnit.SECONDS), "Callback delivered");
        check(!failed[0], "Repository operation succeeded");
        return value.get();
    }

    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }

    public static String run(Instrumentation instrumentation) throws Exception {
        check(UserConfig.getActivatedAccounts().length == 0, "Message insights requires signed-out emulator");
        UserConfig config = UserConfig.getInstance(0);
        Field current = UserConfig.class.getDeclaredField("currentUser"); current.setAccessible(true);
        Object original = current.get(config);
        TLRPC.TL_user owner = new TLRPC.TL_user(); owner.id = OWNER;
        instrumentation.runOnMainSync(() -> {
            try { current.set(config, owner); } catch (Exception e) { throw new RuntimeException(e); }
        });
        File source = new File(ApplicationLoader.getFilesDirFixed(), "memorial_fixture.txt");
        MessageMemorialStore.Memorial memorial = null;
        try {
            try (FileOutputStream output = new FileOutputStream(source)) { output.write("durable local attachment".getBytes(StandardCharsets.UTF_8)); }
            testRecallPaging();
            writeFixtures(source);
            LocalMessageInsightsRepository.Statistics stats = result(cb -> LocalMessageInsightsRepository.statistics(0, PEER, 1700000000L, 1700172800L, TimeZone.getTimeZone("UTC"), cb));
            check(stats.sent == 1 && stats.received == 1, "Real SQLite counts sent and received for selected peer");
            check(stats.activeDays == 2, "Real SQLite two active dates");
            ArrayList<LocalMessageInsightsRepository.DialogEntry> dialogs = result(cb -> LocalMessageInsightsRepository.dialogs(0, true, cb));
            check(dialogs.stream().anyMatch(d -> d.dialogId == PEER), "Picker discovers history without active dialogs row");
            memorial = result(cb -> MessageMemorialStore.capture(0, PEER, "Memorial fixture", cb));
            check(memorial.count == 2 && memorial.attachments == 1, "Snapshot captures history and downloaded attachment");
            String id = memorial.id;
            check(source.delete(), "Removed original attachment");
            clearFixtures();
            ArrayList<MessageMemorialStore.Memorial> list = result(cb -> MessageMemorialStore.list(0, cb));
            check(list.stream().anyMatch(m -> m.id.equals(id) && m.count == 2), "Manifest reload after original chat deletion");
            ArrayList<MessageMemorialStore.Entry> page = result(cb -> MessageMemorialStore.read(0, id, 0, 1, cb));
            check(page.size() == 1 && page.get(0).text.equals("first fixture message"), "Read archived text from disk");
            page = result(cb -> MessageMemorialStore.read(0, id, 1, 1, cb));
            check(page.size() == 1 && page.get(0).outgoing && !page.get(0).mediaFile.isEmpty(), "Snapshot pagination and metadata");
            MessageMemorialStore.Entry attachment = page.get(0);
            File copied = result(cb -> MessageMemorialStore.mediaForViewing(0, id, attachment, cb));
            check(new String(java.nio.file.Files.readAllBytes(copied.toPath()), StandardCharsets.UTF_8).equals("durable local attachment"), "Archived attachment survives original deletion");
            UserConfig otherConfig = UserConfig.getInstance(1);
            Object otherOriginal = current.get(otherConfig);
            try {
                instrumentation.runOnMainSync(() -> {
                    try { current.set(otherConfig, owner); } catch (Exception e) { throw new RuntimeException(e); }
                });
                ArrayList<MessageMemorialStore.Memorial> otherAccount = result(cb -> MessageMemorialStore.list(1, cb));
                check(otherAccount.isEmpty(), "Same owner in different account slot cannot read snapshot");
            } finally {
                instrumentation.runOnMainSync(() -> {
                    try { current.set(otherConfig, otherOriginal); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
            instrumentation.runOnMainSync(() -> owner.id++);
            ArrayList<MessageMemorialStore.Memorial> isolated = result(cb -> MessageMemorialStore.list(0, cb));
            check(isolated.isEmpty(), "Snapshot owner isolation");
            instrumentation.runOnMainSync(() -> owner.id--);
            Boolean deleted = result(cb -> MessageMemorialStore.delete(0, id, cb));
            check(deleted, "Delete local snapshot");
            list = result(cb -> MessageMemorialStore.list(0, cb));
            check(list.stream().noneMatch(m -> m.id.equals(id)), "Deleted snapshot absent from disk listing");
            memorial = null;
            return "Message insights: " + checks + " real database/snapshot assertions passed.\n";
        } finally {
            clearFixtures();
            if (memorial != null) {
                String id = memorial.id;
                instrumentation.runOnMainSync(() -> owner.id = OWNER);
                MessageInsightsInstrumentationChecks.<Boolean>result(cb -> MessageMemorialStore.delete(0, id, cb));
            }
            source.delete();
            instrumentation.runOnMainSync(() -> {
                try { current.set(config, original); } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }

    private static void testRecallPaging() {
        org.telegram.messenger.RecalledMessagesStore.clear(0);
        org.telegram.messenger.RecalledMessagesStore.setEnabled(0, true);
        for (int id : new int[]{10, 20, 30, 40}) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = id; message.date = 1700000000 + id; message.dialog_id = PEER;
            message.peer_id = new TLRPC.TL_peerUser(); message.peer_id.user_id = PEER;
            message.message = "recall " + id;
            org.telegram.messenger.RecalledMessagesStore.save(0, message);
        }
        ArrayList<TLRPC.Message> page = new ArrayList<>();
        org.telegram.messenger.RecalledMessagesStore.merge(0, PEER, page, 0, 2, 0, false);
        check(page.size() == 2 && page.get(0).id == 40 && page.get(1).id == 30, "Empty history restores multiple newest recalled messages");
        page.clear();
        org.telegram.messenger.RecalledMessagesStore.merge(0, PEER, page, 30, 2, 0, false,
                org.telegram.messenger.MessagesController.LOAD_BACKWARD, 0);
        check(page.size() == 2 && page.get(0).id == 20 && page.get(1).id == 10, "Empty history pages older using strict anchor");
        page.clear();
        org.telegram.messenger.RecalledMessagesStore.merge(0, PEER, page, 20, 2, 0, false,
                org.telegram.messenger.MessagesController.LOAD_FORWARD, 0);
        check(page.size() == 2 && page.get(0).id == 40 && page.get(1).id == 30, "Empty history pages newer using strict anchor");
        page.clear();
        org.telegram.messenger.RecalledMessagesStore.merge(0, PEER, page, 20, 4, 0, false,
                org.telegram.messenger.MessagesController.LOAD_AROUND_MESSAGE, 0);
        check(page.size() == 4 && page.stream().anyMatch(m -> m.id == 20), "Jump to recalled message includes both sides and target");
        org.telegram.messenger.RecalledMessagesStore.merge(0, PEER, page, 20, 4, 0, false,
                org.telegram.messenger.MessagesController.LOAD_AROUND_MESSAGE, 0);
        check(page.size() == 4, "Recall paging deduplicates existing page");
        org.telegram.messenger.RecalledMessagesStore.clear(0);
        org.telegram.messenger.RecalledMessagesStore.setEnabled(0, false);
    }

    private static void writeFixtures(File attachment) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        MessagesStorage storage = MessagesStorage.getInstance(0);
        storage.getStorageQueue().postRunnable(() -> {
            try {
                storage.getDatabase().executeFast("DELETE FROM messages_v2 WHERE uid = " + PEER).stepThis().dispose();
                for (int i = 0; i < 2; i++) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = 1001 + i; message.date = 1700000000 + i * 86400; message.out = i == 1;
                    message.peer_id = new TLRPC.TL_peerUser(); message.peer_id.user_id = PEER;
                    message.from_id = new TLRPC.TL_peerUser(); message.from_id.user_id = i == 0 ? PEER : OWNER;
                    message.flags |= 256;
                    message.message = i == 0 ? "first fixture message" : "attachment fixture";
                    if (i == 1) {
                        message.media = new TLRPC.TL_messageMediaDocument();
                        message.media.flags |= 1;
                        message.media.document = new TLRPC.TL_documentEmpty(); message.media.document.id = 1001;
                        message.flags |= 512;
                        message.attachPath = attachment.getAbsolutePath();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);
                    SQLitePreparedStatement state = storage.getDatabase().executeFast("INSERT INTO messages_v2(mid,uid,date,out,send_state,data) VALUES(?,?,?,?,?,?)");
                    state.bindInteger(1, message.id); state.bindLong(2, PEER); state.bindInteger(3, message.date);
                    state.bindInteger(4, i); state.bindInteger(5, 0); state.bindByteBuffer(6, data);
                    state.step(); state.dispose(); data.reuse();
                }
            } catch (Throwable t) { error.set(t); } finally { done.countDown(); }
        });
        check(done.await(25, TimeUnit.SECONDS), "Fixture storage queue completed");
        if (error.get() != null) throw new AssertionError("Fixture write", error.get());
    }

    private static void clearFixtures() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        MessagesStorage storage = MessagesStorage.getInstance(0);
        storage.getStorageQueue().postRunnable(() -> {
            try { storage.getDatabase().executeFast("DELETE FROM messages_v2 WHERE uid = " + PEER).stepThis().dispose(); }
            catch (Exception e) { throw new RuntimeException(e); } finally { done.countDown(); }
        });
        check(done.await(25, TimeUnit.SECONDS), "Fixture cleanup completed");
    }
}

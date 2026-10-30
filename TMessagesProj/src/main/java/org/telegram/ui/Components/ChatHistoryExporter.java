package org.telegram.ui.Components;

import android.content.ContentResolver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes the locally cached history of a dialog to a SAF-selected document. */
public final class ChatHistoryExporter {
    public static final int FORMAT_TXT = 1;
    public static final int FORMAT_PDF = 2;
    private static final int PAGE_SIZE = 200;
    private static final String EXPORT_SCOPE = "Locally cached history only. Messages not stored on this device are not included.";

    public interface Callback {
        void onComplete(boolean success);
    }

    private ChatHistoryExporter() {
    }

    public static String createFileName(String title, int format) {
        String base = TextUtils.isEmpty(title) ? "chat" : title.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (TextUtils.isEmpty(base)) {
            base = "chat";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).trim();
        }
        return base + (format == FORMAT_PDF ? ".pdf" : ".txt");
    }

    public static void export(ContentResolver resolver, Uri destination, int account, long dialogId, long mergeDialogId, long topicId, String title, String scope, int format, Callback callback) {
        if (resolver == null || destination == null || (format != FORMAT_TXT && format != FORMAT_PDF)) {
            complete(callback, false);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            ExportSession session = new ExportSession(resolver, destination, account, dialogId, mergeDialogId, topicId, title, scope, format, callback);
            try {
                session.open();
                session.loadNextPage(false, 0, 0, 0);
            } catch (Exception e) {
                FileLog.e(e);
                session.finish(false);
            }
        });
    }

    private static final class ExportSession {
        private static final int PDF_WIDTH = 595;
        private static final int PDF_HEIGHT = 842;
        private static final float PDF_MARGIN = 42f;

        private final ContentResolver resolver;
        private final Uri destination;
        private final int account;
        private final long dialogId;
        private final long mergeDialogId;
        private final long topicId;
        private final String title;
        private final String scope;
        private final int format;
        private final Callback callback;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        private OutputStream output;
        private BufferedWriter textWriter;
        private PdfDocument pdfDocument;
        private PdfDocument.Page pdfPage;
        private Canvas pdfCanvas;
        private float pdfY;
        private TextPaint pdfTextPaint;
        private Paint pdfTitlePaint;
        private Paint pdfDividerPaint;
        private boolean finished;

        private ExportSession(ContentResolver resolver, Uri destination, int account, long dialogId, long mergeDialogId, long topicId, String title, String scope, int format, Callback callback) {
            this.resolver = resolver;
            this.destination = destination;
            this.account = account;
            this.dialogId = dialogId;
            this.mergeDialogId = mergeDialogId;
            this.topicId = topicId;
            this.title = TextUtils.isEmpty(title) ? "Chat" : title;
            this.scope = TextUtils.isEmpty(scope) ? EXPORT_SCOPE : scope;
            this.format = format;
            this.callback = callback;
        }

        private void open() throws IOException {
            output = resolver.openOutputStream(destination, "w");
            if (output == null) {
                throw new IOException("Unable to open destination document");
            }
            if (format == FORMAT_TXT) {
                textWriter = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), 32 * 1024);
                textWriter.write(title);
                textWriter.write("\n\n");
                textWriter.write("[" + scope + "]\n\n");
            } else {
                pdfDocument = new PdfDocument();
                pdfTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                pdfTextPaint.setColor(Color.BLACK);
                pdfTextPaint.setTextSize(11f);
                pdfTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                pdfTitlePaint.setColor(Color.BLACK);
                pdfTitlePaint.setTextSize(16f);
                pdfTitlePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                pdfDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                pdfDividerPaint.setColor(0xff999999);
                pdfDividerPaint.setStrokeWidth(0.6f);
                beginPdfPage();
            }
        }

        private void loadNextPage(boolean hasCursor, int afterDate, long afterUid, int afterMid) {
            MessagesStorage.getInstance(account).getMessagesForExport(dialogId, mergeDialogId, topicId, hasCursor, afterDate, afterUid, afterMid, PAGE_SIZE, page -> {
                if (finished) {
                    return;
                }
                if (page == null || page.error != null) {
                    finish(false);
                    return;
                }
                try {
                    for (int i = 0; i < page.messages.size(); i++) {
                        String record = formatMessage(page.messages.get(i));
                        if (format == FORMAT_TXT) {
                            textWriter.write(record);
                        } else {
                            writePdfRecord(record);
                        }
                    }
                    if (page.hasMore) {
                        loadNextPage(true, page.nextDate, page.nextUid, page.nextMid);
                    } else {
                        finish(true);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    finish(false);
                }
            });
        }

        private String formatMessage(TLRPC.Message message) {
            String sender = getSenderName(message);
            String body = message.message == null ? "" : message.message.trim();
            if (TextUtils.isEmpty(body)) {
                if (message.action != null) {
                    body = "[Service message]";
                } else if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                    body = "[Photo]";
                } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                    TLRPC.Document document = ((TLRPC.TL_messageMediaDocument) message.media).document;
                    String fileName = document == null ? "" : FileLoader.getDocumentFileName(document);
                    body = TextUtils.isEmpty(fileName) ? "[File]" : "[File: " + fileName + "]";
                } else if (message.media != null) {
                    body = "[Media message]";
                } else {
                    body = "[Empty message]";
                }
            }
            return dateFormat.format(new Date(Math.max(0L, (long) message.date) * 1000L)) + " | " + sender + "\n" + body + "\n\n";
        }

        private String getSenderName(TLRPC.Message message) {
            if (message.from_id instanceof TLRPC.TL_peerUser) {
                long id = message.from_id.user_id;
                TLRPC.User user = MessagesController.getInstance(account).getUser(id);
                return user == null ? "User " + id : UserObject.getUserName(user);
            } else if (message.from_id instanceof TLRPC.TL_peerChat) {
                long id = message.from_id.chat_id;
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(id);
                return chat == null ? "Group " + id : chat.title;
            } else if (message.from_id instanceof TLRPC.TL_peerChannel) {
                long id = message.from_id.channel_id;
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(id);
                return chat == null ? "Channel " + id : chat.title;
            } else if (message.out) {
                TLRPC.User self = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
                return self == null ? "You" : UserObject.getUserName(self);
            }
            return title;
        }

        private void writePdfRecord(String record) {
            StaticLayout layout = new StaticLayout(record, pdfTextPaint, (int) (PDF_WIDTH - PDF_MARGIN * 2), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            int firstLine = 0;
            while (firstLine < layout.getLineCount()) {
                float availableHeight = PDF_HEIGHT - PDF_MARGIN - pdfY;
                if (availableHeight < 14f) {
                    finishPdfPage();
                    beginPdfPage();
                    availableHeight = PDF_HEIGHT - PDF_MARGIN - pdfY;
                }
                int lastLine = firstLine;
                while (lastLine < layout.getLineCount() && layout.getLineBottom(lastLine) - layout.getLineTop(firstLine) <= availableHeight) {
                    lastLine++;
                }
                if (lastLine == firstLine) {
                    finishPdfPage();
                    beginPdfPage();
                    continue;
                }
                float sourceTop = layout.getLineTop(firstLine);
                float chunkHeight = layout.getLineBottom(lastLine - 1) - sourceTop;
                pdfCanvas.save();
                pdfCanvas.clipRect(PDF_MARGIN, pdfY, PDF_WIDTH - PDF_MARGIN, pdfY + chunkHeight);
                pdfCanvas.translate(PDF_MARGIN, pdfY - sourceTop);
                layout.draw(pdfCanvas);
                pdfCanvas.restore();
                pdfY += chunkHeight;
                firstLine = lastLine;
                if (firstLine < layout.getLineCount()) {
                    finishPdfPage();
                    beginPdfPage();
                }
            }
        }

        private void beginPdfPage() {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pdfDocument.getPages().size() + 1).create();
            pdfPage = pdfDocument.startPage(pageInfo);
            pdfCanvas = pdfPage.getCanvas();
            pdfCanvas.drawText(title, PDF_MARGIN, 28f, pdfTitlePaint);
            pdfCanvas.drawLine(PDF_MARGIN, 38f, PDF_WIDTH - PDF_MARGIN, 38f, pdfDividerPaint);
            StaticLayout scopeLayout = new StaticLayout(scope, pdfTextPaint, (int) (PDF_WIDTH - PDF_MARGIN * 2), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            pdfCanvas.save();
            pdfCanvas.translate(PDF_MARGIN, 43f);
            scopeLayout.draw(pdfCanvas);
            pdfCanvas.restore();
            pdfY = 49f + scopeLayout.getHeight();
        }

        private void finishPdfPage() {
            if (pdfPage != null) {
                pdfDocument.finishPage(pdfPage);
                pdfPage = null;
                pdfCanvas = null;
            }
        }

        private void finish(boolean success) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                if (success) {
                    if (format == FORMAT_TXT) {
                        textWriter.flush();
                    } else {
                        finishPdfPage();
                        pdfDocument.writeTo(output);
                    }
                }
            } catch (Exception e) {
                success = false;
                FileLog.e(e);
            } finally {
                try {
                    if (textWriter != null) {
                        textWriter.close();
                    } else if (output != null) {
                        output.close();
                    }
                } catch (Exception e) {
                    success = false;
                    FileLog.e(e);
                }
                if (pdfDocument != null) {
                    pdfDocument.close();
                }
                final boolean result = success;
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onComplete(result);
                    }
                });
            }
        }
    }

    private static void complete(Callback callback, boolean success) {
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.onComplete(success));
        }
    }
}

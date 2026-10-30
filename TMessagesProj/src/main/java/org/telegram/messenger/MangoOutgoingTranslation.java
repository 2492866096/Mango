package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

/** Translates drafts without creating any outgoing message until the caller accepts the result. */
public final class MangoOutgoingTranslation {
    private MangoOutgoingTranslation() { }

    public static String getLanguage(int account, long dialogId) {
        return MangoSettings.getString(account, "mango_send_translation_" + dialogId, "en");
    }

    public static void setLanguage(int account, long dialogId, String language) {
        MangoSettings.setString(account, "mango_send_translation_" + dialogId,
                TranslateController.normalizeTranslateLanguage(language));
    }

    /** Returns a cancellation action. A canceled or timed-out request never invokes success later. */
    public static Runnable translate(int account, TLRPC.TL_textWithEntities source, String language,
                                     Utilities.Callback<TLRPC.TL_textWithEntities> done) {
        final boolean[] complete = {false};
        final int[] requestId = {0};
        final Runnable[] timeout = {null};
        final Utilities.Callback<TLRPC.TL_textWithEntities> finish = result -> {
            if (complete[0]) return;
            complete[0] = true;
            AndroidUtilities.cancelRunOnUIThread(timeout[0]);
            done.run(result == null || TextUtils.isEmpty(result.text) ? null : result);
        };
        timeout[0] = () -> {
            if (requestId[0] != 0) ConnectionsManager.getInstance(account).cancelRequest(requestId[0], true);
            finish.run(null);
        };
        AndroidUtilities.runOnUIThread(timeout[0], 30000);
        final TLRPC.TL_messages_translateText request = new TLRPC.TL_messages_translateText();
        request.flags |= 2;
        request.text.add(source);
        request.to_lang = TranslateController.normalizeTranslateLanguage(language);
        requestId[0] = ConnectionsManager.getInstance(account).sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (complete[0]) return;
                    if (response instanceof TLRPC.TL_messages_translateResult &&
                            !((TLRPC.TL_messages_translateResult) response).result.isEmpty()) {
                        TLRPC.TL_textWithEntities translated = ((TLRPC.TL_messages_translateResult) response).result.get(0);
                        if (!TextUtils.isEmpty(translated.text)) {
                            finish.run(TranslateAlert2.preprocess(source, translated));
                            return;
                        }
                    }
                    // A plain-text fallback must not discard mention IDs, hidden links or custom emoji.
                    if (!source.entities.isEmpty()) {
                        finish.run(null);
                        return;
                    }
                    // Reuse the existing translation endpoint, including its chunk handling.
                    TranslateAlert2.alternativeTranslate(source.text, "auto", request.to_lang,
                            (translated, limited) -> AndroidUtilities.runOnUIThread(() -> {
                                TLRPC.TL_textWithEntities fallback = new TLRPC.TL_textWithEntities();
                                fallback.text = translated;
                                finish.run(fallback);
                            }));
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
        return () -> {
            complete[0] = true;
            AndroidUtilities.cancelRunOnUIThread(timeout[0]);
            if (requestId[0] != 0) ConnectionsManager.getInstance(account).cancelRequest(requestId[0], true);
        };
    }
}

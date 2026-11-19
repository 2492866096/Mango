package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/** Keeps clickable text out of translation requests and restores its original UTF-16 entities. */
public final class MangoTranslationLinks {
    private static final int CACHE_ENTRIES = 64;
    private static final int CACHE_CHARACTERS = 256 * 1024;
    private static final long CACHE_LIFETIME = 5 * 60 * 1000L;
    private static final HashMap<String, Pending> pending = new HashMap<>();
    private static final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private static int cacheCharacters;

    private MangoTranslationLinks() { }

    private static boolean protectedEntity(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityUrl
                || entity instanceof TLRPC.TL_messageEntityTextUrl
                || entity instanceof TLRPC.TL_messageEntityEmail
                || entity instanceof TLRPC.TL_messageEntityMention
                || entity instanceof TLRPC.TL_messageEntityMentionName
                || entity instanceof TLRPC.TL_inputMessageEntityMentionName;
    }

    private static boolean validEntity(TLRPC.MessageEntity entity, String text) {
        return entity != null && entity.offset >= 0 && entity.length > 0
                && (long) entity.offset + entity.length <= text.length()
                && isBoundary(text, entity.offset) && isBoundary(text, entity.offset + entity.length);
    }

    private static boolean isBoundary(String text, int offset) {
        return offset == 0 || offset == text.length()
                || !Character.isHighSurrogate(text.charAt(offset - 1)) || !Character.isLowSurrogate(text.charAt(offset));
    }

    private static TLRPC.MessageEntity copyEntity(TLRPC.MessageEntity entity) {
        SerializedData output = new SerializedData();
        entity.serializeToStream(output);
        SerializedData input = new SerializedData(output.toByteArray());
        TLRPC.MessageEntity copy = TLRPC.MessageEntity.TLdeserialize(input, input.readInt32(true), true);
        if (copy instanceof TLRPC.TL_messageEntityCustomEmoji) {
            ((TLRPC.TL_messageEntityCustomEmoji) copy).document = ((TLRPC.TL_messageEntityCustomEmoji) entity).document;
        }
        input.cleanup();
        output.cleanup();
        return copy;
    }

    private static TLRPC.TL_textWithEntities slice(TLRPC.TL_textWithEntities source, int start, int end) {
        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.text = source.text.substring(start, end);
        if (source.entities != null) {
            for (TLRPC.MessageEntity entity : source.entities) {
                if (!validEntity(entity, source.text)) continue;
                int left = Math.max(start, entity.offset);
                int right = Math.min(end, entity.offset + entity.length);
                if (left >= right) continue;
                TLRPC.MessageEntity copy = copyEntity(entity);
                copy.offset = left - start;
                copy.length = right - left;
                result.entities.add(copy);
            }
        }
        return result;
    }

    private static TLRPC.TL_textWithEntities copy(TLRPC.TL_textWithEntities text) {
        return slice(text, 0, text.text.length());
    }

    public static final class Plan {
        public final TLRPC.TL_textWithEntities original;
        public final ArrayList<TLRPC.TL_textWithEntities> requests = new ArrayList<>();
        private final ArrayList<TLRPC.TL_textWithEntities> parts = new ArrayList<>();
        private final ArrayList<Integer> indexes = new ArrayList<>();
        public final boolean hasLinks;

        private Plan(TLRPC.TL_textWithEntities source) {
            original = copy(source);
            ArrayList<int[]> ranges = new ArrayList<>();
            for (TLRPC.MessageEntity entity : original.entities) {
                if (protectedEntity(entity)) ranges.add(new int[]{entity.offset, entity.offset + entity.length});
            }
            // Use the same URL matcher as chat rendering, including bare t.me/domain links.
            if (LinkifyPort.WEB_URL != null) {
                Matcher matcher = LinkifyPort.WEB_URL.matcher(original.text);
                while (matcher.find()) {
                    int start = matcher.start(), end = matcher.end();
                    if (!isBoundary(original.text, start) || !isBoundary(original.text, end)) continue;
                    boolean overlap = false;
                    for (int[] range : ranges) {
                        if (start < range[1] && end > range[0]) { overlap = true; break; }
                    }
                    if (overlap) continue;
                    TLRPC.TL_messageEntityUrl link = new TLRPC.TL_messageEntityUrl();
                    link.offset = start;
                    link.length = end - start;
                    original.entities.add(link);
                    ranges.add(new int[]{start, end});
                }
            }
            ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
            ArrayList<int[]> merged = new ArrayList<>();
            for (int[] range : ranges) {
                if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                    int[] previous = merged.get(merged.size() - 1);
                    previous[1] = Math.max(previous[1], range[1]);
                } else {
                    merged.add(range.clone());
                }
            }
            hasLinks = !merged.isEmpty();
            int cursor = 0;
            for (int[] range : merged) {
                addText(cursor, range[0]);
                addPart(range[0], range[1], false);
                cursor = range[1];
            }
            addText(cursor, original.text.length());
        }

        private void addText(int start, int end) {
            int left = start, right = end;
            while (left < right && Character.isWhitespace(original.text.charAt(left))) left++;
            while (right > left && Character.isWhitespace(original.text.charAt(right - 1))) right--;
            addPart(start, left, false);
            boolean letters = false;
            for (int i = left; i < right;) {
                int codePoint = original.text.codePointAt(i);
                letters |= Character.isLetter(codePoint);
                i += Character.charCount(codePoint);
            }
            addPart(left, right, letters);
            addPart(right, end, false);
        }

        private void addPart(int start, int end, boolean translate) {
            if (start >= end) return;
            TLRPC.TL_textWithEntities part = slice(original, start, end);
            parts.add(part);
            indexes.add(translate ? requests.size() : -1);
            if (translate) requests.add(part);
        }

        public TLRPC.TL_textWithEntities restore(ArrayList<TLRPC.TL_textWithEntities> translated) {
            if (translated == null || translated.size() != requests.size()) return null;
            TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                int index = indexes.get(i);
                TLRPC.TL_textWithEntities part = index < 0 ? parts.get(i) : translated.get(index);
                if (part == null || part.text == null || index >= 0 && part.text.isEmpty()) return null;
                if (index >= 0) part = TranslateAlert2.preprocess(requests.get(index), copy(part));
                if (part == null) return null;
                for (TLRPC.MessageEntity entity : part.entities) {
                    if (!validEntity(entity, part.text)) continue;
                    TLRPC.MessageEntity adjusted = copyEntity(entity);
                    adjusted.offset += builder.length();
                    result.entities.add(adjusted);
                }
                builder.append(part.text);
            }
            result.text = builder.toString();
            result.entities.sort((a, b) -> Integer.compare(a.offset, b.offset));
            return result;
        }

        public boolean linksPreserved(TLRPC.TL_textWithEntities translated) {
            if (!hasLinks) return true;
            if (translated == null || translated.text == null || translated.entities == null) return false;
            ArrayList<String> remaining = new ArrayList<>();
            for (TLRPC.MessageEntity entity : translated.entities) {
                if (protectedEntity(entity) && validEntity(entity, translated.text)) {
                    remaining.add(linkSignature(entity, translated.text));
                }
            }
            for (TLRPC.MessageEntity entity : original.entities) {
                if (protectedEntity(entity) && !remaining.remove(linkSignature(entity, original.text))) return false;
            }
            return true;
        }
    }

    private static String linkSignature(TLRPC.MessageEntity entity, String text) {
        TLRPC.MessageEntity normalized = copyEntity(entity);
        normalized.offset = 0;
        SerializedData data = new SerializedData();
        normalized.serializeToStream(data);
        String signature = text.substring(entity.offset, entity.offset + entity.length) + Arrays.toString(data.toByteArray());
        data.cleanup();
        return signature;
    }

    public static Plan prepare(TLRPC.TL_textWithEntities source) {
        if (source == null || source.text == null) {
            source = new TLRPC.TL_textWithEntities();
            source.text = "";
        }
        return new Plan(source);
    }

    private static final class Listener {
        final Utilities.Callback2<TLRPC.TL_textWithEntities, Boolean> callback;
        boolean canceled;
        Listener(Utilities.Callback2<TLRPC.TL_textWithEntities, Boolean> callback) { this.callback = callback; }
    }

    private static final class Cached {
        final int account;
        final long owner, time = android.os.SystemClock.elapsedRealtime();
        final TLRPC.TL_textWithEntities result;
        final int characters;
        Cached(int account, long owner, String key, TLRPC.TL_textWithEntities result) {
            this.account = account; this.owner = owner; this.result = copy(result);
            characters = key.length() + result.text.length();
        }
    }

    private static final class Pending {
        final int account;
        final long owner;
        final String key, language;
        final Plan plan;
        final ArrayList<Listener> listeners = new ArrayList<>();
        final ArrayList<Integer> requestIds = new ArrayList<>();
        final ArrayList<Runnable> alternativeCancels = new ArrayList<>();
        final ArrayList<TLRPC.TL_textWithEntities> results;
        int remaining;
        boolean finished;
        Runnable timeout;
        Pending(int account, String key, String language, Plan plan) {
            this.account = account; this.owner = UserConfig.getInstance(account).getClientUserId();
            this.key = key; this.language = language; this.plan = plan;
            results = new ArrayList<>(Collections.nCopies(plan.requests.size(), null));
            remaining = results.size();
        }
    }

    /** Call on the UI thread. Cancellation removes only this caller from a coalesced request. */
    public static Runnable translate(int account, Plan plan, String language, boolean alternative,
                                     Utilities.Callback2<TLRPC.TL_textWithEntities, Boolean> done) {
        Listener listener = new Listener(done);
        final long owner = UserConfig.getInstance(account).getClientUserId();
        if (plan.requests.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!listener.canceled && UserConfig.getInstance(account).getClientUserId() == owner) done.run(copy(plan.original), false);
            });
            return () -> listener.canceled = true;
        }
        String normalized = TranslateController.normalizeTranslateLanguage(language);
        SerializedData data = new SerializedData();
        plan.original.serializeToStream(data);
        String key = account + ":" + UserConfig.getInstance(account).getClientUserId() + ":" + normalized + ":" + alternative + ":" + Arrays.toString(data.toByteArray());
        data.cleanup();
        Cached cached = cache.get(key);
        if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.time <= CACHE_LIFETIME) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!listener.canceled && UserConfig.getInstance(account).getClientUserId() == owner) done.run(copy(cached.result), false);
            });
            return () -> listener.canceled = true;
        }
        if (cached != null) { cache.remove(key); cacheCharacters -= cached.characters; }
        Pending request = pending.get(key);
        if (request == null) {
            request = new Pending(account, key, normalized, plan);
            pending.put(key, request);
            request.listeners.add(listener);
            final Pending active = request;
            active.timeout = () -> finish(active, null, false);
            AndroidUtilities.runOnUIThread(active.timeout, 30000);
            if (alternative) {
                requestAlternative(active, 0, plan.requests.size());
            } else {
                // Telegram accepts at most 20 texts per request. Every batch starts immediately.
                for (int start = 0; start < plan.requests.size(); start += 20) {
                    final int first = start, count = Math.min(20, plan.requests.size() - start);
                    TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
                    req.flags |= 2;
                    req.to_lang = normalized;
                    req.text.addAll(plan.requests.subList(first, first + count));
                    int id = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (active.finished) return;
                        if (response instanceof TLRPC.TL_messages_translateResult) {
                            ArrayList<TLRPC.TL_textWithEntities> texts = ((TLRPC.TL_messages_translateResult) response).result;
                            if (texts.size() != count) { finish(active, null, false); return; }
                            for (int i = 0; i < count; i++) accept(active, first + i, texts.get(i), false);
                        } else if (error != null && "TRANSLATIONS_DISABLED_ALT".equalsIgnoreCase(error.text)) {
                            requestAlternative(active, first, count);
                        } else {
                            finish(active, null, error != null && "QUOTA_EXCEEDED".equals(error.text));
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors);
                    active.requestIds.add(id);
                }
            }
        } else {
            request.listeners.add(listener);
        }
        final Pending active = request;
        return () -> {
            listener.canceled = true;
            active.listeners.remove(listener);
            if (active.listeners.isEmpty()) finish(active, null, false);
        };
    }

    private static void requestAlternative(Pending request, int first, int count) {
        for (int i = first; i < first + count; i++) {
            final int index = i;
            request.alternativeCancels.add(TranslateAlert2.alternativeTranslateCancelable(request.plan.requests.get(i).text, "auto", request.language,
                    (text, rateLimit) -> AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
                        result.text = text;
                        accept(request, index, result, rateLimit);
                    })));
        }
    }

    private static void accept(Pending request, int index, TLRPC.TL_textWithEntities result, boolean rateLimit) {
        if (request.finished || request.results.get(index) != null) return;
        if (result == null || result.text == null || result.text.isEmpty()) { finish(request, null, rateLimit); return; }
        request.results.set(index, result);
        if (--request.remaining == 0) finish(request, request.plan.restore(request.results), false);
    }

    private static void finish(Pending request, TLRPC.TL_textWithEntities result, boolean rateLimit) {
        if (request.finished) return;
        request.finished = true;
        pending.remove(request.key);
        AndroidUtilities.cancelRunOnUIThread(request.timeout);
        for (int id : request.requestIds) ConnectionsManager.getInstance(request.account).cancelRequest(id, true);
        for (Runnable cancel : request.alternativeCancels) cancel.run();
        if (UserConfig.getInstance(request.account).getClientUserId() != request.owner) return;
        if (result != null && !request.listeners.isEmpty()) {
            Cached entry = new Cached(request.account, request.owner, request.key, result);
            if (entry.characters <= CACHE_CHARACTERS) {
                Cached previous = cache.put(request.key, entry);
                cacheCharacters += entry.characters - (previous == null ? 0 : previous.characters);
                while (cache.size() > CACHE_ENTRIES || cacheCharacters > CACHE_CHARACTERS) {
                    Map.Entry<String, Cached> first = cache.entrySet().iterator().next();
                    cacheCharacters -= first.getValue().characters;
                    cache.remove(first.getKey());
                }
            }
        }
        for (Listener listener : new ArrayList<>(request.listeners)) {
            if (!listener.canceled) listener.callback.run(result == null ? null : copy(result), rateLimit);
        }
        request.listeners.clear();
    }

    public static void clear(int account) {
        for (Pending request : new ArrayList<>(pending.values())) {
            if (request.account == account) {
                request.listeners.clear();
                finish(request, null, false);
            }
        }
        for (Map.Entry<String, Cached> entry : new ArrayList<>(cache.entrySet())) {
            if (entry.getValue().account == account) {
                cacheCharacters -= entry.getValue().characters;
                cache.remove(entry.getKey());
            }
        }
    }
}

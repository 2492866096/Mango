#!/usr/bin/env python3
"""Guard message-copy and reply-swipe regressions without requiring Android."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHAT = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"
ANDROID_UTILS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java"


def extract(source: str, marker: str) -> str:
    start = source.index(marker)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end]


chat = CHAT.read_text()
utils = ANDROID_UTILS.read_text()
copy = extract(chat, "            case OPTION_COPY: {")
touch = extract(chat, "            private void processTouchEvent(MotionEvent e) {")
add_to_clipboard = extract(utils, "    public static boolean addToClipboard(CharSequence str, boolean plainTextOnly)")

checks = {
    "selected copy content is normalized to a plain string": "copyContent.toString()" in copy,
    "empty and whitespace-only messages do not replace the clipboard": "TextUtils.isEmpty(plainText) || plainText.trim().isEmpty()" in copy,
    "copy action requests plain-text clipboard data": "AndroidUtilities.addToClipboard(plainText, true)" in copy,
    "plain-text mode skips HTML clipboard entries": "if (!plainTextOnly && str instanceof Spanned)" in add_to_clipboard,
    "successful copy alone shows copied bulletin": copy.index("AndroidUtilities.addToClipboard(plainText, true)") < copy.index("createUndoView();"),
    "reply swipe cancellation copies current motion event": "MotionEvent.obtain(e)" in touch,
    "reply swipe cancellation changes only the action": "event.setAction(MotionEvent.ACTION_CANCEL)" in touch,
    "copied cancellation event is always recycled": "finally {\n                            event.recycle();" in touch,
    "zero-coordinate synthetic cancel has been removed": "MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL" not in touch,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("FAIL: " + "; ".join(failed))
print(f"PASS: {len(checks)} message-copy and swipe regression guards")

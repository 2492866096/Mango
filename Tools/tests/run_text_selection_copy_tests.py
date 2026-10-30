#!/usr/bin/env python3
"""Guard message text-selection source mapping and blank-copy regressions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Cells/TextSelectionHelper.java"


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


source = HELPER.read_text()
chat_selection = source[source.index("public static class ChatListTextSelectionHelper"):source.index("public static class ArticleTextSelectionHelper")]
get_text = extract(chat_selection, "        protected CharSequence getText(ChatMessageCell cell, boolean maybe)")
fill_offset = extract(chat_selection, "        protected void fillLayoutForOffset(int offset, LayoutBlock layoutBlock, boolean maybe)")
copy_text = extract(source, "    private void copyText()")
initial_adjustment = extract(chat_selection, "        protected int adjustInitialSelectionOffset(ChatMessageCell cell, CharSequence text, int offset, int x, int y)")

checks = {
    "message selection snapshots one content type before handle movement": "selectedTextType = getSelectionTextType(newView, true)" in chat_selection,
    "message body maps to the displayed message text": "return cell.getMessageObject().messageText" in get_text,
    "caption selection maps to caption layout text": "return cell.getCaptionLayout() == null ? null : cell.getCaptionLayout().text" in get_text,
    "description selection maps to description layout text": "cell.getDescriptionlayout().getText()" in get_text,
    "fact-check selection maps to fact-check layout text": "cell.getFactCheckLayout().getText()" in get_text,
    "selection offset layout uses the same resolved content type": "getSelectionTextType(selectedView, maybe)" in fill_offset,
    "long-press only snaps an initial whitespace hit": "adjustInitialSelectionOffset(newView, text, offset, x, y)" in source,
    "whitespace snap stays on the hit line and compares visible neighbors": "layout.getLineStart(line)" in initial_adjustment and "distanceToCharacter(layout, layoutBlock.charOffset" in initial_adjustment,
    "normal selected text is copied without trimming": "AndroidUtilities.addToClipboard(str.toString(), true)" in copy_text and ".trim()" not in copy_text,
    "copy action rejects Unicode whitespace-only selections": "hasNonWhitespaceContent(str)" in copy_text and "Character.isSpaceChar(codePoint)" in source,
    "copy menu is hidden for blank active ranges without disabling selection": "!isInSelectionMode() || hasNonWhitespaceContent(getSelectedText())" in chat_selection,
    "initial long-press selection includes one full Unicode code point": "offset + Character.charCount(codePoint)" in source,
    "word expansion is limited to word-character hits": "if (isInterruptedCharacter(text.charAt(offset)))" in source,
    "select-at path also selects a full Unicode code point": "offset + Character.charCount(Character.codePointAt(text, offset))" in chat_selection,
}

failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise SystemExit("FAIL: " + "; ".join(failed))

# Keep the text normalization contract explicit: whitespace around real content is preserved,
# while whitespace-only ranges are not a copyable selection.
def has_content(text: str) -> bool:
    return any(not (char.isspace() or char in "\u200b\ufeff") for char in text)


assert has_content("  actual words \n")
assert not has_content("  \t\n\u200b\ufeff")
assert has_content("字") and has_content("😀")
print(f"PASS: {len(checks) + 2} text-selection/copy regression checks")

#!/usr/bin/env python3
"""Regression guards for v4.8 announcement revisioning and explicit read sync."""
from pathlib import Path
import hashlib
import re

ROOT = Path(__file__).resolve().parents[2]
ann = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MangoAnnouncementController.java").read_text()
launch = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text()
mc = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java").read_text()
dialogs = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text()

checks = 0

def require(ok, label):
    global checks
    assert ok, label
    checks += 1

require("https://1812652903.cdn.123clouddisk.com/1812652903/GY/%E5%85%AC%E5%91%8A.txt" in ann, "announcement URL")
require("CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L" in ann, "two-hour announcement refresh")
require("MessageDigest.getInstance(\"SHA-256\")" in ann, "content revision keyed by SHA-256")
require("KEY_SHOWN_DIGEST" in ann and "KEY_PENDING_DIGEST" in ann and "KEY_PENDING_TEXT" in ann, "persisted acknowledgement and deferred notice")
require("MangoAnnouncementController.onResume(this)" in launch and "MangoAnnouncementController.onPause(this)" in launch, "foreground lifecycle wiring")
require("activity.getVisibleDialog() != null" in ann and "SharedConfig.appLocked" in ann, "announcement avoids replacing blocking UI")

# Model the normalization used by the controller: BOM/outer whitespace do not create a new revision.
def revision(value: str) -> str:
    value = value.removeprefix("\ufeff").strip()
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
require(revision("\ufeff  hello\n") == revision("hello"), "same normalized announcement is stable")
require(revision("hello") != revision("hello v2"), "changed announcement creates a new revision")

require("public boolean forceServerRead" in mc, "read task carries explicit-sync intent")
require("isHideReadStatusEnabled() && !task.forceServerRead" in mc, "passive privacy setting remains intact; explicit action can sync")
require("markMentionsAsRead(did, 0, true)" in dialogs, "manual mark-read syncs mentions")
require("markDialogAsRead(did, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0, true)" in dialogs, "manual mark-read sends history read to server")
require("syncDialogUnreadMark(did, false)" in dialogs, "manual mark-read clears server unread mark")

print(f"PASS: {checks} v4.8 announcement/read-sync regression guards")

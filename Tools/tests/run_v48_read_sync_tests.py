#!/usr/bin/env python3
"""Regression guards for v4.8 explicit read sync."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
mc = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java").read_text()
dialogs = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text()

checks = 0

def require(ok, label):
    global checks
    assert ok, label
    checks += 1

require("public boolean forceServerRead" in mc, "read task carries explicit-sync intent")
require("isHideReadStatusEnabled() && !task.forceServerRead" in mc, "passive privacy setting remains intact; explicit action can sync")
require("markMentionsAsRead(did, 0, true)" in dialogs, "manual mark-read syncs mentions")
require("markDialogAsRead(did, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0, true)" in dialogs, "manual mark-read sends history read to server")
require("syncDialogUnreadMark(did, false)" in dialogs, "manual mark-read clears server unread mark")

print(f"PASS: {checks} v4.8 read-sync regression guards")

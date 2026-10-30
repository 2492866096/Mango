#!/usr/bin/env python3
"""Regression guards for opt-in account-first quick forwarding."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHAT = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java").read_text()
OVERLAY = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/quickforward/QuickShareSelectorOverlayLayout.java").read_text()
DESTINATION = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/quickforward/QuickShareDestination.java").read_text()
AVATAR = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/quickforward/QuickShareAvatarCell.java").read_text()
EXTENSION = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ExtensionFeaturesActivity.java").read_text()
STRINGS_EN = (ROOT / "TMessagesProj/src/main/res/values/extension_tools_strings.xml").read_text()
STRINGS_ZH = (ROOT / "TMessagesProj/src/main/res/values-zh/extension_tools_strings.xml").read_text()


def block(source, marker):
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


checks = []
checks.append(("the Extension Features switch is off by default", '"mango_quick_forward_account_selection", false' in EXTENSION))
checks.append(("the extension switch saves its value", 'MangoSettings.setBoolean(currentAccount, "mango_quick_forward_account_selection", enabled)' in EXTENSION))
checks.append(("the quick-share gesture reads the opt-in setting", 'currentAccount, "mango_quick_forward_account_selection", false' in CHAT and "quickShareSelectorOverlay.open(cell, chooseSendingAccount)" in CHAT))
checks.append(("enabled mode builds account-only entries", 'chooseSendingAccount\n                ? buildAccountChoices()' in OVERLAY and 'new QuickShareDestination(account, selfUserId, true)' in OVERLAY))
checks.append(("disabled mode restricts quick targets to the current account", 'buildDestinationsForAccount(sourceAccount)' in OVERLAY))
checks.append(("account identity and recipient target are distinguished", 'public final boolean accountOnly' in DESTINATION and 'if (accountOnly)' in AVATAR))
checks.append(("choosing an account opens an account-scoped recipient picker", "openQuickShareRecipientPicker(accountChoice.account, messagesToForward)" in CHAT and "picker.setCurrentAccount(sendingAccount)" in CHAT))
checks.append(("recipient picker cannot switch into another account", 'args.putBoolean("allowSwitchAccount", false)' in CHAT))
checks.append(("the selected recipient is sent through the selected account", 'new QuickShareDestination(sendingAccount, dids.get(0).dialogId)' in CHAT and 'QuickShareForwarder.send(destination, messagesToForward)' in CHAT))
checks.append(("the feature has English and Chinese labels/help", 'name="ExtensionQuickForwardAccount"' in STRINGS_EN and 'name="ExtensionQuickForwardAccountInfo"' in STRINGS_EN and 'name="ExtensionQuickForwardAccount"' in STRINGS_ZH and 'name="ExtensionQuickForwardAccountInfo"' in STRINGS_ZH))

failed = [label for label, passed in checks if not passed]
if failed:
    raise SystemExit("FAIL: " + "; ".join(failed))
print(f"PASS: {len(checks)} account-first quick-forward regression guards")

package org.telegram.ui.Components.quickforward;

import androidx.annotation.NonNull;

/**
 * A quick-forward destination is scoped by account.  Dialog ids are only
 * unique inside one Telegram account, so keeping the account next to the id
 * prevents an id from account A accidentally being sent through account B.
 */
public final class QuickShareDestination {
    public final int account;
    public final long dialogId;
    /** True when this item represents the sending account, not a recipient. */
    public final boolean accountOnly;

    public QuickShareDestination(int account, long dialogId) {
        this(account, dialogId, false);
    }

    public QuickShareDestination(int account, long dialogId, boolean accountOnly) {
        this.account = account;
        this.dialogId = dialogId;
        this.accountOnly = accountOnly;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof QuickShareDestination)) {
            return false;
        }
        QuickShareDestination destination = (QuickShareDestination) object;
        return account == destination.account && dialogId == destination.dialogId && accountOnly == destination.accountOnly;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * account + (int) (dialogId ^ (dialogId >>> 32))) + (accountOnly ? 1 : 0);
    }

    @NonNull
    @Override
    public String toString() {
        return account + ":" + dialogId + (accountOnly ? ":account" : "");
    }
}

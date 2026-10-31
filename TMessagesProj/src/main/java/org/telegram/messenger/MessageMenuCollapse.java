package org.telegram.messenger;

/** Keeps long message menus compact without changing the user's action order. */
public final class MessageMenuCollapse {
    private MessageMenuCollapse() { }

    public static int visibleItemCount(int count, boolean enabled) {
        if (!enabled || count <= 6) return Math.max(0, count);
        // Include the More button in the main menu's approximately half-height budget.
        return Math.max(3, (count + 1) / 2 - 1);
    }
}

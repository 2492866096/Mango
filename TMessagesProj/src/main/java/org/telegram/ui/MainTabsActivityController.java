package org.telegram.ui;

public interface MainTabsActivityController {
    void setTabsVisible(boolean visible);

    /** True when the tab bar has its own compose key, so the page's floating buttons stay hidden. */
    default boolean ownsComposeButton() {
        return false;
    }
}

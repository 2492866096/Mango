package org.telegram.ui;

/**
 * Which screens draw with the skin. Each screen switches on its own; one that
 * is off keeps Telegram's and Mango's own interface, untouched.
 */
public final class SkinSwitch {
    /** The tab screen: the aluminium dock and its pages, 我 among them, in place of the glass bar. */
    public static final boolean MAIN_TABS = true;

    /** The chat list keeps Telegram's engine and draws its rows, tabs and header in the skin's style. */
    public static final boolean CHAT_LIST = true;

    /**
     * The chat screen's frame: the floating controls turn from glass to
     * aluminium and the input bar becomes the console with the knob.
     */
    public static final boolean CHAT_FRAME = true;

    /**
     * Profiles keep Telegram's engine and layout, drawn in the skin's style:
     * the squircle avatar and its story ring, the serif name, the actions as
     * aluminium keys, celadon for what is current.
     */
    public static final boolean PROFILE = true;

    private SkinSwitch() {
    }
}

package org.telegram.messenger;

/** Stable tab identities, including hidden tabs, independent from pager positions. */
public final class MainTabsOrder {
    public static final String SETTING_KEY = "main_tabs_order";

    private MainTabsOrder() { }

    public static int[] decode(String stored, int count) {
        int[] order = new int[count];
        boolean[] seen = new boolean[count];
        int size = 0;
        if (stored != null) {
            for (String part : stored.split(",")) {
                try {
                    int id = Integer.parseInt(part.trim());
                    if (id >= 0 && id < count && !seen[id]) {
                        seen[id] = true;
                        order[size++] = id;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        for (int id = 0; id < count; id++) if (!seen[id]) order[size++] = id;
        return order;
    }

    public static String encode(int[] order) {
        StringBuilder result = new StringBuilder();
        for (int id : order) {
            if (result.length() > 0) result.append(',');
            result.append(id);
        }
        return result.toString();
    }
}

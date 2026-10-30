package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    private static ComponentName defaultLaunchComponentName;
    private static ComponentName legacyDefaultLaunchComponentName;
    private static final String[] RETIRED_ICON_KEYS = {
            "StarryIcon", "ClassicRoundIcon", "ClassicBlueIcon"
    };

    public static void tryFixLauncherIconIfNeeded() {
        Context ctx = ApplicationLoader.applicationContext;
        LauncherIcon selectedIcon = null;
        int enabledCount = 0;
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                enabledCount++;
                if (selectedIcon == null || icon != LauncherIcon.DEFAULT) {
                    selectedIcon = icon;
                }
            }
        }
        if (enabledCount != 1) {
            setIcon(selectedIcon != null ? selectedIcon : LauncherIcon.DEFAULT);
        } else {
            enableLegacyDefaultComponent(ctx);
            restoreRetiredCompatibilityComponents(ctx);
        }
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        // Enable the replacement before disabling anything so the launcher never loses all entries.
        setComponentState(pm, icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        enableLegacyDefaultComponent(ctx);
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon) {
                setComponentState(pm, i.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            }
        }
        restoreRetiredCompatibilityComponents(ctx);
    }

    public static LauncherIcon getEnabledIcon() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon == LauncherIcon.DEFAULT) {
                continue;
            }
            if (isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.DEFAULT;
    }

    public static boolean isSelected(LauncherIcon icon) {
        return getEnabledIcon() == icon;
    }

    public static ComponentName getDefaultLaunchComponentName(Context ctx) {
        if (defaultLaunchComponentName == null) {
            defaultLaunchComponentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger.DefaultIcon");
        }
        return defaultLaunchComponentName;
    }

    private static void enableLegacyDefaultComponent(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        setComponentState(pm, getLegacyDefaultLaunchComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private static void restoreRetiredCompatibilityComponents(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        // These aliases no longer have launcher filters, but remain valid for existing shortcuts.
        // Remove overrides left by earlier icon selections and use their manifest default instead.
        for (String key : RETIRED_ICON_KEYS) {
            ComponentName component = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            setComponentState(pm, component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        }
    }

    private static void setComponentState(PackageManager pm, ComponentName component, int state) {
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
        }
    }

    private static ComponentName getLegacyDefaultLaunchComponentName(Context ctx) {
        if (legacyDefaultLaunchComponentName == null) {
            legacyDefaultLaunchComponentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger.DefaultLauncherIcon");
        }
        return legacyDefaultLaunchComponentName;
    }

    public enum LauncherIcon {
        // Keep the component keys stable so existing installations retain their icon selection.
        DEFAULT("DefaultIcon", R.mipmap.ic_launcher, R.mipmap.ic_launcher_round, R.drawable.mango_icon_galaxy, R.string.MangoAppIconGalaxy),
        VINTAGE("VintageIcon", R.mipmap.icon_6_launcher, R.mipmap.icon_6_launcher_round, R.drawable.mango_icon_pink, R.string.MangoAppIconPink),
        AQUA("AquaIcon", R.mipmap.icon_4_launcher, R.mipmap.icon_4_launcher_round, R.drawable.mango_icon_gold, R.string.MangoAppIconGold),
        PREMIUM("PremiumIcon", R.mipmap.icon_3_launcher, R.mipmap.icon_3_launcher_round, R.drawable.mango_icon_moon, R.string.MangoAppIconMoon),
        TURBO("TurboIcon", R.mipmap.icon_5_launcher, R.mipmap.icon_5_launcher_round, R.drawable.mango_icon_speed, R.string.MangoAppIconSpeed),
        NOX("NoxIcon", R.mipmap.icon_2_launcher, R.mipmap.icon_2_launcher_round, R.drawable.mango_icon_neon, R.string.MangoAppIconNeon);

        public final String key;
        public final int launcherIcon;
        public final int roundLauncherIcon;
        public final int previewIcon;
        public final int title;
        public final boolean premium = false;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int launcherIcon, int roundLauncherIcon, int previewIcon, int title) {
            this.key = key;
            this.launcherIcon = launcherIcon;
            this.roundLauncherIcon = roundLauncherIcon;
            this.previewIcon = previewIcon;
            this.title = title;
        }
    }
}

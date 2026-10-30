package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.LauncherIconController.LauncherIcon;

/** Exercises the production controller with an observable, manifest-aware PackageManager. */
public final class LauncherIconControllerTest {
    private static final String PREFIX = "org.telegram.messenger.";
    private static final String[] RETIRED = {"StarryIcon", "ClassicRoundIcon", "ClassicBlueIcon"};
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static PackageManager fresh() {
        ApplicationLoader.applicationContext = new Context();
        return ApplicationLoader.applicationContext.getPackageManager();
    }

    private static ComponentName component(String key) {
        return new ComponentName(ApplicationLoader.applicationContext.getPackageName(), PREFIX + key);
    }

    private static void seedOnly(PackageManager pm, LauncherIcon selected) {
        for (LauncherIcon icon : LauncherIcon.values()) {
            pm.seed(component(icon.key), icon == selected
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }
    }

    private static void checkUnique(PackageManager pm, LauncherIcon expected) {
        int count = 0;
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                count++;
                check(icon == expected, "Expected selected icon " + expected + ", found " + icon);
            }
            check(LauncherIconController.isSelected(icon) == (icon == expected), "Selection matches enabled component");
        }
        check(count == 1, "Exactly one of the six launchers enabled");
        check(pm.launcherCount() == 1, "Manifest-aware PackageManager exposes one launcher");
        check(LauncherIconController.getEnabledIcon() == expected, "Selected getter agrees");
    }

    private static void checkOperations(PackageManager pm, boolean preserveLauncher) {
        for (PackageManager.Operation operation : pm.operations) {
            check(operation.flags == PackageManager.DONT_KILL_APP, "Switch never kills the running process");
            if (preserveLauncher) {
                check(operation.launcherCount > 0, "Never disable every launcher during a valid switch");
            }
        }
    }

    private static void checkSwitchOrder(PackageManager pm, LauncherIcon selected) {
        checkSwitchOrder(pm, selected, false);
    }

    private static void checkSwitchOrder(PackageManager pm, LauncherIcon selected, boolean alreadyEnabled) {
        int enableIndex = -1;
        for (int i = 0; i < pm.operations.size(); i++) {
            PackageManager.Operation operation = pm.operations.get(i);
            if (operation.component.equals(component(selected.key))
                    && operation.state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                enableIndex = i;
                break;
            }
        }
        check(enableIndex >= 0 || alreadyEnabled, "Enables the requested component unless already enabled");
        for (int i = 0; i < pm.operations.size(); i++) {
            PackageManager.Operation operation = pm.operations.get(i);
            if (operation.state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                check(alreadyEnabled || i > enableIndex, "Requested icon is enabled before any previous icon is disabled");
            }
        }
    }

    private static void testCatalog() {
        LauncherIcon[] icons = LauncherIcon.values();
        String[] names = {"DEFAULT", "VINTAGE", "AQUA", "PREMIUM", "TURBO", "NOX"};
        String[] keys = {"DefaultIcon", "VintageIcon", "AquaIcon", "PremiumIcon", "TurboIcon", "NoxIcon"};
        int[] launchers = {R.mipmap.ic_launcher, R.mipmap.icon_6_launcher, R.mipmap.icon_4_launcher,
                R.mipmap.icon_3_launcher, R.mipmap.icon_5_launcher, R.mipmap.icon_2_launcher};
        int[] rounds = {R.mipmap.ic_launcher_round, R.mipmap.icon_6_launcher_round, R.mipmap.icon_4_launcher_round,
                R.mipmap.icon_3_launcher_round, R.mipmap.icon_5_launcher_round, R.mipmap.icon_2_launcher_round};
        int[] previews = {R.drawable.mango_icon_galaxy, R.drawable.mango_icon_pink, R.drawable.mango_icon_gold,
                R.drawable.mango_icon_moon, R.drawable.mango_icon_speed, R.drawable.mango_icon_neon};
        check(icons.length == 6, "Exactly the six supplied artworks are selectable");
        for (int i = 0; i < icons.length; i++) {
            check(icons[i].name().equals(names[i]), "Enum identity stays compatible");
            check(icons[i].key.equals(keys[i]), "Component identity stays compatible");
            check(!icons[i].premium, "All six icons are available without premium");
            check(icons[i].launcherIcon == launchers[i], "Artwork uses the correct launcher resource");
            check(icons[i].roundLauncherIcon == rounds[i], "Artwork uses the correct round launcher resource");
            check(icons[i].previewIcon == previews[i], "Preview uses the corresponding full artwork");
            check(icons[i].title != 0, "Every artwork has a display name");
        }
    }

    private static void testFreshInstall() {
        PackageManager pm = fresh();
        checkUnique(pm, LauncherIcon.DEFAULT);
        check(LauncherIconController.getDefaultLaunchComponentName(ApplicationLoader.applicationContext)
                .equals(component("DefaultIcon")), "Default launcher component is stable");
        LauncherIconController.tryFixLauncherIconIfNeeded();
        checkUnique(pm, LauncherIcon.DEFAULT);
        checkOperations(pm, true);
    }

    private static void testEverySwitch() {
        for (LauncherIcon oldIcon : LauncherIcon.values()) {
            for (LauncherIcon nextIcon : LauncherIcon.values()) {
                PackageManager pm = fresh();
                seedOnly(pm, oldIcon);
                LauncherIconController.setIcon(nextIcon);
                checkUnique(pm, nextIcon);
                checkSwitchOrder(pm, nextIcon, oldIcon == nextIcon);
                checkOperations(pm, true);
            }
        }
    }

    private static void testRepair() {
        PackageManager pm = fresh();
        seedOnly(pm, null);
        LauncherIconController.tryFixLauncherIconIfNeeded();
        checkUnique(pm, LauncherIcon.DEFAULT);
        checkSwitchOrder(pm, LauncherIcon.DEFAULT);
        checkOperations(pm, false);

        pm = fresh();
        for (LauncherIcon icon : LauncherIcon.values()) {
            pm.seed(component(icon.key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        }
        LauncherIconController.tryFixLauncherIconIfNeeded();
        LauncherIcon repaired = LauncherIconController.getEnabledIcon();
        check(repaired != LauncherIcon.DEFAULT, "Repair preserves a chosen nondefault icon");
        checkUnique(pm, repaired);
        checkOperations(pm, true);

        for (LauncherIcon selected : LauncherIcon.values()) {
            pm = fresh();
            seedOnly(pm, selected);
            LauncherIconController.tryFixLauncherIconIfNeeded();
            checkUnique(pm, selected);
            checkOperations(pm, true);
        }
    }

    private static void testRetiredMigration() {
        for (String retired : RETIRED) {
            for (int previousState : new int[]{PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED}) {
                PackageManager pm = fresh();
                seedOnly(pm, null);
                pm.seed(component(retired), previousState);
                LauncherIconController.tryFixLauncherIconIfNeeded();
                checkUnique(pm, LauncherIcon.DEFAULT);
                for (String alias : RETIRED) {
                    check(pm.getComponentEnabledSetting(component(alias)) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            "Retired overrides are reset, retaining explicit shortcuts: " + alias);
                    check(pm.isEffectivelyEnabled(component(alias)), "Retired shortcut component remains usable");
                }
                check(pm.isEffectivelyEnabled(component("DefaultLauncherIcon")), "Legacy default shortcut remains usable");
                checkOperations(pm, false);
            }
        }
        PackageManager pm = fresh();
        seedOnly(pm, LauncherIcon.VINTAGE);
        for (String alias : RETIRED) {
            pm.seed(component(alias), PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        }
        LauncherIconController.tryFixLauncherIconIfNeeded();
        checkUnique(pm, LauncherIcon.VINTAGE);
        for (String alias : RETIRED) {
            check(pm.getComponentEnabledSetting(component(alias)) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    "Retired overrides also migrate alongside a valid selection");
        }
        checkOperations(pm, true);
    }

    public static void main(String[] args) {
        testCatalog();
        testFreshInstall();
        testEverySwitch();
        testRepair();
        testRetiredMigration();
        System.out.println("PASS: production LauncherIconController (" + assertions + " assertions): "
                + "six free artworks, default selection, all 36 transitions, enable-before-disable, "
                + "DONT_KILL_APP, broken state repair and retired shortcut migration");
    }
}

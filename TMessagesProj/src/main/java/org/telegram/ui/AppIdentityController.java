package org.telegram.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

public class AppIdentityController {

    public static void resetToDefault() {
        LocaleController.resetCustomAppName();
        LauncherIconController.setIcon(LauncherIconController.LauncherIcon.DEFAULT);
    }

    public static void restartApp(Context context) {
        Context appContext = ApplicationLoader.applicationContext;
        if (appContext == null) {
            return;
        }
        ComponentName launcherComponent = LauncherIconController.getEnabledIcon().getComponentName(appContext);
        Intent intent = new Intent();
        intent.setComponent(launcherComponent);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Activity activity = AndroidUtilities.findActivity(context);
        if (activity != null) {
            activity.finishAffinity();
            activity.startActivity(intent);
        } else {
            appContext.startActivity(intent);
        }
        System.exit(0);
    }
}

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Internal alarm delivery; never accepts intents from other applications. */
public final class MangoMessageReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!MangoMessageReminderManager.ACTION_FIRE.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)) return;
        ApplicationLoader.postInitApplication();
        if (MangoMessageReminderManager.ACTION_FIRE.equals(action)) {
            MangoMessageReminderManager.fire(context, intent.getIntExtra("account", -1),
                    intent.getLongExtra("owner", 0), intent.getStringExtra("key") == null ? "" : intent.getStringExtra("key"));
        } else {
            MangoMessageReminderManager.rescheduleAll();
        }
    }
}

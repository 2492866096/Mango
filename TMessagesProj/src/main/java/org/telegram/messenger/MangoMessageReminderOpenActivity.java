package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.telegram.ui.LaunchActivity;

/** Validates the original account owner before opening a reminder after an account switch. */
public final class MangoMessageReminderOpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ApplicationLoader.postInitApplication();
        Intent source = getIntent();
        if (source != null) {
            int account = source.getIntExtra("currentAccount", -1);
            long owner = source.getLongExtra("reminderOwner", 0);
            if (UserConfig.isValidAccount(account) && owner != 0 && UserConfig.getInstance(account).getClientUserId() == owner) {
                Intent open = new Intent(this, LaunchActivity.class);
                open.setAction(source.getAction());
                open.putExtras(source);
                startActivity(open);
            }
        }
        finish();
    }
}

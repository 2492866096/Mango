package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RootCommandRunner;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

/** Hidden developer utility, never reachable through exported components or an intent. */
public class RootTerminalActivity extends BaseFragment {
    private RootCommandRunner runner;
    private TextView output;
    private Button run;
    private boolean destroyed;

    @Override public boolean onFragmentCreate() {
        return ApplicationLoader.applicationContext.getSharedPreferences("extension_device_tools", Context.MODE_PRIVATE)
                .getBoolean("root_terminal_unlocked", false) && super.onFragmentCreate();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.DeviceInfoRootTerminal));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        TextView description = new TextView(context);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setText(getString(R.string.RootTerminalInfo));
        layout.addView(description, new LinearLayout.LayoutParams(-1, -2));
        EditTextBoldCursor command = new EditTextBoldCursor(context);
        command.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        command.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextSize(16);
        command.setHint("id");
        command.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        command.setMinLines(2);
        command.setMaxLines(5);
        command.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8192)});
        layout.addView(command, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(context);
        run = new Button(context);
        run.setText(getString(R.string.RootTerminalRun));
        buttons.addView(run, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = new Button(context);
        cancel.setText(getString(R.string.Cancel));
        cancel.setOnClickListener(v -> { if (runner != null) runner.cancel(); });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(context);
        output = new TextView(context);
        output.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        scroll.addView(output, new ScrollView.LayoutParams(-1, -2));
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        run.setOnClickListener(v -> {
            String text = command.getText().toString().trim();
            if (text.isEmpty() || runner != null) return;
            output.setText(getString(R.string.RootTerminalChecking) + "\n");
            run.setEnabled(false);
            AndroidUtilities.hideKeyboard(command);
            runner = new RootCommandRunner();
            runner.start(text, new RootCommandRunner.Listener() {
                @Override public void onOutput(String value) {
                    AndroidUtilities.runOnUIThread(() -> { if (!destroyed) output.append(value); });
                }
                @Override public void onComplete(int code, boolean root, boolean cancelled, boolean timedOut, boolean truncated) {
                    AndroidUtilities.runOnUIThread(() -> {
                        runner = null;
                        if (destroyed) return;
                        String status = timedOut ? getString(R.string.RootTerminalTimedOut) : cancelled ? getString(R.string.RootTerminalCancelled)
                                : !root ? getString(R.string.RootTerminalNoRoot) : LocaleController.formatString(R.string.RootTerminalFinished, code);
                        output.append("\n" + status + (truncated ? "\n" + getString(R.string.RootTerminalTruncated) : ""));
                        run.setEnabled(true);
                    });
                }
            });
        });
        return fragmentView = layout;
    }

    @Override public void onFragmentDestroy() {
        destroyed = true;
        if (runner != null) runner.cancel();
        super.onFragmentDestroy();
    }
}

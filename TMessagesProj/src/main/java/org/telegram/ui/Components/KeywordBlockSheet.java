package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.KeywordMessageFilterManager;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/** Select exact phrases from the message instead of retyping advertising keywords. */
public final class KeywordBlockSheet {
    private static final int MAX_WORDS = 5, ADD_SELECTION = 0x4d4701;

    public static String messageText(MessageObject message) {
        if (message == null || message.messageOwner == null || message.isSponsored()) return "";
        // Use the original message/caption, not translated or locally edited display text.
        return message.messageOwner.message == null ? "" : message.messageOwner.message.trim();
    }

    public static void show(BaseFragment fragment, MessageObject message) {
        final int account = fragment.getCurrentAccount();
        final long owner = UserConfig.getInstance(account).getClientUserId();
        final String text = messageText(message);
        if (fragment.getParentActivity() == null || owner == 0 || text.isEmpty()) return;
        final Context context = fragment.getParentActivity();
        final Theme.ResourcesProvider provider = fragment.getResourceProvider();
        final ArrayList<String> selected = new ArrayList<>();
        final BottomSheet sheet = new BottomSheet(context, false, provider) {
            @Override
            protected boolean canDismissWithSwipe() {
                // A drag in this sheet selects text or scrolls; it must not dismiss it.
                return false;
            }
        };
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        TextView title = label(context, 20, Theme.key_dialogTextBlack, provider);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(getString(R.string.MangoKeywordSelect));
        content.addView(title, LayoutHelper.createLinear(-1, -2));
        TextView hint = label(context, 14, Theme.key_dialogTextGray3, provider);
        hint.setText(getString(R.string.MangoKeywordSelectHint));
        content.addView(hint, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 12));

        TextView source = label(context, 16, Theme.key_dialogTextBlack, provider);
        source.setText(text);
        source.setTextIsSelectable(true);
        source.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        source.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundGray, provider)));
        ScrollView scroll = new ScrollView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                // Keep text selection and source scrolling inside the source viewport.
                // The surrounding scroll is only the small-screen fallback for the controls.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                boolean handled = super.dispatchTouchEvent(event);
                if ((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                        && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return handled;
            }
        };
        scroll.setFillViewport(false);
        scroll.setNestedScrollingEnabled(true);
        scroll.addView(source);
        content.addView(scroll, LayoutHelper.createLinear(-1, Math.min(220, Math.max(100, AndroidUtilities.displaySize.y / AndroidUtilities.density / 4))));

        TextView count = label(context, 14, Theme.key_dialogTextBlue, provider);
        content.addView(count, LayoutHelper.createLinear(-1, -2, 0, 14, 0, 6));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        content.addView(words, LayoutHelper.createLinear(-1, -2));
        TextView submit = label(context, 16, Theme.key_featuredStickers_buttonText, provider);
        submit.setText(getString(R.string.MangoKeywordBlockSelected));
        submit.setGravity(Gravity.CENTER);
        submit.setTypeface(AndroidUtilities.bold());
        submit.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_featuredStickers_addButton, provider)));
        content.addView(submit, LayoutHelper.createLinear(-1, 48, 0, 14, 0, 0));
        Runnable refresh = () -> rebuildSelection(words, count, submit, selected, context, provider);
        source.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private void populate(Menu menu) {
                menu.clear();
                menu.add(0, ADD_SELECTION, 0, getString(R.string.KeywordFilterAddKeyword)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { populate(menu); return true; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { populate(menu); return true; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() != ADD_SELECTION) return false;
                if (addSelection(selected, text, source.getSelectionStart(), source.getSelectionEnd())) {
                    refresh.run();
                }
                mode.finish();
                return true;
            }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        });
        submit.setOnClickListener(v -> {
            if (fragment.isFinished || fragment.getCurrentAccount() != account ||
                    UserConfig.getInstance(account).getClientUserId() != owner || selected.isEmpty()) return;
            KeywordMessageFilterManager.getInstance(account).addKeywordsAndEnable(selected);
            sheet.dismiss();
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, getString(R.string.MangoKeywordBlocked)).show();
        });
        refresh.run();
        ScrollView sheetScroll = new ScrollView(context);
        sheetScroll.setFillViewport(false);
        sheetScroll.addView(content);
        sheet.setCustomView(sheetScroll);
        fragment.showDialog(sheet);
    }

    static boolean addSelection(ArrayList<String> selected, String text, int selectionStart, int selectionEnd) {
        if (selected == null || text == null || selected.size() >= MAX_WORDS) return false;
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        if (start < 0 || end <= start || end > text.length()) return false;
        String value = text.substring(start, end).trim();
        if (value.isEmpty()) return false;
        for (String word : selected) {
            if (word.equalsIgnoreCase(value)) return false;
        }
        selected.add(value);
        return true;
    }

    private static TextView label(Context context, int size, int color, Theme.ResourcesProvider provider) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(color, provider));
        return view;
    }

    private static void rebuildSelection(LinearLayout words, TextView count, TextView submit, ArrayList<String> selected,
                                         Context context, Theme.ResourcesProvider provider) {
        count.setText(LocaleController.formatString(R.string.MangoKeywordSelectedCount, selected.size(), MAX_WORDS));
        submit.setEnabled(!selected.isEmpty());
        submit.setAlpha(selected.isEmpty() ? .45f : 1f);
        words.removeAllViews();
        for (String value : selected) {
            TextView chip = label(context, 15, Theme.key_dialogTextBlue, provider);
            chip.setText(value + "  ×");
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
            chip.setContentDescription(value + ", " + getString(R.string.Remove));
            chip.setOnClickListener(v -> { selected.remove(value); rebuildSelection(words, count, submit, selected, context, provider); });
            words.addView(chip, LayoutHelper.createLinear(-1, -2));
        }
    }
}

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocalMessageInsightsRepository;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class InteractionStatisticsActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private long dialogId;
    private String title;
    private Calendar from = Calendar.getInstance(), through = Calendar.getInstance();
    private LocalMessageInsightsRepository.Statistics statistics;
    private boolean loading, failed;
    private int generation;

    public InteractionStatisticsActivity() { this(0, ""); }
    public InteractionStatisticsActivity(long dialogId, String title) {
        this.dialogId = dialogId;
        this.title = title;
        midnight(from);
        midnight(through);
        from.add(Calendar.DAY_OF_MONTH, -29);
    }

    private static void midnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.V4InteractionStatisticsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frame.addView(listView, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(listView);
        if (dialogId != 0) reload();
        return fragmentView = frame;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSettingsCell(1, getString(R.string.V4StatisticsPerson), dialogId == 0 ? getString(R.string.V4SelectLocalChat) : title));
        items.add(UItem.asSettingsCell(2, getString(R.string.V4StatisticsFrom), date(from)));
        items.add(UItem.asSettingsCell(3, getString(R.string.V4StatisticsThrough), date(through)));
        items.add(UItem.asButton(4, getString(R.string.V4StatisticsRefresh)).accent());
        items.add(UItem.asShadow(getString(R.string.V4StatisticsLocalInfo)));
        if (dialogId == 0) return;
        if (loading || failed || statistics == null) {
            items.add(UItem.asShadow(getString(loading ? R.string.Loading : R.string.V4LocalReadError)));
            return;
        }
        add(items, R.string.V4StatisticsSent, statistics.sent);
        add(items, R.string.V4StatisticsReceived, statistics.received);
        add(items, R.string.V4StatisticsActiveDays, statistics.activeDays);
        add(items, R.string.V4StatisticsCalendarDays, statistics.calendarDays);
        items.add(UItem.asSettingsCell(0, getString(R.string.V4StatisticsPerDay), String.format(Locale.getDefault(), "%.2f", statistics.perDay())));
        items.add(UItem.asSettingsCell(0, getString(R.string.V4StatisticsPerActiveDay), String.format(Locale.getDefault(), "%.2f", statistics.perActiveDay())));
        if (statistics.firstDate > 0) {
            items.add(UItem.asSettingsCell(0, getString(R.string.V4StatisticsFirst), LocaleController.formatDateChat(statistics.firstDate)));
            items.add(UItem.asSettingsCell(0, getString(R.string.V4StatisticsLast), LocaleController.formatDateChat(statistics.lastDate)));
        } else items.add(UItem.asShadow(getString(R.string.V4NoLocalMessages)));
    }

    private void add(ArrayList<UItem> items, int string, int value) { items.add(UItem.asSettingsCell(0, getString(string), Integer.toString(value))); }
    private String date(Calendar c) { return LocaleController.formatDateChat(c.getTimeInMillis() / 1000L); }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            LocalMessageDialogPickerActivity picker = new LocalMessageDialogPickerActivity(true, (id, name) -> {
                dialogId = id;
                title = name;
                reload();
            });
            picker.setCurrentAccount(currentAccount);
            presentFragment(picker);
        } else if (item.id == 2 || item.id == 3) {
            Calendar current = item.id == 2 ? from : through;
            DatePickerDialog picker = new DatePickerDialog(getParentActivity(), (v, year, month, day) -> {
                current.set(year, month, day);
                midnight(current);
                if (from.after(through)) {
                    if (item.id == 2) through = (Calendar) from.clone(); else from = (Calendar) through.clone();
                }
                reload();
            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH));
            Calendar minimum = Calendar.getInstance();
            minimum.clear();
            minimum.set(2013, Calendar.JANUARY, 1);
            picker.getDatePicker().setMinDate(minimum.getTimeInMillis());
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            showDialog(picker);
        } else if (item.id == 4) reload();
    }

    private void reload() {
        if (listView == null) return;
        int request = ++generation;
        failed = false;
        loading = dialogId != 0;
        listView.adapter.update(true);
        if (dialogId == 0) return;
        Calendar until = (Calendar) through.clone();
        until.add(Calendar.DAY_OF_MONTH, 1);
        LocalMessageInsightsRepository.statistics(currentAccount, dialogId, from.getTimeInMillis() / 1000L,
                until.getTimeInMillis() / 1000L, TimeZone.getDefault(), (result, error) -> {
                    if (isFinished || request != generation) return;
                    statistics = result;
                    failed = error;
                    loading = false;
                    listView.adapter.update(true);
                });
    }
}

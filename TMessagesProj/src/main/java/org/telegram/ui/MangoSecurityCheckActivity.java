package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Refreshes server security state for this fragment's account, never the global selected slot. */
public class MangoSecurityCheckActivity extends BaseFragment {
    private UniversalRecyclerView list;
    private TL_account.Password password;
    private String phoneVisibility;
    private boolean phoneLoading, phoneCheckFailed, passwordLoading;
    private int phoneRequest, phoneDiscoveryRequest, passwordRequest, generation;

    @Override public View createView(Context context) {
        actionBar.setTitle(getString(R.string.MangoSecurityCheck));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        list = new UniversalRecyclerView(this, this::fill, this::click, null);
        frame.addView(list, LayoutHelper.createFrame(-1, -1));
        actionBar.setAdaptiveBackground(list);
        return fragmentView = frame;
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    private void cancelRequests() {
        if (phoneRequest != 0) getConnectionsManager().cancelRequest(phoneRequest, true);
        if (phoneDiscoveryRequest != 0) getConnectionsManager().cancelRequest(phoneDiscoveryRequest, true);
        if (passwordRequest != 0) getConnectionsManager().cancelRequest(passwordRequest, true);
        phoneRequest = phoneDiscoveryRequest = passwordRequest = 0;
    }

    private void refresh() {
        cancelRequests();
        final int version = ++generation;
        phoneLoading = passwordLoading = true;
        phoneCheckFailed = false;
        phoneVisibility = null;
        password = null;
        update();
        // The phone editor saves both rules, so it must never open with a missing discovery rule.
        phoneRequest = requestPhonePrivacy(ContactsController.PRIVACY_RULES_TYPE_PHONE, version);
        phoneDiscoveryRequest = requestPhonePrivacy(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE, version);
        passwordRequest = getConnectionsManager().sendRequest(new TL_account.getPassword(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (version != generation || isFinished) return;
            passwordRequest = 0;
            passwordLoading = false;
            if (response instanceof TL_account.Password) password = (TL_account.Password) response;
            update();
        }));
        getConnectionsManager().bindRequestToGuid(passwordRequest, classGuid);
    }

    private int requestPhonePrivacy(int type, int version) {
        TL_account.getPrivacy request = new TL_account.getPrivacy();
        request.key = type == ContactsController.PRIVACY_RULES_TYPE_PHONE
                ? new TLRPC.TL_inputPrivacyKeyPhoneNumber() : new TLRPC.TL_inputPrivacyKeyAddedByPhone();
        int requestId = getConnectionsManager().sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (version != generation || isFinished) return;
            if (type == ContactsController.PRIVACY_RULES_TYPE_PHONE) phoneRequest = 0;
            else phoneDiscoveryRequest = 0;
            if (response instanceof TL_account.privacyRules) {
                TL_account.privacyRules result = (TL_account.privacyRules) response;
                getMessagesController().putUsers(result.users, false);
                getMessagesController().putChats(result.chats, false);
                getContactsController().setPrivacyRules(result.rules, type);
                if (type == ContactsController.PRIVACY_RULES_TYPE_PHONE) {
                    phoneVisibility = PrivacySettingsActivity.formatRulesString(getAccountInstance(), type);
                }
            } else {
                phoneCheckFailed = true;
            }
            phoneLoading = phoneRequest != 0 || phoneDiscoveryRequest != 0;
            update();
        }));
        getConnectionsManager().bindRequestToGuid(requestId, classGuid);
        return requestId;
    }

    private void update() { if (list != null) list.adapter.update(false); }

    private void fill(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.MangoSecurityCheckInfo)));
        items.add(UItem.asSettingsCell(1, getString(R.string.MangoSecurityPhone), phoneLoading
                ? getString(R.string.MangoSecurityLoading) : phoneCheckFailed || phoneVisibility == null
                ? getString(R.string.MangoSecurityFailed) : phoneVisibility));
        items.add(UItem.asSettingsCell(2, getString(R.string.MangoSecurityPassword), passwordLoading
                ? getString(R.string.MangoSecurityLoading) : password == null
                ? getString(R.string.MangoSecurityFailed) : getString(password.has_password ? R.string.PasswordOn : R.string.PasswordOff)));
        items.add(UItem.asButton(3, R.drawable.msg_retry, getString(R.string.MangoSecurityRefresh)));
    }

    private void click(UItem item, View view, int position, float x, float y) {
        BaseFragment page;
        if (item.id == 1) {
            if (phoneLoading) return;
            if (phoneCheckFailed || phoneVisibility == null) { refresh(); return; }
            page = new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE);
        } else if (item.id == 2) {
            if (passwordLoading) return;
            if (password == null) { refresh(); return; }
            if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, false)) {
                AlertsCreator.showUpdateAppAlert(getParentActivity(), getString(R.string.UpdateAppAlert), true);
                return;
            }
            if (password.has_password) {
                TwoStepVerificationActivity check = new TwoStepVerificationActivity(currentAccount);
                check.setPassword(password);
                page = check;
            } else {
                TwoStepVerificationActivity.initPasswordNewAlgo(password);
                int type = TextUtils.isEmpty(password.email_unconfirmed_pattern)
                        ? TwoStepVerificationSetupActivity.TYPE_INTRO : TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM;
                page = new TwoStepVerificationSetupActivity(currentAccount, type, password);
            }
        } else { if (item.id == 3) refresh(); return; }
        page.setCurrentAccount(currentAccount);
        presentFragment(page);
    }

    @Override public void onFragmentDestroy() {
        ++generation;
        cancelRequests();
        list = null;
        super.onFragmentDestroy();
    }
}

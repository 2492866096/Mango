#!/usr/bin/env python3
"""Run the actual security fragment against fake UI, accounts, and delayed RPCs.

No production method is reimplemented: the complete activity is compiled inside
a Java host. The doubles only replace Android views and external services.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JDK = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SOURCE = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/MangoSecurityCheckActivity.java'
production = SOURCE.read_text()
production = re.sub(r'^(?:package|import) .*;\n', '', production, flags=re.M)
production = production.replace('public class MangoSecurityCheckActivity', 'static class MangoSecurityCheckActivity', 1)

fixture = r'''
import java.util.*;

public class SecurityCheckRegression {
 static int checks, selectedAccount;
 static void check(boolean value, String message) {
  checks++;
  if (!value) throw new AssertionError(message);
 }
 static class Context {}
 static class View {}
 static class FrameLayout extends View {
  FrameLayout(Context context) {}
  void setBackgroundColor(int value) {}
  void addView(View view, Object layout) {}
 }
 static class TextUtils { static boolean isEmpty(String text) { return text == null || text.isEmpty(); } }
 static class Theme {
  static final int key_windowBackgroundGray=1;
  static int getColor(int key, Object provider) { return key; }
 }
 static class LayoutHelper { static Object createFrame(int width,int height) { return null; } }
 static class R {
  static class drawable { static final int ic_ab_back=1, msg_retry=2; }
  static class string {
   static final int MangoSecurityCheck=1, MangoSecurityCheckInfo=2, MangoSecurityPhone=3,
    MangoSecurityLoading=4, MangoSecurityFailed=5, MangoSecurityPassword=6,
    PasswordOn=7, PasswordOff=8, MangoSecurityRefresh=9, UpdateAppAlert=10;
  }
 }
 static String getString(int resource) { return "resource-" + resource; }
 static class ActionBar {
  static class ActionBarMenuOnItemClick { public void onItemClick(int id) {} }
  ActionBarMenuOnItemClick listener;
  void setTitle(String title) {}
  void setBackButtonImage(int image) {}
  void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick callback) { listener=callback; }
  void setAdaptiveBackground(View view) {}
 }
 interface Filler { void fill(ArrayList<UItem> items, UniversalAdapter adapter); }
 interface Clicker { void click(UItem item, View view, int position, float x, float y); }
 static class UItem {
  final int id; final String text, value;
  UItem(int id, String text, String value) { this.id=id; this.text=text; this.value=value; }
  static UItem asShadow(String text) { return new UItem(0,text,null); }
  static UItem asSettingsCell(int id,String text,String value) { return new UItem(id,text,value); }
  static UItem asButton(int id,int icon,String text) { return new UItem(id,text,null); }
 }
 static class UniversalAdapter {
  int updates; final Filler filler; ArrayList<UItem> items=new ArrayList<>();
  UniversalAdapter(Filler filler) { this.filler=filler; }
  void update(boolean animated) { updates++; items.clear(); filler.fill(items,this); }
 }
 static class UniversalRecyclerView extends View {
  final UniversalAdapter adapter;
  UniversalRecyclerView(BaseFragment fragment,Filler filler,Clicker clicker,Object resources) {
   adapter=new UniversalAdapter(filler);
  }
 }
 static class AndroidUtilities {
  static final ArrayDeque<Runnable> queue=new ArrayDeque<>();
  static void runOnUIThread(Runnable runnable) { queue.add(runnable); }
  static void drain() { while (!queue.isEmpty()) queue.remove().run(); }
 }
 static class TLRPC {
  static class TL_inputPrivacyKeyPhoneNumber {}
  static class TL_inputPrivacyKeyAddedByPhone {}
 }
 static class TL_account {
  static class getPrivacy { Object key; }
  static class getPassword {}
  static class privacyRules {
   ArrayList<String> users=new ArrayList<>(), chats=new ArrayList<>(), rules=new ArrayList<>();
   privacyRules(String rule) { rules.add(rule); users.add("user:"+rule); chats.add("chat:"+rule); }
  }
  static class Password {
   boolean has_password, supported=true;
   String email_unconfirmed_pattern;
   int initialized;
  }
 }
 interface Callback { void run(Object response,Object error); }
 static class Request {
  final int id; final Object request; final Callback callback;
  boolean canceled; int guid;
  Request(int id,Object request,Callback callback) { this.id=id; this.request=request; this.callback=callback; }
  void deliver(Object response,Object error) { callback.run(response,error); }
 }
 static class ConnectionsManager {
  int sequence, cancellations;
  ArrayList<Request> requests=new ArrayList<>();
  int sendRequest(Object request,Callback callback) {
   Request entry=new Request(++sequence,request,callback); requests.add(entry); return entry.id;
  }
  void cancelRequest(int id,boolean notify) {
   Request request=byId(id);
   check(notify,"cancellation asks the server to cancel");
   request.canceled=true; cancellations++;
  }
  void bindRequestToGuid(int id,int guid) { byId(id).guid=guid; }
  Request byId(int id) {
   for (Request request:requests) if (request.id==id) return request;
   throw new AssertionError("unknown request id " + id);
  }
 }
 static class ContactsController {
  static final int PRIVACY_RULES_TYPE_PHONE=7, PRIVACY_RULES_TYPE_ADDED_BY_PHONE=8;
  Map<Integer,ArrayList<String>> rules=new HashMap<>(); int writes;
  void setPrivacyRules(ArrayList<String> value,int type) { writes++; rules.put(type,value); }
 }
 static class MessagesController {
  int userWrites, chatWrites;
  void putUsers(ArrayList<String> users,boolean cache) { userWrites++; }
  void putChats(ArrayList<String> chats,boolean cache) { chatWrites++; }
 }
 static class AccountInstance {
  final int account;
  final ConnectionsManager connections=new ConnectionsManager();
  final ContactsController contacts=new ContactsController();
  final MessagesController messages=new MessagesController();
  AccountInstance(int account) { this.account=account; }
 }
 static AccountInstance[] accounts;
 static class BaseFragment {
  static int nextGuid=100;
  int currentAccount=selectedAccount, classGuid=++nextGuid;
  boolean isFinished; int resumes, destroys, finishes;
  View fragmentView; Object resourceProvider;
  ActionBar actionBar=new ActionBar();
  ArrayList<BaseFragment> opened=new ArrayList<>();
  View createView(Context context) { return null; }
  public void onResume() { resumes++; }
  public void onFragmentDestroy() { destroys++; isFinished=true; }
  void setCurrentAccount(int account) { currentAccount=account; }
  AccountInstance getAccountInstance() { return accounts[currentAccount]; }
  ConnectionsManager getConnectionsManager() { return getAccountInstance().connections; }
  ContactsController getContactsController() { return getAccountInstance().contacts; }
  MessagesController getMessagesController() { return getAccountInstance().messages; }
  Object getParentActivity() { return this; }
  void presentFragment(BaseFragment page) { opened.add(page); }
  void finishFragment() { finishes++; }
 }
 static class PrivacySettingsActivity {
  static String formatRulesString(AccountInstance account,int type) {
   return "account-" + account.account + ":" + account.contacts.rules.get(type).get(0);
  }
 }
 static class PrivacyControlActivity extends BaseFragment {
  final int type;
  PrivacyControlActivity(int type) { this.type=type; }
 }
 static class TwoStepVerificationActivity extends BaseFragment {
  final int constructorAccount; TL_account.Password password;
  TwoStepVerificationActivity(int account) { constructorAccount=account; currentAccount=account; }
  void setPassword(TL_account.Password value) { password=value; }
  static boolean canHandleCurrentPassword(TL_account.Password password,boolean login) { return password.supported; }
  static void initPasswordNewAlgo(TL_account.Password password) { password.initialized++; }
 }
 static class TwoStepVerificationSetupActivity extends BaseFragment {
  static final int TYPE_INTRO=1, TYPE_EMAIL_CONFIRM=2;
  final int constructorAccount, type; final TL_account.Password password;
  TwoStepVerificationSetupActivity(int account,int type,TL_account.Password password) {
   currentAccount=account; constructorAccount=account; this.type=type; this.password=password;
  }
 }
 static class AlertsCreator {
  static int updateAlerts;
  static void showUpdateAppAlert(Object parent,String message,boolean store) { updateAlerts++; }
 }

 /*PRODUCTION*/

 static void reset() {
  selectedAccount=0;
  accounts=new AccountInstance[]{new AccountInstance(0),new AccountInstance(1),new AccountInstance(2)};
  AndroidUtilities.queue.clear(); AlertsCreator.updateAlerts=0;
 }
 static MangoSecurityCheckActivity start(int account) {
  MangoSecurityCheckActivity page=new MangoSecurityCheckActivity();
  page.setCurrentAccount(account); page.createView(new Context()); page.onResume(); return page;
 }
 static String value(MangoSecurityCheckActivity page,int id) {
  ArrayList<UItem> rows=new ArrayList<>(); page.fill(rows,page.list.adapter);
  for (UItem row:rows) if (row.id==id) return row.value;
  throw new AssertionError("missing settings row " + id);
 }
 static void click(MangoSecurityCheckActivity page,int id) { page.click(new UItem(id,"",null),null,0,0,0); }
 static Request latest(MangoSecurityCheckActivity page,int kind) {
  ArrayList<Request> sent=page.getConnectionsManager().requests;
  for (int i=sent.size()-1;i>=0;i--) {
   Request request=sent.get(i);
   if (kind==2 && request.request instanceof TL_account.getPassword) return request;
   if (request.request instanceof TL_account.getPrivacy) {
    Object key=((TL_account.getPrivacy)request.request).key;
    if (kind==0 && key instanceof TLRPC.TL_inputPrivacyKeyPhoneNumber) return request;
    if (kind==1 && key instanceof TLRPC.TL_inputPrivacyKeyAddedByPhone) return request;
   }
  }
  throw new AssertionError("missing request kind " + kind);
 }
 static void reply(Request request,Object result) { request.deliver(result,null); AndroidUtilities.drain(); }
 static void fail(Request request) { request.deliver(null,new Object()); AndroidUtilities.drain(); }
 static TL_account.Password password(boolean enabled,String pendingEmail) {
  TL_account.Password result=new TL_account.Password();
  result.has_password=enabled; result.email_unconfirmed_pattern=pendingEmail; return result;
 }
 static void completePhone(MangoSecurityCheckActivity page,String visibility,String discovery) {
  reply(latest(page,0),new TL_account.privacyRules(visibility));
  reply(latest(page,1),new TL_account.privacyRules(discovery));
 }
 static void loadingAndSuccess() {
  reset(); MangoSecurityCheckActivity page=start(2);
  check(page.resumes==1,"resume calls its superclass");
  check(value(page,1).equals(getString(R.string.MangoSecurityLoading)),"phone row starts loading");
  check(value(page,2).equals(getString(R.string.MangoSecurityLoading)),"password row starts loading");
  check(page.getConnectionsManager().requests.size()==3,"refresh loads phone visibility, discovery, and 2FA");
  for (Request request:page.getConnectionsManager().requests)
   check(request.guid==page.classGuid,"every request is bound to the fragment lifetime");
  check(accounts[0].connections.requests.isEmpty() && accounts[1].connections.requests.isEmpty(),"refresh stays on the fragment account");
  click(page,1); click(page,2); click(page,-100);
  check(page.opened.isEmpty(),"loading rows and unknown items do not navigate");
  check(page.getConnectionsManager().requests.size()==3,"loading clicks do not restart requests");
  int updates=page.list.adapter.updates;
  latest(page,0).deliver(new TL_account.privacyRules("Nobody"),null);
  check(page.list.adapter.updates==updates,"server callback dispatches UI work rather than mutating from RPC thread");
  AndroidUtilities.drain();
  check(value(page,1).equals(getString(R.string.MangoSecurityLoading)),"visibility alone does not unlock phone settings");
  click(page,1); check(page.opened.isEmpty(),"phone click waits for discovery rules in a cold cache");
  reply(latest(page,1),new TL_account.privacyRules("Contacts"));
  check(value(page,1).equals("account-2:Nobody"),"phone value uses the owning account's fetched rules");
  check(accounts[2].contacts.rules.get(ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE).get(0).equals("Contacts"),"discovery rules are cached before navigation");
  check(accounts[2].messages.userWrites==2 && accounts[2].messages.chatWrites==2,"both responses populate referenced peers");
  check(accounts[0].contacts.writes==0 && accounts[1].contacts.writes==0,"privacy cache remains account-isolated");
  selectedAccount=1; click(page,1);
  BaseFragment opened=page.opened.get(0);
  check(opened instanceof PrivacyControlActivity && ((PrivacyControlActivity)opened).type==ContactsController.PRIVACY_RULES_TYPE_PHONE,"phone row opens its privacy editor");
  check(opened.currentAccount==2,"phone editor stays on the source account despite global account selection");
  TL_account.Password enabled=password(true,null); reply(latest(page,2),enabled);
  check(value(page,2).equals(getString(R.string.PasswordOn)),"enabled password is displayed");
  click(page,2); opened=page.opened.get(1);
  check(opened instanceof TwoStepVerificationActivity,"enabled password opens verification");
  TwoStepVerificationActivity verifier=(TwoStepVerificationActivity)opened;
  check(verifier.currentAccount==2 && verifier.constructorAccount==2,"verification constructor and destination use the source account");
  check(verifier.password==enabled,"verification receives the checked server password object");
  enabled.supported=false; click(page,2);
  check(page.opened.size()==2 && AlertsCreator.updateAlerts==1,"unsupported password algorithm displays update and does not navigate");
  page.actionBar.listener.onItemClick(-1); check(page.finishes==1,"back button finishes the fragment");
 }
 static void passwordSetupBranches() {
  for (String email:new String[]{null,"","m***@mail.example"}) {
   reset(); MangoSecurityCheckActivity page=start(2);
   TL_account.Password disabled=password(false,email); reply(latest(page,2),disabled);
   check(value(page,2).equals(getString(R.string.PasswordOff)),"disabled password is displayed independently of pending phone requests");
   selectedAccount=1; click(page,2);
   check(page.opened.size()==1 && page.opened.get(0) instanceof TwoStepVerificationSetupActivity,"disabled password opens setup");
   TwoStepVerificationSetupActivity setup=(TwoStepVerificationSetupActivity)page.opened.get(0);
   int expected=email==null || email.isEmpty() ? TwoStepVerificationSetupActivity.TYPE_INTRO : TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM;
   check(setup.type==expected,"pending recovery email selects confirmation; no email selects introduction");
   check(setup.currentAccount==2 && setup.constructorAccount==2,"setup retains the account before and after construction");
   check(setup.password==disabled && disabled.initialized==1,"setup receives initialized server password parameters");
   disabled.supported=false; click(page,2);
   check(page.opened.size()==1 && AlertsCreator.updateAlerts==1 && disabled.initialized==1,"unsupported setup parameters do not initialize or open another page");
  }
 }
 static void phoneFailuresAndOrdering() {
  for (int failedKind=0;failedKind<2;failedKind++) {
   for (boolean failFirst:new boolean[]{false,true}) {
    reset(); MangoSecurityCheckActivity page=start(2);
    Request failed=latest(page,failedKind), successful=latest(page,1-failedKind);
    if (failFirst) fail(failed); else reply(successful,new TL_account.privacyRules("Contacts"));
    click(page,1); check(page.opened.isEmpty(),"a partial phone result cannot open the privacy editor");
    check(page.getConnectionsManager().requests.size()==3,"partial result clicks wait for both requests");
    if (failFirst) reply(successful,new TL_account.privacyRules("Contacts")); else fail(failed);
    check(value(page,1).equals(getString(R.string.MangoSecurityFailed)),"either phone request failure marks the entire phone check failed");
    click(page,1);
    check(page.opened.isEmpty() && page.getConnectionsManager().requests.size()==6,"failed phone click retries all security checks without navigating");
    check(value(page,1).equals(getString(R.string.MangoSecurityLoading)),"phone retry returns to loading");
    completePhone(page,"Nobody","Contacts");
    check(value(page,1).equals("account-2:Nobody"),"successful retry clears previous phone failure");
    click(page,1); check(page.opened.size()==1,"successful retry unlocks the privacy editor");
   }
  }
 }
 static void passwordFailuresAndRefresh() {
  reset(); MangoSecurityCheckActivity page=start(2);
  fail(latest(page,2));
  check(value(page,2).equals(getString(R.string.MangoSecurityFailed)),"RPC password failure is visible");
  click(page,2);
  check(page.opened.isEmpty() && page.getConnectionsManager().requests.size()==6,"failed password click retries without navigating");
  reply(latest(page,2),new Object());
  check(value(page,2).equals(getString(R.string.MangoSecurityFailed)),"unexpected password response is handled as failure");
  click(page,3); completePhone(page,"Nobody","Contacts"); reply(latest(page,2),password(true,null));
  check(value(page,1).equals("account-2:Nobody") && value(page,2).equals(getString(R.string.PasswordOn)),"both rows recover after an explicit refresh");
  click(page,3);
  check(value(page,1).equals(getString(R.string.MangoSecurityLoading)) && value(page,2).equals(getString(R.string.MangoSecurityLoading)),"refresh hides old success while obtaining new server state");
  fail(latest(page,0)); fail(latest(page,1)); fail(latest(page,2));
  check(value(page,1).equals(getString(R.string.MangoSecurityFailed)) && value(page,2).equals(getString(R.string.MangoSecurityFailed)),"failed refresh does not retain stale success values");
 }
 static void staleResponsesAndLifecycle() {
  reset(); MangoSecurityCheckActivity page=start(2);
  Request[] old={latest(page,0),latest(page,1),latest(page,2)};
  old[0].deliver(new TL_account.privacyRules("OLD"),null);
  old[1].deliver(new TL_account.privacyRules("OLD"),null);
  old[2].deliver(password(true,null),null);
  page.onResume();
  int updates=page.list.adapter.updates;
  for (Request request:old) check(request.canceled,"refresh cancels previous in-flight requests");
  AndroidUtilities.drain();
  check(page.list.adapter.updates==updates,"already queued responses from an older generation are ignored");
  check(accounts[2].contacts.writes==0 && accounts[2].messages.userWrites==0,"old callbacks cannot mutate account caches");
  check(value(page,1).equals(getString(R.string.MangoSecurityLoading)) && value(page,2).equals(getString(R.string.MangoSecurityLoading)),"old callbacks cannot finish current loading state");
  Request currentPhone=latest(page,0), currentDiscovery=latest(page,1), currentPassword=latest(page,2);
  reply(currentDiscovery,new TL_account.privacyRules("Contacts"));
  check(value(page,1).equals(getString(R.string.MangoSecurityLoading)),"discovery-first completion waits for visibility");
  reply(currentPhone,new TL_account.privacyRules("Nobody")); reply(currentPassword,password(false,null));
  old[0].deliver(new TL_account.privacyRules("OLD"),null); old[1].deliver(null,new Object()); old[2].deliver(password(true,null),null); AndroidUtilities.drain();
  check(value(page,1).equals("account-2:Nobody") && value(page,2).equals(getString(R.string.PasswordOff)),"late old successes and failures cannot overwrite current results");
  click(page,3);
  check(!currentPhone.canceled && !currentDiscovery.canceled && !currentPassword.canceled,"completed request handles are cleared before refresh");
  Request[] last={latest(page,0),latest(page,1),latest(page,2)};
  UniversalAdapter adapter=page.list.adapter;
  int writes=accounts[2].contacts.writes;
  updates=adapter.updates;
  page.onFragmentDestroy();
  check(page.list==null && page.destroys==1,"destroy clears UI reference and calls superclass");
  for (Request request:last) check(request.canceled,"destroy cancels every security request");
  last[0].deliver(new TL_account.privacyRules("AFTER-DESTROY"),null);
  last[1].deliver(new TL_account.privacyRules("AFTER-DESTROY"),null);
  last[2].deliver(password(true,null),null); AndroidUtilities.drain();
  check(accounts[2].contacts.writes==writes && adapter.updates==updates,"destroyed fragment ignores late server callbacks");
  reset(); page=start(2); page.isFinished=true;
  completePhone(page,"FINISHED","FINISHED"); reply(latest(page,2),password(true,null));
  check(accounts[2].contacts.writes==0 && page.password==null,"finished fragment rejects callbacks even without a generation change");
 }
 static void independentAccountsAndMissingView() {
  reset(); MangoSecurityCheckActivity first=start(1), second=start(2);
  completePhone(first,"Nobody","Contacts"); reply(latest(first,2),password(true,null));
  completePhone(second,"Everybody","Everybody"); reply(latest(second,2),password(false,null));
  check(value(first,1).equals("account-1:Nobody") && value(second,1).equals("account-2:Everybody"),"simultaneous fragments do not share phone state");
  check(value(first,2).equals(getString(R.string.PasswordOn)) && value(second,2).equals(getString(R.string.PasswordOff)),"simultaneous fragments do not share password state");
  MangoSecurityCheckActivity headless=new MangoSecurityCheckActivity(); headless.setCurrentAccount(2);
  headless.refresh(); completePhone(headless,"Nobody","Contacts"); reply(latest(headless,2),password(true,null));
  check(headless.password!=null,"refresh and callbacks tolerate a missing view"); headless.onFragmentDestroy();
 }
 public static void main(String[] args) {
  loadingAndSuccess(); passwordSetupBranches(); phoneFailuresAndOrdering(); passwordFailuresAndRefresh();
  staleResponsesAndLifecycle(); independentAccountsAndMissingView();
  System.out.println("PASS: " + checks + " security lifecycle, retry, privacy cache, account, and 2FA behavior checks");
 }
}
'''

with tempfile.TemporaryDirectory(prefix='mango-security-tests-') as temporary:
    directory = Path(temporary)
    harness = directory / 'SecurityCheckRegression.java'

    def run(source, capture=False):
        harness.write_text(fixture.replace('/*PRODUCTION*/', source))
        subprocess.run([str(JDK / 'javac'), '-d', temporary, str(harness)], check=True)
        return subprocess.run([str(JDK / 'java'), '-cp', temporary, 'SecurityCheckRegression'],
                              capture_output=capture, text=True, check=not capture)

    run(production)
    mutations = [
        ('stale callback protection', 'version != generation || isFinished', 'isFinished', 'older generation'),
        ('destination account ownership', 'page.setCurrentAccount(currentAccount);', '', 'source account'),
        ('loading click guard', 'if (phoneLoading) return;', '', 'loading clicks'),
        ('phone discovery loading barrier',
         'phoneLoading = phoneRequest != 0 || phoneDiscoveryRequest != 0;',
         'phoneLoading = phoneRequest != 0;', 'visibility alone'),
    ]
    for name, original, replacement, failure in mutations:
        assert original in production, f'mutation anchor missing: {name}'
        result = run(production.replace(original, replacement), capture=True)
        assert result.returncode != 0 and failure in result.stderr, (name, result.stdout, result.stderr)
        print(f'PASS: rejected {name} mutation')

# BaseFragment.presentFragment does not inherit the source account. Follow-on
# password pages must explicitly carry it, including verification success,
# password creation, recovery-email confirmation, and cancellation routes.
routes = 0
for filename in ('TwoStepVerificationActivity.java', 'TwoStepVerificationSetupActivity.java'):
    source = (SOURCE.parent / filename).read_text()
    calls = list(re.finditer(r'new\s+(TwoStepVerification(?:Setup)?Activity)\s*\(\s*([^,)]*)', source))
    assert calls, f'no internal password routes found in {filename}'
    for call in calls:
        line = source.count('\n', 0, call.start()) + 1
        assert call.group(2).strip() == 'currentAccount', f'{filename}:{line} loses the source account'
        routes += 1
print(f'PASS: {routes} internal password-page routes retain the source account')

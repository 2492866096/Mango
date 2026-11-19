#!/usr/bin/env python3
"""Exercise production queue and adapter layout/mapping methods with offline fixtures."""
from pathlib import Path
import os, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
SOURCE = ROOT / 'TMessagesProj/src/main/java'

def method(text, marker):
    start = text.index(marker)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        if text[end] == '{': depth += 1
        elif text[end] == '}': depth -= 1
        end += 1
    return text[start:end]

adapter = (SOURCE / 'org/telegram/ui/Adapters/DialogsAdapter.java').read_text()
methods = '\n'.join(method(adapter, signature) for signature in (
    '    private boolean shouldShowPinnedDialogRows()',
    '    private void insertPinnedDialogRows()',
    '    private void addPinnedDialogPlaceholders(ArrayList<ItemInternal> items, int index)',
    '    public int fixPosition(int position)',
))
fixture = '''
import java.util.*;
public class PinnedRowsRegression {
 static class TLObject {}
 static class TLRPC {
  static class Dialog extends TLObject { long id; Dialog(long id) { this.id=id; } }
  static class TL_dialogFolder extends Dialog { TL_dialogFolder() { super(99); } }
 }
 static class DialogsActivity {
  static final int DIALOGS_TYPE_DEFAULT=0;
  ArrayList<TLRPC.Dialog> dialogs=new ArrayList<>();
  ArrayList<TLRPC.Dialog> getDialogsArray(int a,int b,int c,boolean d) {return dialogs;}
 }
 static class MessagesController {
  static class DialogFilter { boolean standard; DialogFilter(boolean b){standard=b;} boolean isDefault(){return standard;} }
  static final MessagesController INSTANCE=new MessagesController();
  static MessagesController getInstance(int account){return INSTANCE;}
  boolean isPromoDialog(long id,boolean b){return id==100;}
 }
 static class PinnedDialogLink {long placeholderId; PinnedDialogLink(long id){placeholderId=id;}}
 static class ItemInternal {TLRPC.Dialog dialog; ItemInternal(int t,TLRPC.Dialog d){dialog=d;}}
 int dialogsType, folderId, currentAccount;
 boolean isOnlySelect, dialogsListFrozen, isEmpty=true;
 final DialogsActivity parentFragment=new DialogsActivity();
 MessagesController.DialogFilter filter;
 final int VIEW_TYPE_DIALOG=0;
 ArrayList<ItemInternal> itemInternals=new ArrayList<>();
 ArrayList<PinnedDialogLink> links=new ArrayList<>(Arrays.asList(new PinnedDialogLink(-1000),new PinnedDialogLink(-1001)));
 MessagesController.DialogFilter getCurrentFilter(){return filter;}
 ArrayList<PinnedDialogLink> getPinnedDialogLinksSnapshot(){return links;}
 TLRPC.Dialog createPinnedDialog(long id){return new TLRPC.Dialog(id);}
 boolean isPinnedPlaceholderId(long id){return id<=-1000;}
 TLObject getItem(int p){return p<0||p>=itemInternals.size()?null:itemInternals.get(p).dialog;}
 void row(TLRPC.Dialog d){itemInternals.add(new ItemInternal(0,d));if(d!=null)parentFragment.dialogs.add(d);}
 static int checks;
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
''' + methods + '''
 public static void main(String[] args){
  PinnedRowsRegression a=new PinnedRowsRegression();
  a.row(new TLRPC.TL_dialogFolder());a.row(new TLRPC.Dialog(100));a.row(new TLRPC.Dialog(1));a.row(new TLRPC.Dialog(2));
  a.insertPinnedDialogRows();
  check(((TLRPC.Dialog)a.getItem(0)).id==99,"Archive stays first");
  check(((TLRPC.Dialog)a.getItem(1)).id==100,"Promoted server row stays before built-in pins");
  check(((TLRPC.Dialog)a.getItem(2)).id==-1000,"Pins precede normal chats");
  check(a.fixPosition(2)==-1,"Synthetic pin never maps to real dialog");
  check(a.fixPosition(4)==2&&a.fixPosition(5)==3,"Real rows map correctly after pins");
  check(a.fixPosition(-1)==-1&&a.fixPosition(99)==-1,"Out-of-range index rejected");
  check(!a.isEmpty,"Built-in rows make list visible");
  for(int type:new int[]{7,8}){
   PinnedRowsRegression b=new PinnedRowsRegression();b.dialogsType=type;b.filter=new MessagesController.DialogFilter(true);b.row(new TLRPC.Dialog(3));b.insertPinnedDialogRows();
   check(b.itemInternals.size()==3,"Default folder representation supports pins: "+type);
   b.filter=new MessagesController.DialogFilter(false);check(!b.shouldShowPinnedDialogRows(),"Custom filter excluded");
  }
  PinnedRowsRegression c=new PinnedRowsRegression();c.isOnlySelect=true;check(!c.shouldShowPinnedDialogRows(),"Selection picker excluded");
  c.isOnlySelect=false;c.folderId=1;check(!c.shouldShowPinnedDialogRows(),"Archive excluded");
  PinnedRowsRegression d=new PinnedRowsRegression();d.row(null);d.row(null);d.row(new TLRPC.Dialog(4));d.insertPinnedDialogRows();
  check(d.fixPosition(4)==0,"Hints/contact rows do not skew mapping");
  PinnedRowsRegression e=new PinnedRowsRegression();e.insertPinnedDialogRows();check(e.itemInternals.size()==2&&!e.isEmpty,"Fresh empty account shows built-in rows");
  System.out.println("PASS: "+checks+" production pinned-row layout and mapping checks");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='navigation-tests-') as directory:
    output = Path(directory)
    java = output / 'PinnedRowsRegression.java'
    java.write_text(fixture)
    subprocess.run([str(JAVA/'javac'), '-d', directory, str(SOURCE/'org/telegram/messenger/BatchLeaveQueue.java'), str(ROOT/'Tools/tests/BatchLeaveQueueTest.java'), str(java)],check=True)
    for name in ('BatchLeaveQueueTest','PinnedRowsRegression'):
        subprocess.run([str(JAVA/'java'),'-cp',directory,name],check=True)
    sdk=Path.home()/'Library/Android/sdk'
    aapt=sorted(sdk.glob('build-tools/*/aapt2'))[-1]
    for qualifier in ('values','values-zh'):
        subprocess.run([str(aapt),'compile',str(ROOT/'TMessagesProj/src/main/res'/qualifier/'v4_navigation.xml'),'-o',directory],check=True)
print('PASS: English and Chinese navigation resources compile')

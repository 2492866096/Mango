#!/usr/bin/env python3
"""Exercise the production queue and dialog position mapping with offline fixtures."""
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
methods = method(adapter, '    public int fixPosition(int position)')
fixture = '''
import java.util.*;
public class DialogPositionRegression {
 static class TLObject {}
 static class TLRPC {
  static class Dialog extends TLObject { long id; Dialog(long id) { this.id=id; } }
 }
 static class DialogsActivity {
  ArrayList<TLRPC.Dialog> dialogs=new ArrayList<>();
  ArrayList<TLRPC.Dialog> getDialogsArray(int a,int b,int c,boolean d) {return dialogs;}
 }
 static class ItemInternal {TLRPC.Dialog dialog; ItemInternal(TLRPC.Dialog d){dialog=d;}}
 int dialogsType, folderId, currentAccount;
 boolean dialogsListFrozen;
 final DialogsActivity parentFragment=new DialogsActivity();
 ArrayList<ItemInternal> itemInternals=new ArrayList<>();
 TLObject getItem(int p){return p<0||p>=itemInternals.size()?null:itemInternals.get(p).dialog;}
 void row(TLRPC.Dialog d){itemInternals.add(new ItemInternal(d));if(d!=null)parentFragment.dialogs.add(d);}
 static int checks;
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
''' + methods + '''
 public static void main(String[] args){
  DialogPositionRegression a=new DialogPositionRegression();
  a.row(null);a.row(null);a.row(new TLRPC.Dialog(4));a.row(new TLRPC.Dialog(5));
  check(a.fixPosition(0)==-1&&a.fixPosition(1)==-1,"Adapter-only rows never map to a dialog");
  check(a.fixPosition(2)==0&&a.fixPosition(3)==1,"Hints/contact rows do not skew mapping");
  check(a.fixPosition(-1)==-1&&a.fixPosition(99)==-1,"Out-of-range index rejected");
  System.out.println("PASS: "+checks+" production dialog position mapping checks");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='navigation-tests-') as directory:
    output = Path(directory)
    java = output / 'DialogPositionRegression.java'
    java.write_text(fixture)
    subprocess.run([str(JAVA/'javac'), '-d', directory, str(SOURCE/'org/telegram/messenger/BatchLeaveQueue.java'), str(ROOT/'Tools/tests/BatchLeaveQueueTest.java'), str(java)],check=True)
    for name in ('BatchLeaveQueueTest','DialogPositionRegression'):
        subprocess.run([str(JAVA/'java'),'-cp',directory,name],check=True)
    sdk=Path.home()/'Library/Android/sdk'
    aapt=sorted(sdk.glob('build-tools/*/aapt2'))[-1]
    for qualifier in ('values','values-zh'):
        subprocess.run([str(aapt),'compile',str(ROOT/'TMessagesProj/src/main/res'/qualifier/'v4_navigation.xml'),'-o',directory],check=True)
print('PASS: English and Chinese navigation resources compile')

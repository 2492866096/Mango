#!/usr/bin/env python3
"""Run the production selection extraction/copy callbacks against stale UI state."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Cells/TextSelectionHelper.java').read_text()

def method(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

word = method('    public static boolean isInterruptedCharacter(char c)')
assert 'Character.UnicodeScript' not in word, 'API 24-only UnicodeScript call returned'
fixture = '''
public class TextSelectionHelper {
 int selectionStart, selectionEnd, clears, hidden, copied;
 Object selectedView = new Object();
 CharSequence text;
 Callback callback = new Callback();
 class Callback { void onTextCopied() { copied++; } }
 static class TextUtils { static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; } }
 static class AndroidUtilities {
  static boolean succeeds = true;
  static String clipboard = "previous";
  static boolean addToClipboard(String value, boolean plain) {
   if (!succeeds) return false;
   clipboard = value;
   return true;
  }
 }
 boolean isInSelectionMode() { return selectionStart >= 0 && selectionEnd >= 0; }
 CharSequence getText(Object view, boolean maybe) { return view == null ? null : text; }
 void hideActions() { hidden++; }
 void clear(boolean instant) { clears++; }
 //METHODS
 static int checks;
 static void check(boolean value, String description) { checks++; if (!value) throw new AssertionError(description); }
 public static void main(String[] args) {
  TextSelectionHelper helper = new TextSelectionHelper();
  helper.text = "message"; helper.selectionStart = 0; helper.selectionEnd = 7;
  check("message".contentEquals(helper.getSelectedText()), "valid selection");
  helper.text = "edit";
  check(helper.getSelectedText() == null, "stale endpoint after message edit");
  helper.copyText(); check(helper.clears == 0 && AndroidUtilities.clipboard.equals("previous"), "stale selection never overwrites clipboard");
  helper.text = null; check(helper.getSelectedText() == null, "missing layout text");
  helper.text = "message"; helper.selectionStart = -1; check(helper.getSelectedText() == null, "selection cleared during toolbar callback");
  helper.selectionStart = 5; helper.selectionEnd = 2; check(helper.getSelectedText() == null, "crossed stale handles");
  helper.text = "  value  "; helper.selectionStart = 0; helper.selectionEnd = helper.text.length();
  AndroidUtilities.succeeds = false; helper.copyText(); check(helper.clears == 0 && helper.copied == 0, "clipboard rejection leaves selection active");
  AndroidUtilities.succeeds = true; helper.copyText(); check(helper.clears == 1 && helper.copied == 1 && AndroidUtilities.clipboard.equals("  value  "), "successful copy retains exact range");
  helper.text = "  \\t\\n"; helper.selectionEnd = helper.text.length(); helper.copyText(); check(helper.clears == 1, "blank copy ignored");
  for (char c : "中文字あア한ㄱﾊ".toCharArray()) check(!isInterruptedCharacter(c), "CJK selection does not require API 24");
  for (char c : "abZ19_é".toCharArray()) check(isInterruptedCharacter(c), "word selection preserved");
  check(!isInterruptedCharacter(' '), "space is a boundary");
  System.out.println("PASS: " + checks + " copy-crash behavior checks");
 }
}
'''
fixture = fixture.replace('//METHODS', '\n'.join([
    word, method('    protected CharSequence getSelectedText()'),
    method('    private void copyText()'),
    method('    static boolean hasNonWhitespaceContent(CharSequence text)')
]))
java = Path(os.environ.get('JAVA_HOME', '/Applications/Android Studio.app/Contents/jbr/Contents/Home')) / 'bin'
with tempfile.TemporaryDirectory(prefix='mango-copy-crash-') as directory:
    path = Path(directory) / 'TextSelectionHelper.java'
    path.write_text(fixture)
    subprocess.run([str(java / 'javac'), '-d', directory, str(path)], check=True)
    subprocess.run([str(java / 'java'), '-cp', directory, 'TextSelectionHelper'], check=True)

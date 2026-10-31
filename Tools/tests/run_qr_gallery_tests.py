#!/usr/bin/env python3
"""Exercise QR decoding and token-completion lifecycle from production scanner methods."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
java_sources = ROOT/'TMessagesProj/src/main/java'
source = (java_sources/'org/telegram/ui/CameraScanActivity.java').read_text()

def method(marker):
    start=source.index(marker);end=source.index('{',start)+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]

fixture=r'''
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.encoder.*;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
public class QrGalleryTest {
 static final int TYPE_QR_LOGIN=2;
 int currentType=TYPE_QR_LOGIN, galleryGeneration=1, finished, resumed, processed, delivered;
 boolean scannerDestroyed, galleryResultDelivered, recognized, qrLoading;
 String recognizedText;
 CameraView cameraView;
 Delegate delegate;
 static class CameraView { Object getCameraSession(){return null;} }
 static class CameraController {static CameraController getInstance(){return new CameraController();} void stopPreview(Object session){} }
 static class TextUtils {static boolean isEmpty(String value){return value==null||value.isEmpty();}}
 static class FileLog {static void e(Exception ignored){} }
 static class AndroidUtilities {
  static ArrayList<Runnable> queue=new ArrayList<>();
  static void runOnUIThread(Runnable action){queue.add(action);}
  static void flush(){while(!queue.isEmpty())queue.remove(0).run();}
 }
 interface Delegate {boolean processQr(String text,Runnable end);void didFindQr(String text);}
 void updateRecognized(){}
 void finishFragment(){finished++;}
 void resumeAfterGallery(boolean error){resumed++;}
 static class Bitmap {
  final int width,height;final int[] pixels;
  Bitmap(int w,int h,int[] p){width=w;height=h;pixels=p;}
  int getWidth(){return width;}int getHeight(){return height;}
  void getPixels(int[] dest,int offset,int stride,int x,int y,int w,int h){System.arraycopy(pixels,0,dest,0,pixels.length);}
 }
 //METHODS
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static Bitmap qr(String text,boolean invert) throws Exception {
  ByteMatrix data=Encoder.encode(text,ErrorCorrectionLevel.M).getMatrix();
  int scale=8,quiet=4,side=(data.getWidth()+quiet*2)*scale;
  int[] pixels=new int[side*side];Arrays.fill(pixels,invert?0xff000000:0xffffffff);
  for(int y=0;y<data.getHeight();y++)for(int x=0;x<data.getWidth();x++)if(data.get(x,y)==1)
   for(int dy=0;dy<scale;dy++)for(int dx=0;dx<scale;dx++)pixels[((y+quiet)*scale+dy)*side+(x+quiet)*scale+dx]=invert?0xffffffff:0xff000000;
  return new Bitmap(side,side,pixels);
 }
 public static void main(String[] args) throws Exception {
  String token="tg://login?token="+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
  QrGalleryTest t=new QrGalleryTest();
  check(t.isValidQrText(token),"valid login token");
  for(String bad:new String[]{"", "tg://login?token=", "tg://login?token=!", "tg://login?token=A", "https://example.org", "tg://login?token=QQ&other=x"})
   check(!t.isValidQrText(bad),"reject invalid login token");
  t.currentType=1;check(t.isValidQrText("https://example.org"),"normal scanner still accepts ordinary links");t.currentType=2;
  check(t.decodeGalleryQr(qr(token,false)).equals(token),"real QR decoder reads photo pixels");
  check(t.decodeGalleryQr(qr(token,true)).equals(token),"inverted QR photo decoded");
  final Runnable[] done={null};
  t.delegate=new Delegate(){public boolean processQr(String text,Runnable end){t.processed++;done[0]=end;return true;}public void didFindQr(String text){t.delivered++;}};
  t.processGalleryQr(token,1);AndroidUtilities.flush();
  check(t.processed==1&&t.delivered==0&&t.finished==0,"wait for accept-token completion before didFindQr");
  done[0].run();done[0].run();AndroidUtilities.flush();
  check(t.delivered==1&&t.finished==1,"duplicate asynchronous callback delivered only once");
  QrGalleryTest sync=new QrGalleryTest();
  sync.delegate=new Delegate(){public boolean processQr(String text,Runnable end){end.run();return false;}public void didFindQr(String text){sync.delivered++;}};
  sync.processGalleryQr(token,1);AndroidUtilities.flush();check(sync.delivered==1&&sync.finished==1,"synchronous completion plus false return still only delivers once");
  QrGalleryTest destroyed=new QrGalleryTest();
  destroyed.delegate=new Delegate(){public boolean processQr(String text,Runnable end){done[0]=end;return true;}public void didFindQr(String text){destroyed.delivered++;}};
  destroyed.processGalleryQr(token,1);destroyed.scannerDestroyed=true;done[0].run();AndroidUtilities.flush();
  check(destroyed.delivered==0&&destroyed.finished==0,"dismissed scanner ignores pending token callback");
  QrGalleryTest changed=new QrGalleryTest();
  changed.delegate=new Delegate(){public boolean processQr(String text,Runnable end){done[0]=end;return true;}public void didFindQr(String text){changed.delivered++;}};
  changed.processGalleryQr(token,1);changed.galleryGeneration++;done[0].run();AndroidUtilities.flush();check(changed.delivered==0,"stale gallery attempt cannot deliver");
  System.out.println("PASS: "+checks+" QR gallery decode/lifecycle behavior checks");
 }
}
'''
fixture=fixture.replace('//METHODS','\n'.join([method('    private boolean isValidQrText('),method('    private String decodeGalleryQr('),method('    private void processGalleryQr(')]))
# The Android Base64 API is a tiny compatibility double; all QR encoder/decoder code is real repository code.
base64='''package android.util; public class Base64 { public static final int URL_SAFE=8; public static byte[] decode(String text,int flags){return java.util.Base64.getUrlDecoder().decode(text);} }'''
java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))/'bin'
with tempfile.TemporaryDirectory(prefix='mango-qr-gallery-') as directory:
    d=Path(directory);(d/'QrGalleryTest.java').write_text(fixture)
    (d/'android/util').mkdir(parents=True);(d/'android/util/Base64.java').write_text(base64)
    subprocess.run([str(java/'javac'),'-d',directory,'-sourcepath',str(java_sources)+os.pathsep+directory,str(d/'QrGalleryTest.java'),str(d/'android/util/Base64.java')],check=True)
    subprocess.run([str(java/'java'),'-cp',directory,'QrGalleryTest'],check=True)
# Wiring and lifetime contracts not accessible without an Android Activity.
assert 'Intent.ACTION_OPEN_DOCUMENT' in source and 'READ_MEDIA_IMAGES' not in source
assert 'currentType == TYPE_QR_LOGIN && !text.startsWith' not in source
assert 'MAX_GALLERY_QR_SIZE = 2048' in source
assert 'if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();' in source
assert 'clearGalleryPicker();' in method('    public void onFragmentDestroy()')
sessions = (java_sources/'org/telegram/ui/SessionsActivity.java').read_text()
assert 'getParentActivity(), true, CameraScanActivity.TYPE_QR_LOGIN' in sessions
assert '.setNeutralButton(LocaleController.getString(R.string.MangoQrGallery)' in sessions
assert 'fragment.fragmentView.post(fragment::openGalleryPicker)' in source
assert source.count('needGalleryButton && currentType != TYPE_QR_LOGIN') == 2
print('PASS: 9 QR gallery wiring, permission fallback and cleanup guards')

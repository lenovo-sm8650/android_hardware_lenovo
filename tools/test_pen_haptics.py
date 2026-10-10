"""Exercise production PenHaptics and StylusHandwritingState against a recording GATT."""
from pathlib import Path
import subprocess
import tempfile

top = Path(__file__).resolve().parents[3]
sources = {
    'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
 public static final Queue<Runnable> queue = new ArrayDeque<>();
 public boolean post(Runnable r) { queue.add(r); return true; }
 public static void drain() { while (!queue.isEmpty()) queue.remove().run(); }
}''',
    'android/view/Display.java': 'package android.view; public class Display { public static final int DEFAULT_DISPLAY=0, INVALID_DISPLAY=-1; }',
    'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
public static final int TOOL_TYPE_STYLUS=2, TOOL_TYPE_ERASER=4,
 ACTION_DOWN=0, ACTION_UP=1, ACTION_HOVER_ENTER=9, ACTION_HOVER_EXIT=10;
}''',
    'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver { public void registerContentObserver(Object u,boolean b,Object o) {} }''',
    'android/content/Context.java': '''package android.content;
public class Context { final ContentResolver cr=new ContentResolver(); public ContentResolver getContentResolver(){return cr;} }''',
    'android/database/ContentObserver.java': 'package android.database; public class ContentObserver {}',
    'android/text/TextUtils.java': 'package android.text; public class TextUtils { public static boolean isEmpty(String s){return s==null||s.isEmpty();} }',
    'android/util/Log.java': 'package android.util; public class Log { public static int d(String t,String m){return 0;} }',
    'android/provider/Settings.java': '''package android.provider;
import android.content.ContentResolver;
import java.util.*;
public class Settings { public static class Global {
 public static final Map<String,String> values=new HashMap<>();
 public static Object getUriFor(String k){return k;}
 public static int getInt(ContentResolver c,String k,int d){return values.containsKey(k)?Integer.parseInt(values.get(k)):d;}
 public static String getString(ContentResolver c,String k){return values.get(k);}
 public static boolean putInt(ContentResolver c,String k,int v){values.put(k,""+v);return true;}
 public static boolean putString(ContentResolver c,String k,String v){values.put(k,v);return true;}
}}''',
    'com/tb520fu/input/Safe.java': '''package com.tb520fu.input;
import android.database.ContentObserver; import android.os.Handler;
public class Safe { public static java.util.function.Consumer<Object> changed;
 public static ContentObserver observer(Handler h,String n,java.util.function.Consumer<Object> c){changed=c;return new ContentObserver();}
}''',
    'com/tb520fu/input/LenovoHal.java': '''package com.tb520fu.input;
public class LenovoHal { static int value; static int getHaptics(){return value;} static void setHaptics(int v){value=v;} }''',
    'com/tb520fu/input/PenController.java': 'package com.tb520fu.input; public class PenController { static final int TYPE_PARKER_2=2; }',
    'com/tb520fu/input/PenGatt.java': '''package com.tb520fu.input;
import java.util.*;
public class PenGatt {
 static final int HAPTIC_SERVICE=1,HAPTIC_SWITCH=2,HAPTIC_INFO_NOTIFY=3,HAPTIC_REQ_INFO=4,HAPTIC_CONTINUOUS=5,HAPTIC_IMPACT=6;
 final List<byte[]> continuous=new ArrayList<>();
 boolean has(int s,int c){return true;} boolean isReady(){return true;}
 void setNotify(int s,int c,boolean b){}
 void write(int s,int c,byte[] v){if(c==HAPTIC_CONTINUOUS)continuous.add(v.clone());}
 void write(int s,int c,byte[] v,String tag){write(s,c,v);}
}''',
    'com/tb520fu/input/HapticsTest.java': '''package com.tb520fu.input;
import android.os.Handler; import android.content.Context; import android.provider.Settings;
import com.android.internal.inputmethod.StylusHandwritingState;
public class HapticsTest {
 static final String GBOARD="com.google.android.inputmethod.latin";
 static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
 public static void main(String[] args) {
  PenHaptics h=new PenHaptics(new Context(),new Handler()); PenGatt g=new PenGatt();
  h.start();Handler.drain();h.onGattReady(g,2);
  h.onStylusEvent(0,2,0,"com.android.settings");
  check(g.continuous.isEmpty(),"ordinary text-field tapping must not arm Gboard");
  StylusHandwritingState.start("other.ime",0);Handler.drain();
  check(g.continuous.isEmpty(),"other IME must not enable Gboard feedback");
  StylusHandwritingState.start(GBOARD,1);Handler.drain();
  check(g.continuous.isEmpty(),"another display must not vibrate tablet pen");
  StylusHandwritingState.start(GBOARD,0);Handler.drain();
  check(g.continuous.size()==1 && g.continuous.get(0)[0]==32,"first stroke armed on session start without another DOWN");
  StylusHandwritingState.start(GBOARD,0);Handler.drain();
  check(g.continuous.size()==1,"repeated start is idempotent");
  StylusHandwritingState.finish();Handler.drain();
  check(g.continuous.size()==2 && g.continuous.get(1)[0]==0,"session end sends explicit stop");
  h.onStylusEvent(0,2,0,"com.android.settings");
  check(g.continuous.size()==2,"ordinary use after finish stays off");
  StylusHandwritingState.start(GBOARD,0);StylusHandwritingState.finish();Handler.drain();
  check(g.continuous.size()==2,"queued start cannot arm after a fast reset");
  Settings.Global.values.put("pen_haptic_feedback","0");Safe.changed.accept(null);
  StylusHandwritingState.start(GBOARD,0);Handler.drain();
  check(g.continuous.size()==2,"feedback master OFF wins over handwriting");
  Settings.Global.values.put("pen_haptic_feedback","1");Safe.changed.accept(null);
  check(g.continuous.size()==3 && g.continuous.get(2)[0]==32,"reenabling during session rearms");
  Settings.Global.values.put("pen_haptic_sound","0");Safe.changed.accept(null);
  check(g.continuous.get(g.continuous.size()-1)[0]==37,"session honors sound setting");
  h.onPenDisconnected();PenGatt reconnected=new PenGatt();h.onGattReady(reconnected,2);
  check(reconnected.continuous.size()==1,"reconnect rearms current session");
  StylusHandwritingState.finish();Handler.drain();
  check(reconnected.continuous.get(1)[0]==0,"reconnect session also stops");
  Settings.Global.values.put("pen_haptic_packages","drawing.app");Safe.changed.accept(null);
  h.onStylusEvent(0,2,0,"drawing.app");
  check(reconnected.continuous.get(2)[0]==37,"existing app whitelist remains functional");
  h.setContinuous(34,3,1);
  h.onStylusEvent(0,2,0,"drawing.app");
  check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==37,
      "next app stroke must restore selected brush after an SDK/preview override");
  h.stop(0);
  h.onStylusEvent(0,2,0,"drawing.app");
  check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==37,
      "next app stroke must rearm after a preview stop");
  h.onStylusEvent(0,4,0,"drawing.app");
  check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==40,
      "eraser stroke must replace the armed pen waveform");
  h.onStylusEvent(0,2,0,"drawing.app");
  check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==37,
      "pen stroke must restore the selected brush after erasing");
  for (int brush : new int[] {32,33,34,36}) {
   Settings.Global.values.put("pen_haptic_brush",""+brush);Safe.changed.accept(null);
   h.onStylusEvent(0,2,0,"drawing.app");
   check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==brush+5,
       "allowlisted app must use brush " + brush);
   StylusHandwritingState.start(GBOARD,0);Handler.drain();
   check(reconnected.continuous.get(reconnected.continuous.size()-1)[0]==brush+5,
       "Gboard session must use brush " + brush);
   StylusHandwritingState.finish();Handler.drain();
  }
  System.out.println("PASS: 26 session/haptic and brush transition assertions");
 }
}''',
}

with tempfile.TemporaryDirectory(prefix='pen-haptics-test-') as directory:
    work = Path(directory)
    for name, source in sources.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    production = [
        top / 'frameworks/base/core/java/com/android/internal/inputmethod/StylusHandwritingState.java',
        top / 'hardware/lenovo/input/src/com/tb520fu/input/PenHaptics.java',
    ]
    jdk = top / 'prebuilts/jdk/jdk21/linux-x86/bin'
    subprocess.run([str(jdk / 'javac'), '-d', str(work), *map(str, work.rglob('*.java')), *map(str, production)], check=True)
    subprocess.run([str(jdk / 'java'), '-cp', str(work), 'com.tb520fu.input.HapticsTest'], check=True)

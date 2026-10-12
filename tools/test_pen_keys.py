"""Run production PenKeys against recording Android services (no device needed).

--before compiles HEAD's PenKeys to reproduce the swallowed double-tap regression.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

top = Path(__file__).resolve().parents[3]
sources = {
    'android/content/ContentResolver.java': 'package android.content; public class ContentResolver {}',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
import java.util.*; public class PackageManager {
 public final Map<Integer,String[]> packages = new HashMap<>();
 public String[] getPackagesForUid(int uid) { return packages.get(uid); }
}''',
    'android/content/Context.java': '''package android.content;
import java.util.*; import android.content.pm.PackageManager;
import android.os.*; import android.hardware.input.InputManager;
public class Context {
 public final List<Intent> broadcasts=new ArrayList<>();
 public final List<UserHandle> users=new ArrayList<>();
 public final PackageManager pm=new PackageManager();
 public final InputManager input=new InputManager();
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public PackageManager getPackageManager(){return pm;}
 public <T> T getSystemService(Class<T> c){return c.cast(c==InputManager.class?input:new PowerManager());}
 public void sendBroadcastAsUser(Intent i, UserHandle u){broadcasts.add(i);users.add(u);}
}''',
    'android/content/Intent.java': '''package android.content;
import java.util.*; public class Intent {
 public static final int FLAG_RECEIVER_FOREGROUND=0x10000000, FLAG_RECEIVER_INCLUDE_BACKGROUND=0x01000000;
 public final String action; public String pkg; public int flags;
 public final Map<String,Object> extras=new HashMap<>();
 public Intent(String a){action=a;} public Intent addFlags(int f){flags|=f;return this;}
 public Intent setPackage(String p){pkg=p;return this;}
 public Intent putExtra(String k, Object v){extras.put(k,v);return this;}
}''',
    'android/hardware/input/InputManager.java': '''package android.hardware.input;
import java.util.*; import android.view.KeyEvent;
public class InputManager { public static final int INJECT_INPUT_EVENT_MODE_ASYNC=0;
 public final List<KeyEvent> events=new ArrayList<>();
 public boolean injectInputEvent(KeyEvent e,int m){events.add(e);return true;}
}''',
    'android/os/Handler.java': '''package android.os; import java.util.*;
public class Handler { public final Queue<Runnable> queue=new ArrayDeque<>();
 public void post(Runnable r){queue.add(r);} public void drain(){while(!queue.isEmpty())queue.remove().run();}
}''',
    'android/os/PowerManager.java': 'package android.os; public class PowerManager {public boolean isInteractive(){return true;}}',
    'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long uptimeMillis(){return 100;}}',
    'android/os/UserHandle.java': '''package android.os; public class UserHandle {
 public static final UserHandle CURRENT_OR_SELF=new UserHandle(-3); public final int id;
 public UserHandle(int v){id=v;} public static UserHandle getUserHandleForUid(int u){return new UserHandle(u/100000);}
}''',
    'android/provider/Settings.java': '''package android.provider;
import android.content.ContentResolver;
public class Settings {public static class Global {public static int mode=1,copyPaste=0;
 public static int getInt(ContentResolver c,String k,int d){return k.equals("pen_touch_film_tap_two")?mode:copyPaste;}
}}''',
    'android/util/Log.java': 'package android.util; public class Log {public static int d(String t,String m){return 0;}}',
    'android/util/SparseBooleanArray.java': '''package android.util; import java.util.*;
public class SparseBooleanArray {final Map<Integer,Boolean> map=new HashMap<>();
 public void put(int k,boolean v){map.put(k,v);} public boolean get(int k){return map.getOrDefault(k,false);}
 public void delete(int k){map.remove(k);}}''',
    'android/view/InputDevice.java': '''package android.view; public class InputDevice {
 public static final int SOURCE_KEYBOARD=0x101; public final int vendor;
 public InputDevice(int v){vendor=v;} public int getVendorId(){return vendor;}
}''',
    'android/view/KeyCharacterMap.java': 'package android.view; public class KeyCharacterMap {public static final int VIRTUAL_KEYBOARD=-1;}',
    'android/view/KeyEvent.java': '''package android.view; public class KeyEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,FLAG_CANCELED=0x20,KEYCODE_PEN_TWO_CLICK=601,
 KEYCODE_F13=326,KEYCODE_F14=327,KEYCODE_F15=328,KEYCODE_F16=329,KEYCODE_F17=330,
 KEYCODE_F18=331,KEYCODE_F19=332,KEYCODE_F20=333,KEYCODE_F21=334,KEYCODE_F22=335,KEYCODE_F23=336,
 KEYCODE_COPY=278,KEYCODE_PASTE=279;
 final long down,time; final int action,code,repeat,meta,device,scan,flags,source; int display;
 public KeyEvent(long d,long t,int a,int c,int r,int m,int i,int s,int f,int src){
 down=d;time=t;action=a;code=c;repeat=r;meta=m;device=i;scan=s;flags=f;source=src;display=-1;}
 public long getDownTime(){return down;} public long getEventTime(){return time;}
 public int getAction(){return action;} public int getKeyCode(){return code;}
 public int getRepeatCount(){return repeat;} public int getMetaState(){return meta;}
 public int getDeviceId(){return device;} public int getScanCode(){return scan;}
 public int getFlags(){return flags;} public int getSource(){return source;}
 public void setDisplayId(int d){display=d;} public int getDisplayId(){return display;} public boolean isCanceled(){return (flags&FLAG_CANCELED)!=0;}
 public InputDevice getDevice(){return device<0?null:new InputDevice(device==8?0x1234:0x17ef);}
}''',
    'com/tb520fu/input/Safe.java': '''package com.tb520fu.input;
import android.os.Handler; public class Safe {public static void post(Handler h,String s,Runnable r){h.post(r);}}''',
    'com/tb520fu/input/PenController.java': 'package com.tb520fu.input; public class PenController {public void setLost(boolean lost){}}',
    'com/tb520fu/input/PenKeysTest.java': '''package com.tb520fu.input;
import android.content.*; import android.os.*; import android.view.*; import android.provider.Settings;
public class PenKeysTest {
 static int checks; static Context c; static Handler h; static PenKeys keys;
 static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void reset(int mode){c=new Context();h=new Handler();keys=new PenKeys(c,h,new PenController());Settings.Global.mode=mode;}
 static KeyEvent event(int action,int code,int device,int repeat,int flags){KeyEvent e=new KeyEvent(50,60+action,action,code,repeat,3,device,123,flags,0x101);e.setDisplayId(2);return e;}
 static void key(int action){check(keys.handle(event(action,327,7,0,0)),"physical double tap consumed");h.drain();}
 static boolean fallback(KeyEvent e,int uid) throws Exception {
  return (boolean)PenKeys.class.getDeclaredMethod("handleUnhandled",KeyEvent.class,int.class).invoke(keys,e,uid);
 }
 static KeyEvent app(int action){return event(action,601,7,0,0);}
 public static void main(String[] args) throws Exception {
  reset(1);key(0);key(1);
  check(c.input.events.size()==2,"REGRESSION: app must receive both halves of double tap, not only PenService broadcast");
  check(c.broadcasts.isEmpty(),"no fallback before app gets a chance");
  for(int a=0;a<2;a++) {KeyEvent e=c.input.events.get(a);
   check(e.getKeyCode()==601&&e.getAction()==a,"stock app keycode and action");
   check(e.getDownTime()==50&&e.getEventTime()==60+a,"preserve gesture times");
   check(e.getDeviceId()==7&&e.getScanCode()==123&&e.getSource()==0x101&&e.getDisplayId()==2,"preserve device, scan, source, display");
   check(e.getMetaState()==3,"preserve meta state");
   check(!keys.handle(e),"compatibility event cannot recurse into physical-key translation");
  }
  reset(0);key(0);key(1);check(c.input.events.isEmpty()&&c.broadcasts.isEmpty(),"disabled stays disabled");
  reset(2);key(0);key(1);check(c.input.events.isEmpty()&&c.broadcasts.size()==1,"menu mode broadcasts once");
  check(c.broadcasts.get(0).extras.get("lenovo.intent.extra.DEVICE_KEYTIMES_STATE").equals(11),"menu click type unchanged");
  reset(1);key(0);Settings.Global.mode=0;key(1);check(c.input.events.size()==2,"setting change cannot strand app key down");
  reset(0);key(0);Settings.Global.mode=1;key(1);check(c.input.events.isEmpty(),"setting change cannot inject unmatched up");
  reset(1);check(!keys.handle(event(0,327,8,0,0)),"ordinary keyboard F14 untouched");
  check(!keys.handle(event(0,327,-1,0,0)),"virtual keyboard F14 untouched");
  check(!keys.handle(event(0,100,7,0,0)),"other keys untouched");
  reset(1);key(0);keys.handle(event(0,327,7,2,0));key(1);h.drain();
  check(c.input.events.size()==3&&c.input.events.get(1).getRepeatCount()==2,"preserve held-key repeats");
  reset(1);key(0);keys.handle(event(0,327,9,0,0));keys.handle(event(1,327,9,0,0));key(1);h.drain();
  check(c.input.events.size()==4,"independent device sequences");
  reset(1);check(!fallback(app(0),10123),"unhandled DOWN does not trigger fallback");
  check(fallback(app(1),10123),"unhandled UP has generic fallback");h.drain();
  check(c.broadcasts.size()==1&&c.broadcasts.get(0).extras.get("lenovo.intent.extra.DEVICE_KEYTIMES_STATE").equals(1),"generic stock fallback payload");
  for(String pkg:new String[]{"cn.wps.moffice_eng","net.huanci.hsjpro"}) {
   reset(1);c.pm.packages.put(1010123,new String[]{pkg});check(fallback(app(1),1010123),"partner fallback accepted");h.drain();
   check(c.broadcasts.size()==1&&c.broadcasts.get(0).action.equals("lenovo.partner.intent.action.BT_PEN_KEY_EVENT"),"partner fallback action");
   check(c.broadcasts.get(0).pkg.equals(pkg)&&c.broadcasts.get(0).extras.get("action").equals("01"),"partner target and payload");
   check(c.users.get(0).id==10,"partner fallback uses window owner's user");
  }
  reset(1);check(!fallback(app(1),-1),"unknown focused window cannot trigger fallback");
  check(!fallback(event(1,601,8,0,0),10123),"non-Lenovo compatibility key ignored");
  check(!fallback(event(1,601,7,0,0x20),10123),"cancelled key cannot trigger fallback");
  Settings.Global.mode=0;check(!fallback(app(1),10123),"disabled setting suppresses stale fallback");
  reset(1);keys.handle(event(0,326,7,0,0));keys.handle(event(1,326,7,0,0));h.drain();
  check(c.broadcasts.size()==1&&c.broadcasts.get(0).extras.get("lenovo.intent.extra.DEVICE_KEYTIMES_STATE").equals(0),"single-click menu unchanged");
  reset(1);Settings.Global.copyPaste=1;keys.handle(event(1,335,7,0,0));h.drain();
  check(c.input.events.size()==2&&c.input.events.get(0).getKeyCode()==278,"slide copy shortcut unchanged");
  System.out.println("PASS: "+checks+" pen routing assertions");
 }
}''',
}

with tempfile.TemporaryDirectory(prefix='pen-keys-test-') as directory:
    work = Path(directory)
    for name, source in sources.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    production = top / 'hardware/lenovo/input/src/com/tb520fu/input/PenKeys.java'
    if '--before' in sys.argv:
        original = subprocess.check_output(['git', '-C', str(top / 'hardware/lenovo'),
            'show', 'HEAD:input/src/com/tb520fu/input/PenKeys.java'], text=True)
        production = work / 'com/tb520fu/input/PenKeys.java'
        production.write_text(original)
    else:
        (work / 'com/tb520fu/input/PenKeys.java').write_text(production.read_text())
    # Exercise the real framework conversion methods, isolating their native
    # AOSP-name lookup so this can run on a host JVM without libandroid_runtime.
    framework = (top / 'frameworks/base/core/java/android/view/KeyEvent.java').read_text()
    def method(signature):
        start = framework.index(signature)
        opening = framework.index('{', start)
        depth = 1
        end = opening + 1
        while depth:
            if framework[end] == '{':
                depth += 1
            elif framework[end] == '}':
                depth -= 1
            end += 1
        return framework[start:end].replace('@NonNull ', '')
    conversions = '\n'.join(method(s) for s in (
        'public static String keyCodeToString(int keyCode)',
        'public static int keyCodeFromString(@NonNull String symbolicName)',
        'private static boolean keyCodeIsValid(int keyCode)'))
    framework_test = r"""public class VendorKeyNamesTest {
 static final int KEYCODE_UNKNOWN=0, KEYCODE_PEN_TWO_CLICK=601, LAST_KEYCODE=340;
 static final String LABEL_PREFIX="KEYCODE_";
 static String nativeKeyCodeToString(int c){return c==29?"A":null;}
 static int nativeKeyCodeFromString(String s){return s.equals("A")?29:-1;}
 static int count;
 static void check(boolean ok){count++;if(!ok)throw new AssertionError("key name case "+count);}
 public static void main(String[] args){
  check(keyCodeFromString("KEYCODE_PEN_TWO_CLICK")==601);
  check(keyCodeFromString("PEN_TWO_CLICK")==601);
  check(keyCodeFromString("601")==601);
  check(keyCodeToString(601).equals("KEYCODE_PEN_TWO_CLICK"));
  check(keyCodeFromString(keyCodeToString(601))==601);
  check(keyCodeFromString("602")==0);
  check(keyCodeFromString("-1")==0);
  check(keyCodeFromString("KEYCODE_A")==29);
  check(keyCodeToString(29).equals("KEYCODE_A"));
  check(keyCodeFromString("NOT_A_KEY")==0);
  System.out.println("PASS: "+count+" production framework key-name assertions");
 }
 REPLACE_METHODS
}""".replace('REPLACE_METHODS', conversions)
    (work / 'VendorKeyNamesTest.java').write_text(framework_test)
    jdk = top / 'prebuilts/jdk/jdk21/linux-x86/bin'
    subprocess.run([str(jdk / 'javac'), '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([str(jdk / 'java'), '-cp', str(work), 'com.tb520fu.input.PenKeysTest'], check=True)
    subprocess.run([str(jdk / 'java'), '-cp', str(work), 'VendorKeyNamesTest'], check=True)

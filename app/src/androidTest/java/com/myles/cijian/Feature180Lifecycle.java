package com.myles.cijian;
import android.app.*;import android.content.*;import android.os.*;import java.lang.reflect.*;import java.util.*;
/** 专用模拟器：不等销毁就连续开关，检测孤立通知和服务。 / Disposable emulator: start/stop without waiting for destruction, detecting orphan notifications and services. */
public final class Feature180Lifecycle extends Instrumentation {
 private int checks;private void check(boolean v,String message){if(!v)throw new AssertionError(message);checks++;}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private FloatService active()throws Exception{Field f=FloatService.class.getDeclaredField("active");f.setAccessible(true);return (FloatService)((java.lang.ref.WeakReference<?>)f.get(null)).get();}
 @Override @SuppressWarnings("deprecation") public void onStart(){Bundle out=new Bundle();try{
  Context c=getTargetContext();Prefs p=new Prefs(c);p.data.edit().putString("uiLanguage","ZH").putBoolean("autoSpeak",false).putBoolean("autoStart",false).putBoolean("session.focused",false).putBoolean("session.enabled",false).commit();
  Activity home=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(500);
  runOnMainSync(()->{for(int i=0;i<100;i++){c.startForegroundService(new Intent(c,FloatService.class).setAction("BROWSE"));c.startService(new Intent(c,FloatService.class).setAction(FloatService.STOP));}});
  waitForIdleSync();SystemClock.sleep(600);check(!FloatService.running,"100 queued start/stop pairs end stopped");check(Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==7),"Queued start/stop leaves no notification");
  for(int i=0;i<20;i++){runOnMainSync(()->c.startForegroundService(new Intent(c,FloatService.class).setAction("BROWSE")));SystemClock.sleep(25);runOnMainSync(()->{c.stopService(new Intent(c,FloatService.class));c.startForegroundService(new Intent(c,FloatService.class).setAction("BROWSE"));});SystemClock.sleep(25);}
  long end=SystemClock.elapsedRealtime()+15000;while(!FloatService.running&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(20);waitForIdleSync();check(FloatService.running&&active()!=null,"Immediate restart retains the new active service");
  check(Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->n.getId()==7),"Restarted service retains a visible notification");
  runOnMainSync(()->c.startService(new Intent(c,FloatService.class).setAction(FloatService.STOP)));SystemClock.sleep(1200);waitForIdleSync();check(!FloatService.running&&active()==null,"Explicit close clears the active instance");check(Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==7),"No delayed notification after final close");
  check(c.getSystemService(ActivityManager.class).getRunningServices(100).stream().noneMatch(v->v.service.getClassName().equals(FloatService.class.getName())),"No hidden service remains after burst close");runOnMainSync(home::finish);
  out.putString("stream","PASS: "+checks+" rapid lifecycle checks; 100 queued pairs and 20 immediate restarts\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

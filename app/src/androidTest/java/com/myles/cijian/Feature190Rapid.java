package com.myles.cijian;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;import java.lang.reflect.*;import org.json.*;
/** 只用专用模拟器的真实界面，核对快速操作后的界面和记录。 / Use real UI on the disposable emulator and verify state and records after bursts. */
@android.annotation.TargetApi(29)
public final class Feature190Rapid extends Instrumentation {
    private int checks;private MainActivity activity;private Prefs prefs;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private String normal(String value){return value.replaceAll("\\s+"," ").trim();}
    private TextView text(View view,String title,boolean prefix){if(view instanceof TextView&&view.isShown()){String s=normal(((TextView)view).getText().toString());if(prefix?s.startsWith(title):s.equals(title))return (TextView)view;}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),title,prefix);if(found!=null)return found;}return null;}
    private List<View> windows(){return android.view.inspector.WindowInspector.getGlobalWindowViews();}
    private TextView anyText(String title,boolean prefix){List<View> roots=windows();for(int i=roots.size()-1;i>=0;i--){TextView v=text(roots.get(i),title,prefix);if(v!=null&&v.isClickable())return v;}throw new AssertionError("Missing "+title);}
    private EditText input(View view){if(view instanceof EditText&&view.isShown())return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=input(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private EditText anyInput(){for(View root:windows()){EditText e=input(root);if(e!=null)return e;}throw new AssertionError("No input");}
    private ChoiceSpinner batch(View view){if(view instanceof ChoiceSpinner&&"每批词数".contentEquals(view.getContentDescription()))return (ChoiceSpinner)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ChoiceSpinner found=batch(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    // 入口由系统输入用例覆盖，此处直接打开真实弹窗以集中测试连续保存。 / System-input tests cover the entry; open the real dialog directly here to focus on save bursts.
    private void openCustom(){ui(()->{try{Method method=MainActivity.class.getDeclaredMethod("batchDialog");method.setAccessible(true);method.invoke(activity);}catch(Exception error){throw new RuntimeException(error);}});waitForIdleSync();}
    private void ui(Runnable action){java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();super.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError("Test interaction failed",failure.get());}

    @Override public void onStart(){Bundle result=new Bundle();try{
        if(Build.VERSION.SDK_INT<29)throw new AssertionError("Dedicated API35 emulator required");prefs=new Prefs(getTargetContext());prefs.data.edit().putString("uiLanguage","ZH").putBoolean("languagePromptSeen",true).putBoolean("languagePromptPending",false).putInt("reviewBatch",20).putBoolean("autoSpeak",false).commit();
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));long deadline=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<deadline){final boolean[] ready={false};ui(()->ready[0]=field("store")!=null);if(ready[0])break;SystemClock.sleep(20);}waitForIdleSync();WordStore store=WordStore.get(getTargetContext());JSONObject before=new JSONObject(store.backup());
        for(int i=0;i<100;i++){final int step=i;ui(()->{TextView toggle=text(activity.getWindow().getDecorView(),"学习工具",true);check(toggle!=null,"Tools header stays reachable");toggle.performClick();LinearLayout holder=(LinearLayout)toggle.getParent();check(holder.getChildAt(1).getVisibility()==(step%2==0?View.VISIBLE:View.GONE),"Disclosure follows the last click");});SystemClock.sleep(i%3*8);}
        SystemClock.sleep(200);waitForIdleSync();check(!activity.isFinishing()&&!activity.isDestroyed(),"Delayed expansion scroll is safe after rapid toggles");
        ui(()->((LinearLayout)field("nav")).getChildAt(2).performClick());ui(()->anyText("复习选项",true).performClick());waitForIdleSync();openCustom();
        final float[] originalSize={0};ui(()->originalSize[0]=anyInput().getTextSize());
        for(int size:new int[]{36,48}){ui(()->anyInput().setTextSize(size));SystemClock.sleep(100);waitForIdleSync();ui(()->{EditText e=anyInput();check(e.getLayout()!=null&&e.getHeight()-e.getCompoundPaddingTop()-e.getCompoundPaddingBottom()>=e.getLayout().getHeight(),"Large batch digits fit their input at "+size+"sp");});}
        ui(()->anyInput().setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,originalSize[0]));waitForIdleSync();
        for(String invalid:new String[]{"0","51","1.5","","999999999999999999999"})ui(()->{EditText input=anyInput();input.setText(invalid);TextView save=anyText("保存",false);for(int i=0;i<50;i++)save.performClick();check(prefs.reviewBatch()==20&&input.getError()!=null,"Repeated invalid saves preserve settings");});
        ui(()->{anyInput().setText("17");TextView save=anyText("保存",false);for(int i=0;i<50;i++)save.performClick();check(prefs.reviewBatch()==17,"Repeated valid save has one final value");});waitForIdleSync();openCustom();
        ui(()->{anyInput().setText("50");TextView cancel=anyText("取消",false);for(int i=0;i<50;i++)cancel.performClick();check(prefs.reviewBatch()==17,"Repeated cancel never changes the saved value");});waitForIdleSync();
        ui(()->{ReviewFlow.Host host=new ReviewFlow.Host(){public void exit(){}public void transfer(){}public boolean prepareAudio(){return false;}public boolean visible(){return true;}public boolean overlay(){return false;}};
            LinearLayout closedRoot=Ui.column(activity);ReviewFlow closed=new ReviewFlow(activity,closedRoot,"TASK",host,false);TextView retired=(TextView)((LinearLayout)closedRoot.getChildAt(0)).getChildAt(0);closed.close();for(int i=0;i<50;i++)retired.performClick();check(!ReviewGuide.showing(activity),"Closed recall callbacks cannot reopen help");
            LinearLayout hiddenRoot=Ui.column(activity);ReviewFlow hidden=new ReviewFlow(activity,hiddenRoot,"TASK",host,false);TextView inactive=(TextView)((LinearLayout)hiddenRoot.getChildAt(0)).getChildAt(0);hidden.setVisible(false);for(int i=0;i<50;i++)inactive.performClick();check(!ReviewGuide.showing(activity),"Hidden recall callbacks cannot reopen help");hidden.close();
        });
        ui(()->{for(int i=0;i<40;i++)((LinearLayout)field("nav")).getChildAt(i%4).performClick();});java.util.concurrent.CountDownLatch drained=new java.util.concurrent.CountDownLatch(1);Async.IO.execute(drained::countDown);check(drained.await(30,java.util.concurrent.TimeUnit.SECONDS),"Burst navigation background queries finish");waitForIdleSync();JSONObject after=new JSONObject(store.backup());
        for(String key:new String[]{"progress","encounters","exposures","reviews","review_started","review_days","weak_words","mark_changes"})check(before.getJSONArray(key).toString().equals(after.getJSONArray(key).toString()),"Navigation and settings never change learning records: "+key);
        ui(()->activity.finish());result.putString("stream","PASS: "+checks+" rapid 1.9.0 interaction checks; 100 disclosure toggles, 350 save/cancel clicks, 100 retired callbacks and 40 tab switches\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}

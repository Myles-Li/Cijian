package com.myles.kotobafloat;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;
import org.json.*;

/** 专用模拟器：新设置边界、真实滑动路由与字号确认／取消。 / Disposable emulator: preference boundaries, real swipe routing and font Apply/Cancel. */
@android.annotation.TargetApi(29)
public final class Feature110Tests extends Instrumentation {
    private int checks,flips,taps,changed;private boolean touching;private MainActivity activity;private ScrollView scroll;private long down;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private void ui(Runnable action){java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();runOnMainSync(()->{try{action.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError("UI assertion failed",error.get());}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private void ready(){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){boolean[] value={false};ui(()->value[0]=field("store")!=null&&field("cachedHome")!=null);if(value[0])return;SystemClock.sleep(25);}throw new AssertionError("Main activity did not become ready");}
    @Override public void onStart(){Bundle out=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Disposable emulator only");
        Context target=getTargetContext();Context isolated=new ContextWrapper(target){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("feature110-isolated",mode);}};Prefs prefs=new Prefs(isolated);prefs.data.edit().clear().commit();
        check(prefs.appWordSize()==34&&prefs.appReadingSize()==15&&prefs.appMeaningSize()==21,"Default app typography preserves old hierarchy");
        check(prefs.wordSize()==23&&prefs.readingSize()==12&&prefs.meaningSize()==14,"Overlay font defaults remain independent");
        check(prefs.detailHeight()==180,"Detail height has a compact default");
        int[][] limits={{18,48},{10,28},{12,32},{100,420}};String[] keys={"appWordSize","appReadingSize","appMeaningSize","detailHeight"};
        for(int i=0;i<keys.length;i++){
            for(int value:new int[]{limits[i][0],limits[i][1]}){BackupSettings.validate(new JSONObject().put(keys[i],value));check(true,"Backup accepts "+keys[i]+"="+value);}
            for(Object bad:new Object[]{limits[i][0]-1,limits[i][1]+1,1.5,"20",true,JSONObject.NULL}){boolean rejected=false;try{BackupSettings.validate(new JSONObject().put(keys[i],bad));}catch(JSONException expected){rejected=true;}check(rejected,"Backup rejects invalid "+keys[i]+": "+bad);}
        }
        JSONObject original=new JSONObject().put("appWordSize",48).put("appReadingSize",28).put("appMeaningSize",32).put("detailHeight",420);BackupSettings.restore(prefs.data,original);JSONObject exported=BackupSettings.export(prefs.data);
        for(String key:keys)check(exported.getInt(key)==original.getInt(key),"New preference round-trips: "+key);
        BackupSettings.restore(prefs.data,new JSONObject().put("wordSize",16).put("readingSize",10).put("meaningSize",12));check(prefs.appWordSize()==48&&prefs.wordSize()==16,"Old overlay-only backup never resets app text sizes");
        prefs.put("appWordSize",-900);prefs.put("appReadingSize",1000);prefs.put("appMeaningSize",0);prefs.put("detailHeight",9999);check(prefs.appWordSize()==18&&prefs.appReadingSize()==28&&prefs.appMeaningSize()==12&&prefs.detailHeight()==420,"Damaged local values are bounded");prefs.data.edit().clear().commit();
        check(Arrays.asList(SettingIndex.RESET[0]).containsAll(Arrays.asList("appWordSize","appReadingSize","appMeaningSize")),"Appearance reset includes in-app fonts");check(!Arrays.asList(SettingIndex.RESET[2]).contains("appWordSize")&&Arrays.asList(SettingIndex.RESET[2]).contains("detailHeight"),"Overlay reset does not change app fonts");
        new Prefs(target).data.edit().putString("uiLanguage","ZH").putBoolean("languagePromptSeen",true).putBoolean("languagePromptPending",false).commit();
        activity=(MainActivity)startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));ready();waitForIdleSync();JSONObject before=new JSONObject(WordStore.get(target).backup());
        fontDialogs(prefs);gestures();
        JSONObject after=new JSONObject(WordStore.get(target).backup());for(String key:new String[]{"progress","encounters","exposures","reviews","review_started","review_days","weak_words","mark_changes"})check(before.getJSONArray(key).toString().equals(after.getJSONArray(key).toString()),"Preference previews and gesture fixture preserve evidence: "+key);
        reviewCompletion();ui(()->activity.finish());out.putString("stream","PASS: "+checks+" 1.10.0 boundary, gesture and font-dialog assertions\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
    private TextView findText(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void waitText(String text){long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until){boolean[] found={false};ui(()->found[0]=findText(activity.getWindow().getDecorView(),text)!=null);if(found[0])return;SystemClock.sleep(25);}throw new AssertionError("Missing review state: "+text);}
    /** 完成后的字号操作不能复活最后一题或重新评分。 / Font changes after completion must not resurrect or regrade the last card. */
    private void reviewCompletion()throws Exception{
        Context target=getTargetContext();Prefs prefs=new Prefs(target);prefs.data.edit().putString("level","N5").putString("frequency","ALL").putString("groupMode","OFF").putString("studyMode","all").putBoolean("reverseRecall",false).putBoolean("reviewAutoSpeak",false).commit();WordStore store=WordStore.get(target);Word word=store.words("N5","ALL","","all",false,false,1).get(0);
        ReviewDraft seed=new ReviewDraft("N5|ALL|all","PRACTICE",false,Collections.singletonList(word.id));store.startDraft(seed);ReviewFlow[] flow={null};
        ui(()->{LinearLayout panel=Ui.column(activity);activity.setContentView(panel);flow[0]=new ReviewFlow(activity,panel,"PRACTICE",new ReviewFlow.Host(){public void exit(){}public void transfer(){}public boolean prepareAudio(){return true;}public boolean visible(){return true;}public boolean overlay(){return false;}},true);});
        waitText("揭晓答案");ui(()->findText(activity.getWindow().getDecorView(),"揭晓答案").performClick());waitText("记得");ui(()->findText(activity.getWindow().getDecorView(),"记得").performClick());waitText("这一轮完成了");String draft=flow[0].snapshot();JSONObject before=new JSONObject(store.backup());
        try{
            for(int i=0;i<3;i++){ui(()->flow[0].editFonts(activity));waitForIdleSync();ui(()->dialogRoot().findViewById(android.R.id.button1).performClick());waitForIdleSync();ui(()->check(findText(activity.getWindow().getDecorView(),"这一轮完成了")!=null,"Completed review remains completed after font Apply"));check(flow[0].snapshot().equals(draft),"Font Apply preserves completed draft token, scores and position");}
            JSONObject after=new JSONObject(store.backup());for(String key:new String[]{"progress","encounters","exposures","reviews","review_started","review_days","weak_words","mark_changes"})check(before.getJSONArray(key).toString().equals(after.getJSONArray(key).toString()),"Completed font changes preserve evidence: "+key);
        }finally{ui(()->flow[0].close());}
    }
    private View dialogRoot(){List<View> roots=android.view.inspector.WindowInspector.getGlobalWindowViews();return roots.get(roots.size()-1);}
    private void bars(View view,List<SeekBar> out){if(view instanceof SeekBar)out.add((SeekBar)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)bars(((ViewGroup)view).getChildAt(i),out);}
    private void fontDialogs(Prefs prefs){
        for(int i=0;i<12;i++){final boolean app=i%2==0;ui(()->{if(app)FontPicker.showApp(activity,prefs,()->changed++);else FontPicker.show(activity,prefs,()->changed++);});waitForIdleSync();ui(()->{List<SeekBar> sliders=new ArrayList<>();View root=dialogRoot();bars(root,sliders);check(sliders.size()==3,"Font dialog contains all three size controls");for(SeekBar bar:sliders)bar.setProgress(bar.getMax());root.findViewById(android.R.id.button2).performClick();});waitForIdleSync();check(changed==0&&prefs.appWordSize()==34&&prefs.wordSize()==23,"Cancelling repeated font previews preserves both scopes");}
        ui(()->FontPicker.showApp(activity,prefs,()->changed++));waitForIdleSync();ui(()->{List<SeekBar> sliders=new ArrayList<>();View root=dialogRoot();bars(root,sliders);for(SeekBar bar:sliders)bar.setProgress(bar.getMax());root.findViewById(android.R.id.button1).performClick();});waitForIdleSync();check(changed==1&&prefs.appWordSize()==48&&prefs.appReadingSize()==28&&prefs.appMeaningSize()==32,"Apply commits the displayed app sizes together");check(prefs.wordSize()==23&&prefs.readingSize()==12&&prefs.meaningSize()==14,"App Apply preserves overlay font sizes");
    }
    private void send(int action,float x,float y){int[] origin=new int[2];scroll.getLocationOnScreen(origin);if(action==MotionEvent.ACTION_DOWN)down=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+origin[0],y+origin[1],0);e.offsetLocation(-origin[0],-origin[1]);scroll.dispatchTouchEvent(e);e.recycle();}
    private void two(int action,float x,float y){int[] origin=new int[2];scroll.getLocationOnScreen(origin);MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[2];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[2];for(int i=0;i<2;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=x+origin[0]+i*15;coords[i].y=y+origin[1];coords[i].pressure=1;coords[i].size=1;}MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);e.offsetLocation(-origin[0],-origin[1]);scroll.dispatchTouchEvent(e);e.recycle();}
    private void gestures(){
        ui(()->{FrameLayout host=new FrameLayout(activity);scroll=new ScrollView(activity);SwipeCardLayout card=new SwipeCardLayout(activity,new SwipeCardLayout.Listener(){public void touching(boolean value){touching=value;}public void flip(int value){flips+=value;}});TextView button=Ui.button(activity,"Clickable word",false,v->taps++);card.addView(button,new LinearLayout.LayoutParams(-1,Ui.dp(activity,65)));TextView longText=Ui.prose(activity,String.join("",Collections.nCopies(90,"Vertical content\n")),18,Ui.INK);card.addView(longText);scroll.addView(card);host.addView(scroll,new FrameLayout.LayoutParams(Ui.dp(activity,300),Ui.dp(activity,220)));activity.setContentView(host);});waitForIdleSync();
        ui(()->{send(MotionEvent.ACTION_DOWN,Ui.dp(activity,160),Ui.dp(activity,30));send(MotionEvent.ACTION_UP,Ui.dp(activity,160),Ui.dp(activity,30));});waitForIdleSync();check(taps==1&&flips==0&&!touching,"Ordinary child click remains clickable");
        ui(()->{float x=Ui.dp(activity,160),y=Ui.dp(activity,30),distance=Ui.dp(activity,80);for(int i=0;i<120;i++){int before=flips;send(MotionEvent.ACTION_DOWN,x,y);send(MotionEvent.ACTION_MOVE,x-distance,y+2);send(MotionEvent.ACTION_UP,x-distance,y+2);check(flips==before+1&&!touching,"Fast left swipe advances exactly once");send(MotionEvent.ACTION_DOWN,x-distance,y);send(MotionEvent.ACTION_MOVE,x,y+2);send(MotionEvent.ACTION_UP,x,y+2);check(flips==before&&!touching,"Fast right swipe returns exactly once");}check(taps==1,"Swipes across a clickable word do not activate its click");
            for(int i=0;i<30;i++){int before=flips;float plainY=Ui.dp(activity,110);send(MotionEvent.ACTION_DOWN,x,plainY);send(MotionEvent.ACTION_MOVE,x-distance,plainY+2);send(MotionEvent.ACTION_UP,x-distance,plainY+2);check(flips==before+1&&!touching,"Swipe on non-clickable word text advances once");send(MotionEvent.ACTION_DOWN,x-distance,plainY);send(MotionEvent.ACTION_MOVE,x,plainY+2);send(MotionEvent.ACTION_UP,x,plainY+2);check(flips==before&&!touching,"Swipe on non-clickable text returns once");}
            for(int i=0;i<30;i++){int before=flips;send(MotionEvent.ACTION_DOWN,x,y);send(MotionEvent.ACTION_MOVE,x-Ui.dp(activity,20),y);send(MotionEvent.ACTION_UP,x-Ui.dp(activity,20),y);check(flips==before&&!touching,"Small horizontal movement never flips");send(MotionEvent.ACTION_DOWN,x,y);send(MotionEvent.ACTION_MOVE,x-distance,y);send(MotionEvent.ACTION_CANCEL,x-distance,y);check(flips==before&&!touching,"Cancelled swipe never advances");}
            int before=flips;send(MotionEvent.ACTION_DOWN,x,Ui.dp(activity,160));send(MotionEvent.ACTION_MOVE,x,Ui.dp(activity,110));send(MotionEvent.ACTION_MOVE,x-distance,Ui.dp(activity,50));send(MotionEvent.ACTION_UP,x-distance,Ui.dp(activity,50));check(flips==before&&scroll.getScrollY()>0&&!touching,"Vertical scroll is not stolen by a later sideways movement");scroll.scrollTo(0,0);
            before=flips;send(MotionEvent.ACTION_DOWN,x,y);send(MotionEvent.ACTION_MOVE,x-distance,y);two(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x-distance,y);two(MotionEvent.ACTION_MOVE,x-distance*1.5f,y);two(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x-distance*1.5f,y);send(MotionEvent.ACTION_UP,x-distance*1.5f,y);check(flips==before&&!touching,"A second finger cancels swipe navigation");
            send(MotionEvent.ACTION_DOWN,x,y);activity.setContentView(new FrameLayout(activity));check(!touching,"Detaching a touched card releases timer suppression");});
    }
}

package com.myles.kotobafloat;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** 仅专用模拟器：快速点击与延迟加载验收。 / Disposable emulator only: burst clicks and deliberately delayed loads. */
public final class Feature180Stress extends Instrumentation {
    private int checks;private final StringBuilder log=new StringBuilder();private Context context;private WordStore store;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;log.append("PASS: ").append(message).append("\n");}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void main(Runnable action){runOnMainSync(action);}
    private Object field(Object instance,String name){try{Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}catch(Exception e){throw new RuntimeException(e);}}
    private Object call(Object instance,String name){try{Method m=instance.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(instance);}catch(Exception e){throw new RuntimeException(e);}}
    private boolean busy(Object o){AtomicReference<Boolean> b=new AtomicReference<>();main(()->b.set((Boolean)field(o,"busy")));return b.get();}
    private void idle(Object o){long until=SystemClock.elapsedRealtime()+20000;while(busy(o)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(10);check(!busy(o),"Pending action finishes");waitForIdleSync();}
    private TextView find(View v,String value){if(v instanceof TextView&&((TextView)v).getText().toString().equals(value)&&v.getVisibility()==View.VISIBLE)return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),value);if(t!=null)return t;}}return null;}
    private void click(Activity a,String label,int count){main(()->{TextView v=find(a.getWindow().getDecorView(),label);if(v==null)throw new AssertionError("Missing "+label);for(int i=0;i<count;i++)v.performClick();});}
    private <T extends Activity>T launch(Class<T> cls){return cls.cast(startActivitySync(new Intent(context,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));}
    private Word current(BrowseActivity a){AtomicReference<Word>w=new AtomicReference<>();main(()->w.set((Word)call(a,"current")));return w.get();}
    private FloatService service(){try{Field f=FloatService.class.getDeclaredField("active");f.setAccessible(true);return (FloatService)((java.lang.ref.WeakReference<?>)f.get(null)).get();}catch(Exception e){throw new RuntimeException(e);}}
    private void overlayIdle(FloatService s){long end=SystemClock.elapsedRealtime()+15000;AtomicReference<Boolean> done=new AtomicReference<>(false);while(SystemClock.elapsedRealtime()<end){main(()->done.set(!(Boolean)field(s,"loading")&&!(Boolean)field(s,"marking")));if(done.get())break;SystemClock.sleep(10);}check(done.get(),"Overlay write and load finish");}
    private ReviewDraft draft(ReviewActivity a){AtomicReference<ReviewDraft>d=new AtomicReference<>();main(()->d.set((ReviewDraft)field(field(a,"flow"),"draft")));return d.get();}
    @Override public void onStart(){Bundle out=new Bundle();try{
        context=getTargetContext();store=WordStore.get(context);Prefs prefs=new Prefs(context);List<Word> sample=store.words("N5","ALL","","all",false,false,8);List<String> ids=new ArrayList<>();for(Word w:sample){ids.add(w.id);store.familiar(w,false);store.favorite(w,false);}
        prefs.data.edit().putString("uiLanguage","ZH").putString("groupMode","CUSTOM").putString("customWords",String.join("\n",ids)).putString("studyMode","all").putBoolean("hideFamiliar",true).putBoolean("shuffle",false).putBoolean("autoSpeak",false).putBoolean("speakOnFlip",false).putString("scopeMemory","{}").putBoolean("scopeMemoryMigrated",true).commit();
        L.init(context);BrowseActivity a=launch(BrowseActivity.class);idle(a);Word first=current(a);check(first!=null,"Browse starts");
        Async.IO.execute(()->SystemClock.sleep(300));click(a,"收藏",100);idle(a);check(store.byId(first.id).favorite,"100 rapid favorite taps commit one toggle");check(current(a).favorite,"Favorite UI agrees with committed database");check(find(a.getWindow().getDecorView(),"正在准备连续浏览…")==null,"Favorite never replaces content with loading screen");
        click(a,"撤销上次标记",100);idle(a);check(!store.byId(first.id).favorite,"100 rapid undo taps undo once");
        for(int i=0;i<8;i++){click(a,"标为熟悉",40);idle(a);}
        check(current(a)==null,"Eight rapid marks exhaust selected range safely");for(Word w:sample)check(store.byId(w.id).familiar,"Each selected word was marked");
        for(int i=0;i<8;i++){click(a,"撤销上次标记",40);idle(a);check(current(a)!=null&&current(a).id.equals(ids.get(7-i)),"Undo restores the correct previous word");}
        for(Word w:sample)check(!store.byId(w.id).familiar,"All rapid marks were undone");
        prefs.put("speakOnFlip",true);Async.IO.execute(()->SystemClock.sleep(400));click(a,"标为熟悉",50);MainActivity interruption=launch(MainActivity.class);idle(a);SystemClock.sleep(350);check(!(Boolean)field(a,"foreground")&&!((Speech)field(a,"speech")).isSpeakingSequence(),"Leaving during a mark never starts background flip audio");main(interruption::finish);main(a::finish);waitForIdleSync();prefs.put("speakOnFlip",false);for(Word w:sample)store.familiar(w,false);

        // 人为堵住IO，验证超时可恢复且旧结果不会覆盖重试。 / Delay IO to verify timeout recovery and rejection of stale results.
        Async.IO.execute(()->SystemClock.sleep(13000));BrowseActivity delayed=launch(BrowseActivity.class);SystemClock.sleep(12400);waitForIdleSync();check(find(delayed.getWindow().getDecorView(),"重试加载")!=null,"Delayed load exposes retry instead of waiting forever");SystemClock.sleep(1000);click(delayed,"重试加载",50);idle(delayed);check(current(delayed)!=null,"Rapid retries recover without stale load replacement");main(delayed::finish);waitForIdleSync();
        prefs.data.edit().putInt("reviewBatch",5).putBoolean("reviewAutoSpeak",false).putBoolean("reverseRecall",false).commit();
        // 反复运行也使用确定的到期数据，不依赖先前测试已消耗的新学额度。 / Repeated runs seed known due words rather than relying on an unused daily quota.
        for(Word w:sample)store.rate(w,2,System.currentTimeMillis()-40*ReviewPlan.DAY);
        ((android.database.sqlite.SQLiteDatabase)field(store,"db")).delete("review_drafts","mode=?",new String[]{"TASK"});
        ReviewActivity review=launch(ReviewActivity.class);Object flow=field(review,"flow");idle(flow);if(draft(review)==null){click(review,"放弃本轮，重新安排",1);idle(flow);}ReviewDraft before=draft(review);check(before!=null&&before.session.current()!=null,"Recall batch prepared");
        String answerId=before.session.current();int scoredBefore=store.scalar("SELECT COALESCE(SUM(attempts),0) FROM review_days");click(review,"揭晓答案",100);idle(flow);check(draft(review).revealed,"100 reveal taps reveal only the current card");click(review,"记得",100);idle(flow);ReviewDraft after=draft(review);check(after.session.position==before.session.position+1&&after.remembered==before.remembered+1,"100 rating taps advance and score once");check(store.scalar("SELECT COALESCE(SUM(attempts),0) FROM review_days")==scoredBefore+1,"Exactly one formal rating is persisted");main(review::finish);waitForIdleSync();
        // 位置历史上限与撤销窗口边界。 / Undo capacity and expiration boundaries.
        MarkHistory<Integer> history=new MarkHistory<>();for(int i=0;i<100;i++)history.push(i,i);check(history.size(100)==32,"Undo is bounded at 32 marks");for(int i=99;i>=68;i--)check(history.pop(101)==i,"Undo order remains LIFO");check(history.pop(101)==null,"Empty history is safe");history.push(1,100);check(history.peek(8099)==1&&history.peek(8100)==null,"Undo expires exactly at eight seconds");
        for(String language:new String[]{"EN","JA","ZH"}){prefs.put("uiLanguage",language);L.init(context);check(!L.t("设置").isEmpty(),"Language catalog loads");org.json.JSONObject backup=BackupSettings.export(prefs.data);check(backup.getString("uiLanguage").equals(language),"UI language exports");BackupSettings.restore(prefs.data,backup);check(prefs.data.getString("uiLanguage","").equals(language),"UI language restores");}
        try{BackupSettings.validate(new org.json.JSONObject().put("uiLanguage","bad"));throw new AssertionError("Invalid UI language accepted");}catch(org.json.JSONException expected){checks++;}
        prefs.put("uiLanguage","ZH");L.init(context);check(store.byId(answerId).meaning.equals(sample.stream().filter(w->w.id.equals(answerId)).findFirst().get().meaning),"UI languages leave dictionary meaning untouched");
        MainActivity home=launch(MainActivity.class);SystemClock.sleep(500);
        prefs.data.edit().putString("groupMode","CUSTOM").putString("customWords",String.join("\n",ids)).putBoolean("shuffle",true).putBoolean("autoSpeak",false).putBoolean("session.focused",false).commit();
        main(()->context.startForegroundService(new Intent(context,FloatService.class).setAction("BROWSE")));long until=SystemClock.elapsedRealtime()+10000;while((service()==null||!FloatService.running)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);check(service()!=null&&FloatService.running,"Overlay starts for burst lifecycle tests");
        FloatService marks=service();overlayIdle(marks);List<String> order=new ArrayList<>();for(int i=0;i<8;i++){AtomicReference<Word> word=new AtomicReference<>();main(()->word.set((Word)call(marks,"current")));check(word.get()!=null,"Shuffled overlay has a current word");order.add(word.get().id);main(()->{for(int j=0;j<50;j++)call(marks,"markFamiliar");});overlayIdle(marks);}
        for(int i=7;i>=0;i--){main(()->{for(int j=0;j<50;j++)call(marks,"undoMark");});overlayIdle(marks);AtomicReference<Word> word=new AtomicReference<>();main(()->word.set((Word)call(marks,"current")));check(word.get()!=null&&word.get().id.equals(order.get(i)),"Overlay undo restores shuffled word and queue");}
        for(Word w:sample)check(!store.byId(w.id).familiar,"Overlay restores every familiar flag");
        prefs.put("hideFamiliar",false);overlayIdle(marks);
        AtomicReference<String> expectedNext=new AtomicReference<>();main(()->{java.util.Map<String,String> choices=new java.util.LinkedHashMap<>();for(Word w:(List<Word>)field(marks,"words"))choices.put(w.id,w.word+"\\u001f"+w.reading);ShuffleDeck copy=new ShuffleDeck(choices,((ShuffleDeck)field(marks,"shuffleDeck")).save(),(String)field(marks,"activeScope"));expectedNext.set(copy.next());call(marks,"markFamiliar");});overlayIdle(marks);AtomicReference<Word> retained=new AtomicReference<>();main(()->retained.set((Word)call(marks,"current")));check(retained.get().id.equals(expectedNext.get()),"Marking familiar without hiding advances shuffled queue exactly once");
        store.familiar(retained.get(),true);AtomicReference<String> queueBefore=new AtomicReference<>();main(()->{queueBefore.set(((ShuffleDeck)field(marks,"shuffleDeck")).save());call(marks,"markFamiliar");});overlayIdle(marks);main(()->check(((Word)call(marks,"current")).id.equals(retained.get().id)&&((ShuffleDeck)field(marks,"shuffleDeck")).save().equals(queueBefore.get()),"Removing familiar without filtering keeps current word and shuffled queue"));
        for(Word w:sample)store.familiar(w,false);
        prefs.data.edit().putString("groupMode","OFF").putBoolean("shuffle",false).commit();

        main(()->{for(int i=0;i<100;i++)prefs.put("level",i==99?"N3":i%2==0?"N5":"N4");});
        SystemClock.sleep(1500);FloatService overlay=service();check(overlay!=null,"Rapid filters keep service alive");AtomicReference<Boolean> loaded=new AtomicReference<>();main(()->loaded.set(!(Boolean)field(overlay,"loading")));check(loaded.get(),"100 rapid filter changes finish loading");AtomicReference<Word> finalWord=new AtomicReference<>();main(()->finalWord.set((Word)call(overlay,"current")));check(finalWord.get()!=null&&finalWord.get().level.equals("N3"),"Latest scope wins over 99 stale requests");
        Async.IO.execute(()->SystemClock.sleep(13000));main(()->call(overlay,"reload"));SystemClock.sleep(12400);main(()->check(!(Boolean)field(overlay,"loading")&&!((String)field(overlay,"error")).isEmpty()&&find((View)field(overlay,"overlay"),"重试加载")!=null,"Overlay reload timeout visibly offers retry even with an old word"));main(()->{call(overlay,"togglePause");check((Boolean)field(overlay,"paused")&&!((Speech)field(overlay,"speech")).isSpeakingSequence(),"Play on load failure retries without reading the old scope");});SystemClock.sleep(900);
        main(()->{for(int i=0;i<25;i++)call(overlay,"reload");});overlayIdle(overlay);main(()->check(((Word)call(overlay,"current")).level.equals("N3")&&((String)field(overlay,"error")).isEmpty(),"Overlay retry recovers the latest scope"));
        main(()->{for(int i=0;i<50;i++)call(overlay,"togglePause");call(overlay,"stopByUser");});SystemClock.sleep(1600);check(!FloatService.running,"Rapid playback toggles then close stop the service");
        check(Arrays.stream(context.getSystemService(NotificationManager.class).getActiveNotifications()).noneMatch(n->n.getId()==7),"No lingering notification after delayed callbacks");
        main(home::finish);
        out.putString("stream",log+"PASS: "+checks+" rapid-operation and boundary checks\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream",log+"FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

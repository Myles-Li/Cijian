package com.myles.kotobafloat;
import android.app.*;import android.content.*;import android.database.sqlite.*;import android.os.Bundle;import java.util.*;import java.io.File;import org.json.*;
/** 新版本隔离数据验收，不操作真机数据。 / Isolated acceptance checks never touch real-user records. */
public final class Feature175Instrumentation extends Instrumentation {
    private int checks;private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context target=getTargetContext();checks+=Feature17Tests.run(target);
        File file=new File(target.getCacheDir(),"features-175.db");SQLiteDatabase.deleteDatabase(file);
        Context c=new ContextWrapper(target){@Override public File getDatabasePath(String n){return file;}@Override public SharedPreferences getSharedPreferences(String n,int m){return target.getSharedPreferences("features-175",m);}};
        Prefs prefs=new Prefs(c);prefs.data.edit().clear().commit();check(prefs.reviewAutoSpeak()&&prefs.reviewBatch()==20,"Upgrade defaults");
        for(int n:new int[]{5,10,20,30,50}){prefs.put("reviewBatch",n);check(prefs.reviewBatch()==n,"Batch selected");JSONObject data=BackupSettings.export(prefs.data);check(data.getInt("reviewBatch")==n,"Batch exported");prefs.put("reviewBatch",20);BackupSettings.restore(prefs.data,data);check(prefs.reviewBatch()==n,"Batch restored");}
        for(int n:new int[]{-1,0,Integer.MIN_VALUE,Integer.MAX_VALUE,51,1000}){prefs.put("reviewBatch",n);check(prefs.reviewBatch()==20,"Invalid batch clamps to default");try{BackupSettings.validate(new JSONObject().put("reviewBatch",n));throw new AssertionError("Invalid backup accepted");}catch(JSONException expected){checks++;}}
        prefs.put("reviewBatch",20);prefs.put("reviewAutoSpeak",false);JSONObject config=BackupSettings.export(prefs.data);prefs.put("reviewAutoSpeak",true);BackupSettings.restore(prefs.data,config);check(!prefs.reviewAutoSpeak(),"Recall audio restored independently");
        java.lang.reflect.Constructor<WordStore> ctor=WordStore.class.getDeclaredConstructor(Context.class);ctor.setAccessible(true);WordStore store=ctor.newInstance(c);java.lang.reflect.Field f=WordStore.class.getDeclaredField("db");f.setAccessible(true);SQLiteDatabase db=(SQLiteDatabase)f.get(store);
        try{
            for(String level:new String[]{"N5","N4","N3","N2","N1"}){
                List<Word> pool=store.words(level,"ALL","","all",false,false,0);List<QuizOptions.Entry> entries=new ArrayList<>();for(Word w:pool)entries.add(new QuizOptions.Entry(w.id,w.word,w.reading,w.meaning));
                for(int i=0;i<pool.size();i+=Math.max(1,pool.size()/12)){
                    QuizOptions.Entry answer=entries.get(i);List<QuizOptions.Entry> options=QuizOptions.make(answer,entries);check(options.size()==4,"Representative word has four options");check(options.stream().filter(e->e.id.equals(answer.id)).count()==1,"Answer appears once");
                    for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)check(!QuizOptions.conflicts(options.get(a),options.get(b)),"No duplicate, same head, reading or exact gloss overlap");
                    check(options.stream().map(e->e.id).collect(java.util.stream.Collectors.joining()).equals(QuizOptions.make(answer,entries).stream().map(e->e.id).collect(java.util.stream.Collectors.joining())),"Recreation preserves option order");
                }
            }
            QuizOptions.Entry a=new QuizOptions.Entry("a","同形","どうけい","相同；一样");check(QuizOptions.make(a,Arrays.asList(a,new QuizOptions.Entry("b","别词","べつ","一样"))).isEmpty(),"Insufficient reliable options do not fabricate a question");
            List<Word> words=store.words("N5","ALL","","all",false,false,60);List<String> ids=new ArrayList<>();for(int i=0;i<50;i++)ids.add(words.get(i).id);ReviewDraft d=new ReviewDraft("scope","TASK",false,ids);store.startDraft(d);
            ReviewDraft reveal=d.copy();reveal.revealed=true;d=store.saveDraft(d,reveal,null,-1);check(store.draft("TASK").revealed,"Reveal checkpoint for both surfaces");ReviewDraft next=d.copy();next.session.answer(2);next.remembered++;next.revealed=false;store.saveDraft(d,next,words.get(0),2);String first=new JSONObject(store.backup()).getJSONArray("review_days").toString();store.saveDraft(d,next,words.get(0),2);check(first.equals(new JSONObject(store.backup()).getJSONArray("review_days").toString()),"Double click after surface transfer scores once");
            check(ReviewDraft.parse(store.draft("TASK").json()).session.queue.size()==50,"Maximum batch survives serialization");
            check(store.scalar("SELECT count(*) FROM words")==10641,"Dictionary untouched");
        }finally{db.close();SQLiteDatabase.deleteDatabase(file);prefs.data.edit().clear().commit();}
        result.putString("stream","PASS: "+checks+" isolated 1.7.5 checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}

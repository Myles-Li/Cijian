package com.myles.kotobafloat;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import org.json.*;
/** 专用模拟器：验证新边界、实际备份和持久队列。 / Disposable emulator: verify new bounds, real backup persistence, and durable queues. */
public final class Feature190Rules extends Instrumentation {
    private int checks;
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private String read(java.io.InputStream in)throws java.io.IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}
    @Override public void onStart(){Bundle result=new Bundle();SharedPreferences local=null;try{
        Context base=getTargetContext();Context isolated=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("feature190-isolated",mode);}};Prefs prefs=new Prefs(isolated);local=prefs.data;local.edit().clear().commit();
        check(prefs.accent().equals("BLACK"),"Fresh default is monochrome");prefs.put("accent","BLUE");check(prefs.accent().equals("BLUE"),"Explicit legacy accent retained");
        for(String tag:new String[]{"zh-CN","zh-TW","zh-HK"})check(UiLanguage.resolve(null,Locale.forLanguageTag(tag)).equals("ZH"),"Chinese system default");
        for(String tag:new String[]{"ja-JP","ja"})check(UiLanguage.resolve(null,Locale.forLanguageTag(tag)).equals("JA"),"Japanese system default");
        for(String tag:new String[]{"en-US","en-GB","fr-FR","ar-SA","ko-KR",""})check(UiLanguage.resolve(null,Locale.forLanguageTag(tag)).equals("EN"),"English fallback");
        for(String id:UiLanguage.IDS)check(UiLanguage.resolve(id,Locale.FRENCH).equals(id),"Explicit selection wins");check(UiLanguage.resolve("bad",Locale.JAPANESE).equals("JA"),"Invalid old selection falls back to system");
        for(int count=1;count<=50;count++){
            check(ReviewBatch.parse(" "+count+" ")==count,"Trimmed whole batch accepted");prefs.put("reviewBatch",count);check(prefs.reviewBatch()==count,"Custom batch read");JSONObject saved=BackupSettings.export(local);prefs.put("reviewBatch",20);BackupSettings.restore(local,saved);check(prefs.reviewBatch()==count,"Actual settings backup round trip");
        }
        for(String text:new String[]{""," ","0","51","-1","1.5","1e1","+3","1 2","1,2","NaN","2147483648","999999999999999999999"}){try{ReviewBatch.parse(text);throw new AssertionError("Accepted invalid batch: "+text);}catch(IllegalArgumentException expected){checks++;}}
        for(int count:new int[]{Integer.MIN_VALUE,-1,0,51,1000,Integer.MAX_VALUE}){prefs.put("reviewBatch",count);check(prefs.reviewBatch()==20,"Corrupt local batch fallback");try{BackupSettings.validate(new JSONObject().put("reviewBatch",count));throw new AssertionError("Invalid backup accepted");}catch(JSONException expected){checks++;}}
        for(Object value:new Object[]{"17",17.5,true,JSONObject.NULL}){try{BackupSettings.validate(new JSONObject().put("reviewBatch",value));throw new AssertionError("Invalid type accepted");}catch(JSONException expected){checks++;}}
        List<String> ids=new ArrayList<>();for(int i=0;i<50;i++)ids.add(UUID.nameUUIDFromBytes(("word"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());ReviewDraft draft=new ReviewDraft("test","TASK",false,ids);
        while(draft.session.current()!=null)draft.session.answer(0);check(draft.session.queue.size()<=100,"Fifty words and retries fit persisted limit");check(ReviewDraft.parse(draft.json()).session.position==draft.session.position,"Maximum retry queue survives serialization");
        JSONObject oversized=new JSONObject(draft.json());oversized.getJSONArray("ids").put(ids.get(0));try{ReviewDraft.parse(oversized.toString());throw new AssertionError("Oversized queue accepted");}catch(JSONException expected){checks++;}
        long now=100000;Map<String,StudyTasks.State> states=new HashMap<>();for(int i=0;i<12;i++)states.put(ids.get(i),new StudyTasks.State(now-1,1));StudyTasks tasks=new StudyTasks(ids,states,Collections.emptySet(),Collections.emptySet(),now);List<String> first=tasks.plan(10,20,false);check(first.size()==20&&first.subList(0,12).equals(ids.subList(0,12)),"Guide example is due-first with eight new words");
        Set<String> newToday=new HashSet<>(ids.subList(12,20));for(String id:first)states.put(id,new StudyTasks.State(now+ReviewPlan.DAY,1));StudyTasks next=new StudyTasks(ids,states,newToday,new HashSet<>(first),now);check(next.plan(10,20,false).size()==2,"Remaining daily new allowance is two");check(next.plan(10,20,true).isEmpty(),"Due-only never adds new words");
        JSONObject en,ja;try(java.io.InputStream in=base.getAssets().open("ui-en.json")){en=new JSONObject(read(in));}try(java.io.InputStream in=base.getAssets().open("ui-ja.json")){ja=new JSONObject(read(in));}
        for(String[] section:ReviewGuide.SECTIONS)for(String key:section){check(en.has(key)&&!en.getString(key).isEmpty(),"Guide has English text");check(ja.has(key)&&!ja.getString(key).isEmpty(),"Guide has Japanese text");}
        check(en.getString("词间").equals("Cijian")&&ja.getString("词间").equals("词间"),"Chosen brand preserved");
        result.putString("stream","PASS: "+checks+" isolated 1.9.0 boundary and migration checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}finally{if(local!=null)local.edit().clear().commit();}}
}

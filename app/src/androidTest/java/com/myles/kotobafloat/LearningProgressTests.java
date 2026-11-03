package com.myles.kotobafloat;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.time.LocalDate;
import java.util.*;

final class LearningProgressTests {
    private int checks;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static int run(Context target)throws Exception{return new LearningProgressTests().test(target);}
    private int test(Context target)throws Exception{
        java.io.File fixture=new java.io.File(target.getCacheDir(),"progress-1.5-fixture.db");SQLiteDatabase.deleteDatabase(fixture);
        Context isolated=new ContextWrapper(target){
            @Override public java.io.File getDatabasePath(String name){return fixture;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences("progress-1.5-fixture",mode);}
        };
        isolated.getSharedPreferences("preferences",0).edit().clear().commit();
        java.lang.reflect.Constructor<WordStore> constructor=WordStore.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);WordStore store=constructor.newInstance(isolated);
        java.lang.reflect.Field field=WordStore.class.getDeclaredField("db");field.setAccessible(true);SQLiteDatabase db=(SQLiteDatabase)field.get(store);
        try{
            List<Word> range=store.words("N5","","all",false,false,3);Word a=range.get(0),b=range.get(1),outside=store.words("N1","","all",false,false,1).get(0);
            JSONObject prior=new JSONObject().put("format","kotoba-float-progress-v1").put("progress",new JSONArray().put(new JSONObject().put("id",a.id).put("favorite",0).put("familiar",0).put("seen",1).put("last_seen",1))).put("encounters",new JSONArray().put(new JSONObject().put("id",a.id).put("day",LocalDate.now().minusDays(1).toString())));
            store.restore(prior.toString());store.seen(a);store.seen(b);store.seen(outside);store.seen(b);
            WordStore.HomeProgress snapshot=store.homeProgress(range,20);
            check(snapshot.all[0]==3,"All-deck daily encounter count includes other levels and deduplicates repeats");
            check(snapshot.rangeToday==2,"Range daily encounters exclude out-of-scope words");
            check(snapshot.pass.today==1&&snapshot.pass.encountered==2,"Today's first exposure excludes old words revisited today");
            check(snapshot.pass.today<=snapshot.rangeToday&&snapshot.rangeToday<=snapshot.all[0],"Home snapshot keeps all three counting scopes consistent");
            store.familiar(a,true);int[] evidence=store.evidence(range,System.currentTimeMillis());check(evidence[0]==2&&evidence[1]==1&&evidence[2]==0,"Manual familiarity cannot manufacture spaced recall evidence");
            long now=System.currentTimeMillis();store.rate(a,2,now);store.rate(a,2,now+ReviewPlan.DAY+1);store.rate(a,2,now+4*ReviewPlan.DAY+2);
            check(store.evidence(range,now)[2]==1,"Spaced recall evidence counts a third-stage remembered card");
            store.rate(a,0,now+5*ReviewPlan.DAY);check(store.evidence(range,now)[2]==0&&store.evidence(range,now)[1]==1,"A lapse removes stable evidence without clearing the user's manual mark");
            store.favorite(b,true);String json=store.backup();JSONObject original=new JSONObject(json);BackupPreview preview=BackupPreview.inspect(json);
            check(preview.favorites==1&&preview.familiar==1&&preview.encountered==3&&preview.reviews==1,"Backup preview counts the actual independent learning states");
            check(!preview.exported.contains("未记录"),"Backup preview formats a recorded export instant");
            String before=BackupSettings.export(isolated.getSharedPreferences("preferences",0)).toString();
            JSONObject withSettings=new JSONObject(json).put("preferences",new JSONObject().put("dailyNewTarget",77));BackupPreview.inspect(withSettings.toString());
            check(new Prefs(isolated).dailyNewTarget()==20&&before.equals(BackupSettings.export(isolated.getSharedPreferences("preferences",0)).toString()),"Reading a backup preview never applies its settings");
            check(new JSONObject(store.backup()).getJSONArray("progress").toString().equals(original.getJSONArray("progress").toString()),"Reading a preview never merges progress");
            BackupPreview legacy=BackupPreview.inspect(prior.toString());check(!legacy.settings&&legacy.exported.contains("未记录"),"Legacy backup preview explicitly reports missing settings and date");
            JSONObject duplicate=new JSONObject(json);duplicate.getJSONArray("progress").put(duplicate.getJSONArray("progress").getJSONObject(1));check(BackupPreview.inspect(duplicate.toString()).favorites==1,"Duplicate backup rows never inflate preview word counts");
            JSONObject bad=new JSONObject(json).put("preferences",new JSONObject().put("dailyNewTarget",0));
            try{BackupPreview.inspect(bad.toString());throw new AssertionError("Invalid preference preview accepted");}catch(JSONException expected){}check(true,"Invalid preferences are rejected before preview confirmation");
            bad=new JSONObject(json).put("reviews",new JSONArray().put(new JSONObject().put("id",a.id).put("due_at",1).put("reviewed_at",2).put("stage",1).put("rating",2)));
            try{BackupPreview.inspect(bad.toString());throw new AssertionError("Invalid review preview accepted");}catch(IllegalArgumentException expected){}check(true,"Invalid review timestamps are rejected before restore");
            bad=new JSONObject(json).put("encounters",new JSONArray().put(new JSONObject().put("id",a.id).put("day","invalid")));
            try{BackupPreview.inspect(bad.toString());throw new AssertionError("Invalid date accepted");}catch(java.time.format.DateTimeParseException expected){}check(true,"Malformed encounter dates cannot enter a restore preview");
            bad=new JSONObject(json).put("exposures","not an array");try{BackupPreview.inspect(bad.toString());throw new AssertionError("Wrong array type accepted");}catch(JSONException expected){}check(true,"Wrong optional-record types are rejected rather than silently ignored");
            check(store.homeProgress(range,20).all[0]==3&&new Prefs(isolated).dailyNewTarget()==20,"Rejected previews leave learning records and preferences unchanged");
            return checks;
        }finally{db.close();SQLiteDatabase.deleteDatabase(fixture);}
    }
}

package com.myles.kotobafloat;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
import java.time.*;

final class Feature16Tests {
    private int checks;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    static int run(Context target)throws Exception{return new Feature16Tests().test(target);}
    private int test(Context target)throws Exception{
        java.io.File file=new java.io.File(target.getCacheDir(),"features-1.6.db");SQLiteDatabase.deleteDatabase(file);
        Context isolated=new ContextWrapper(target){@Override public java.io.File getDatabasePath(String n){return file;}@Override public SharedPreferences getSharedPreferences(String n,int mode){return target.getSharedPreferences("features-1.6",mode);}};
        isolated.getSharedPreferences("preferences",0).edit().clear().commit();java.lang.reflect.Constructor<WordStore> ctor=WordStore.class.getDeclaredConstructor(Context.class);ctor.setAccessible(true);WordStore store=ctor.newInstance(isolated);java.lang.reflect.Field f=WordStore.class.getDeclaredField("db");f.setAccessible(true);SQLiteDatabase db=(SQLiteDatabase)f.get(store);
        try{
            Prefs p=new Prefs(isolated);check(p.speakOnFlip()&&!p.autoSpeak(),"Manual flip audio defaults on independently from automatic audio");
            check(store.words("ALL","TABERU","all",false,false,100).stream().anyMatch(w->w.word.equals("食べる")),"Actual romaji search finds 食べる");
            check(store.words("ALL","すーぱーこんぴゅーたー","all",false,false,100).stream().anyMatch(w->w.word.equals("スーパーコンピューター")),"Actual hiragana search finds a katakana headword");
            check(store.words("ALL","ｽｰﾊﾟｰｺﾝﾋﾟｭｰﾀｰ","all",false,false,100).stream().anyMatch(w->w.word.equals("スーパーコンピューター")),"Half-width kana are normalized");
            check(store.words("N5","すーぱーこんぴゅーたー","all",false,false,100).isEmpty(),"Normalized search still respects the selected level");
            Word a=store.words("N5","","all",false,false,1).get(0);List<Word> range=Collections.singletonList(a);long start=LocalDate.now().minusDays(10).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),day=ReviewPlan.DAY;
            store.rate(a,0,start);store.rate(a,0,start+1000);check(store.weak(range,5).isEmpty(),"Repeated forgetting on one date never labels a word weak");
            store.rate(a,0,start+day);check(store.weak(range,5).equals(Collections.singletonList(a.id)),"Forgetting on two dates enters the weak list");
            JSONObject old=new JSONObject(store.backup());store.rateWeak(a,2,start+day+1000);check(store.weak(range,5).size()==1,"Remembering immediately after forgetting cannot graduate");
            store.rateWeak(a,2,start+2*day);store.rateWeak(a,2,start+3*day);check(store.weak(range,5).size()==1,"Two later remembered dates still retain the weak word");
            store.rateWeak(a,2,start+4*day);check(store.weak(range,5).isEmpty(),"Three later remembered dates graduate the weak word");
            JSONObject graduated=new JSONObject(store.backup());check(old.getJSONArray("reviews").toString().equals(graduated.getJSONArray("reviews").toString())&&old.getJSONArray("review_days").toString().equals(graduated.getJSONArray("review_days").toString()),"Weak practice never changes scheduled reviews or daily task counts");
            store.restore(old.toString());check(store.weak(range,5).isEmpty(),"Restoring an older backup cannot resurrect a newer graduated word");
            JSONObject legacy=new JSONObject(graduated.toString());legacy.remove("weak_words");store.restore(legacy.toString());check(new JSONObject(store.backup()).getJSONArray("weak_words").length()==1,"Old backups leave new weak-word evidence intact");
            JSONObject bad=new JSONObject(graduated.toString());bad.getJSONArray("weak_words").getJSONObject(0).put("misses",9);bad.put("preferences",new JSONObject().put("wordSize",30));try{store.restore(bad.toString());throw new AssertionError("Invalid weak evidence accepted");}catch(IllegalArgumentException expected){}check(p.wordSize()==23&&store.weak(range,5).isEmpty(),"Invalid weak backups fail before changing records or settings");
            p.data.edit().putBoolean("speakOnFlip",false).putInt("wordSize",30).putInt("readingSize",16).putInt("meaningSize",19).commit();JSONObject settings=BackupSettings.export(p.data);check(!settings.getBoolean("speakOnFlip")&&settings.getInt("wordSize")==30&&settings.getInt("readingSize")==16&&settings.getInt("meaningSize")==19,"Full backups include the new audio and font settings");
            try{BackupSettings.validate(new JSONObject().put("wordSize",99));throw new AssertionError("Invalid font accepted");}catch(JSONException expected){}check(true,"Restore validates font-size bounds");
            return checks;
        }finally{db.close();SQLiteDatabase.deleteDatabase(file);isolated.getSharedPreferences("preferences",0).edit().clear().commit();}
    }
}

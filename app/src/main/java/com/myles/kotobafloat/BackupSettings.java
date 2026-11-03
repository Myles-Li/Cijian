package com.myles.kotobafloat;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class BackupSettings {
    private static final Set<String> STRINGS=new HashSet<>(Arrays.asList("uiLanguage","scopeMemory","learningStyle","accent","customColor","level","theme","frequency","intervalSeconds","studyMode","groupMode","customWords","group.state","queue.state","currentId","voiceEngine"));
    private static final Set<String> BOOLEANS=new HashSet<>(Arrays.asList("reviewAutoSpeak","positionLocked","speakOnFlip","ballAudio","lockAudio","snapEdges","autoStart","shuffle","hideFamiliar","favoritesOnly","readChinese","readExample","autoSpeak","conceal","traditional","showFirstPass","reverseRecall"));
    private static final Set<String> INTS=new HashSet<>(Arrays.asList("reviewBatch","wordSize","readingSize","meaningSize","meaningLines","seconds","width","alpha","wordRepeats","exampleRepeats","groupSize","groupRevision","dailyNewTarget","panelX","panelY","ballX","ballY","focusNewTarget","speechRate","speechGap"));
    static JSONObject export(SharedPreferences prefs)throws JSONException{
        JSONObject out=new JSONObject();for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(STRINGS.contains(e.getKey())||BOOLEANS.contains(e.getKey())||INTS.contains(e.getKey()))out.put(e.getKey(),e.getValue());return out;
    }
    static void validate(JSONObject data)throws JSONException{
        if(data.toString().length()>4000000)throw new JSONException("设置过大");
        for(Iterator<String> keys=data.keys();keys.hasNext();){String key=keys.next();Object value=data.get(key);
            if(STRINGS.contains(key)&&!(value instanceof String)||BOOLEANS.contains(key)&&!(value instanceof Boolean)||INTS.contains(key)&&!(value instanceof Integer))throw new JSONException("设置类型错误："+key);
        }
        if(data.has("reviewBatch")&&!ReviewBatch.valid(data.getInt("reviewBatch")))throw new JSONException("每批词数无效");
        if(data.has("scopeMemory"))ScopeMemory.validate(data.getString("scopeMemory"));
        if(data.has("intervalSeconds"))try{Interval.parseMillis(data.getString("intervalSeconds"));}catch(IllegalArgumentException e){throw new JSONException("切词间隔无效");}
        range(data,"wordSize",16,36);range(data,"readingSize",10,22);range(data,"meaningSize",12,24);
        range(data,"width",220,360);range(data,"alpha",45,100);range(data,"meaningLines",1,3);range(data,"wordRepeats",1,5);range(data,"exampleRepeats",1,5);range(data,"groupSize",1,10641);range(data,"dailyNewTarget",1,10641);
        range(data,"focusNewTarget",1,200);range(data,"speechRate",50,150);range(data,"speechGap",0,3000);oneOf(data,"learningStyle","ROAM","FOCUS");
        oneOf(data,"accent",Accent.IDS);if(data.has("customColor"))try{Accent.parse(data.getString("customColor"));}catch(IllegalArgumentException e){throw new JSONException("自定义色号无效");}
        oneOf(data,"uiLanguage","ZH","EN","JA");oneOf(data,"level","ALL","N1","N2","N3","N4","N5");oneOf(data,"theme","AUTO","LIGHT","DARK");oneOf(data,"studyMode","all","favorites","familiar");oneOf(data,"groupMode","OFF","SIZE","CUSTOM");
    }
    private static void range(JSONObject o,String k,int min,int max)throws JSONException{if(o.has(k)&&(o.getInt(k)<min||o.getInt(k)>max))throw new JSONException("设置超出范围："+k);}
    private static void oneOf(JSONObject o,String k,String... choices)throws JSONException{if(o.has(k)&&!Arrays.asList(choices).contains(o.getString(k)))throw new JSONException("设置值无效："+k);}
    static void restore(SharedPreferences prefs,JSONObject data)throws JSONException{
        validate(data);SharedPreferences.Editor edit=prefs.edit();
        for(Iterator<String> keys=data.keys();keys.hasNext();){String k=keys.next();if(STRINGS.contains(k))edit.putString(k,data.getString(k));else if(BOOLEANS.contains(k))edit.putBoolean(k,data.getBoolean(k));else if(INTS.contains(k))edit.putInt(k,data.getInt(k));}
        if(data.has("scopeMemory"))edit.putBoolean("scopeMemoryMigrated",true);
        else if(data.has("queue.state")||data.has("group.state"))edit.remove("scopeMemory").putBoolean("scopeMemoryMigrated",false);
        if(!edit.commit())throw new IllegalStateException("设置保存失败");
    }
}

package com.myles.cijian;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) { data=context.getSharedPreferences("preferences", Context.MODE_PRIVATE); }
    // 识别旧中英版保存的状态文字，但不启用英文界面。 / Recognize saved status text from the archived bilingual build without enabling its language mode.
    String status(String key){
        String text=data.getString(key,"");
        String[][] legacy={{"Last share file created: ","上次生成分享备份："},{"Last saved to Downloads: ","上次保存到下载："},{"Last saved file: ","上次保存文件："},{"Auto-start requires overlay permission first","自动启动需要先允许悬浮显示"},{"Requested floating study after boot","已请求开机恢复悬浮学习"},{"System blocked auto-start. Check startup and background permissions.","系统阻止了自动启动，请检查自启动和后台运行权限"},{"Dictionary updated; learning records retained.","词库内容已更新，学习记录保留。"},{"The dictionary update could not be applied safely. The previous dictionary is still in use; keep your records and contact the maintainer.","新词库未能安全更新，继续使用原词库。请保留记录并联系维护者。"}};
        for(String[] pair:legacy)if(text.startsWith(pair[0]))return pair[1]+text.substring(pair[0].length());
        return text;
    }
    String learningStyle(){return data.getString("learningStyle","ROAM");}
    int focusNewTarget(){return Math.max(1,Math.min(200,data.getInt("focusNewTarget",10)));}
    int reviewBatch(){int n=data.getInt("reviewBatch",ReviewBatch.DEFAULT);return ReviewBatch.valid(n)?n:ReviewBatch.DEFAULT;}
    boolean reviewAutoSpeak(){return data.getBoolean("reviewAutoSpeak",true);}
    boolean reverseRecall(){return data.getBoolean("reverseRecall",false);}
    float speechRate(){return Math.max(50,Math.min(150,data.getInt("speechRate",85)))/100f;}
    int speechGap(){return Math.max(0,Math.min(3000,data.getInt("speechGap",300)));}
    String level() { return data.getString("level", "N5"); }
    int dailyNewTarget(){return Math.max(1,Math.min(10641,data.getInt("dailyNewTarget",20)));}
    boolean showFirstPass(){return data.getBoolean("showFirstPass",true);}
    String accent(){return data.getString("accent","BLACK");}
    String customColor(){return data.getString("customColor","#32715F");}
    String theme() { return data.getString("theme","AUTO"); }
    boolean ballAudio() { return data.getBoolean("ballAudio",false); }
    boolean lockAudio() { return data.getBoolean("lockAudio",false); }
    String frequency() { return data.getString("frequency", "ALL"); }
    int meaningLines() { return Math.max(1,Math.min(3,data.getInt("meaningLines",2))); }
    boolean positionLocked(){return data.getBoolean("positionLocked",false);}
    boolean snapEdges() { return data.getBoolean("snapEdges",false); }
    boolean autoStart() { return data.getBoolean("autoStart",false); }
    long intervalMillis() {
        String value=data.getString("intervalSeconds",null);
        if(value==null)return Math.max(100L,Math.min(3600000L,data.getInt("seconds",15)*1000L));
        try{return Interval.parseMillis(value);}catch(IllegalArgumentException invalid){return 15000L;}
    }
    String intervalLabel(){return Interval.format(intervalMillis());}
    int width() { return data.getInt("width", 260); }
    int alpha() { return data.getInt("alpha", 95); }
    boolean shuffle() { return data.getBoolean("shuffle", false); }
    boolean hideFamiliar() { return data.getBoolean("hideFamiliar", true); }
    boolean favoritesOnly() { return data.getBoolean("favoritesOnly", false); }
    String studyMode() { return data.getString("studyMode", favoritesOnly()?"favorites":"all"); }
    boolean effectiveHideFamiliar() { return !studyMode().equals("familiar")&&hideFamiliar(); }
    String groupMode() { return data.getString("groupMode","OFF"); }
    int groupSize() { return Math.max(1,Math.min(10641,data.getInt("groupSize",20))); }
    java.util.List<String> customWords() {
        java.util.LinkedHashSet<String> ids=new java.util.LinkedHashSet<>();
        for(String id:data.getString("customWords","").split("\n"))if(!id.isEmpty())ids.add(id);
        return new java.util.ArrayList<>(ids);
    }
    int wordRepeats() { return SpeechPlan.clamp(data.getInt("wordRepeats",1)); }
    int exampleRepeats() { return SpeechPlan.clamp(data.getInt("exampleRepeats",1)); }
    boolean readChinese() { return data.getBoolean("readChinese",false); }
    boolean readExample() { return data.getBoolean("readExample",false); }
    boolean speakOnFlip(){return data.getBoolean("speakOnFlip",true);}
    int wordSize(){return Math.max(16,Math.min(36,data.getInt("wordSize",23)));}
    int readingSize(){return Math.max(10,Math.min(22,data.getInt("readingSize",12)));}
    int meaningSize(){return Math.max(12,Math.min(24,data.getInt("meaningSize",14)));}
    // App字号与悬浮字号独立，仍由sp继承系统缩放。 / Independent in-app sizes retain system sp scaling.
    int appWordSize(){return Math.max(18,Math.min(48,data.getInt("appWordSize",34)));}
    int appReadingSize(){return Math.max(10,Math.min(28,data.getInt("appReadingSize",15)));}
    int appMeaningSize(){return Math.max(12,Math.min(32,data.getInt("appMeaningSize",21)));}
    float appSize(float base,int role){return base*(role==0?appWordSize()/34f:role==1?appReadingSize()/15f:appMeaningSize()/21f);}
    int detailHeight(){return Math.max(100,Math.min(420,data.getInt("detailHeight",180)));}
    boolean autoSpeak() { return data.getBoolean("autoSpeak", false); }
    boolean conceal() { return data.getBoolean("conceal", false); }
    boolean traditional() { return data.getBoolean("traditional", false); }
    void put(String key, String value) { data.edit().putString(key,value).apply(); }
    void put(String key, boolean value) { data.edit().putBoolean(key,value).apply(); }
    void put(String key, int value) { data.edit().putInt(key,value).apply(); }
}

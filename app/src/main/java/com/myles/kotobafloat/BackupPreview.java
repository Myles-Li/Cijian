package com.myles.kotobafloat;

import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 只读取校验备份，不写设置或数据库。 / Read and validate a backup without touching preferences or the database. */
final class BackupPreview {
    final String exported;final int favorites,familiar,encountered,reviews,days,weak;final boolean settings;
    private BackupPreview(String exported,int favorites,int familiar,int encountered,int reviews,int days,int weak,boolean settings){this.exported=exported;this.favorites=favorites;this.familiar=familiar;this.encountered=encountered;this.reviews=reviews;this.days=days;this.weak=weak;this.settings=settings;}
    static BackupPreview inspect(String json)throws JSONException {
        JSONObject o=new JSONObject(json);String format=o.getString("format");
        if(!format.equals("kotoba-float-progress-v1")&&!format.equals("kotoba-float-progress-v2"))throw new IllegalArgumentException("这不是词间的备份文件");
        JSONArray progress=o.getJSONArray("progress"),encounters=o.getJSONArray("encounters");limit(progress,50000);limit(encounters,1000000);
        Set<String> favorites=new HashSet<>(),familiar=new HashSet<>(),seen=new HashSet<>(),reviewed=new HashSet<>(),days=new HashSet<>();
        for(int i=0;i<progress.length();i++){JSONObject p=progress.getJSONObject(i);String id=id(p);if(p.getInt("favorite")==1)favorites.add(id);if(p.getInt("familiar")==1)familiar.add(id);if(p.getInt("seen")>0)seen.add(id);p.getLong("last_seen");}
        for(int i=0;i<encounters.length();i++){JSONObject e=encounters.getJSONObject(i);seen.add(id(e));days.add(LocalDate.parse(e.getString("day")).toString());}
        JSONArray exposures=optional(o,"exposures"),reviews=optional(o,"reviews"),started=optional(o,"review_started"),history=optional(o,"review_days");limit(exposures,50000);limit(reviews,50000);limit(started,50000);limit(history,1000000);
        if(exposures!=null)for(int i=0;i<exposures.length();i++){JSONObject e=exposures.getJSONObject(i);seen.add(id(e));LocalDate.parse(e.getString("first_day"));}
        if(reviews!=null)for(int i=0;i<reviews.length();i++){JSONObject e=reviews.getJSONObject(i);reviewed.add(id(e));int stage=e.getInt("stage"),rating=e.getInt("rating");long time=e.getLong("reviewed_at"),due=e.getLong("due_at");if(stage<0||stage>5||rating<0||rating>2||time<0||due<time)throw new IllegalArgumentException("无效复习记录");}
        if(started!=null)for(int i=0;i<started.length();i++){JSONObject e=started.getJSONObject(i);id(e);LocalDate.parse(e.getString("day"));}
        if(history!=null)for(int i=0;i<history.length();i++){JSONObject e=history.getJSONObject(i);id(e);LocalDate.parse(e.getString("day"));int a=e.getInt("attempts"),r=e.getInt("remembered"),f=e.getInt("forgotten");if(a<0||r<0||f<0||r>a||f>a)throw new IllegalArgumentException("无效复习次数");}
        JSONArray weak=optional(o,"weak_words");limit(weak,50000);
        if(weak!=null)for(int i=0;i<weak.length();i++){JSONObject e=weak.getJSONObject(i);id(e);WeakWords.validate(new WeakWords.State(e.getInt("misses"),e.getInt("wins"),e.getString("missed"),e.getString("won"),e.getLong("updated_at")));}
        JSONArray marks=optional(o,"mark_changes");limit(marks,100000);Set<String> markKeys=new HashSet<>();
        if(marks!=null)for(int i=0;i<marks.length();i++){JSONObject m=marks.getJSONObject(i);String id=id(m),field=m.getString("field");if(!field.equals("favorite")&&!field.equals("familiar")||m.getInt("value")<0||m.getInt("value")>1||m.getLong("changed_at")<0||m.getLong("changed_at")>253402300799999L||!markKeys.add(id+"|"+field))throw new IllegalArgumentException("无效标记时间");}
        JSONObject preferences=null;if(o.has("preferences")&&!o.isNull("preferences")){preferences=o.getJSONObject("preferences");BackupSettings.validate(preferences);}
        String date="未记录（旧版备份）";try{date=Instant.parse(o.getString("exported")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}catch(JSONException|java.time.format.DateTimeParseException ignored){}
        return new BackupPreview(date,favorites.size(),familiar.size(),seen.size(),reviewed.size(),days.size(),weak==null?0:weak.length(),preferences!=null&&preferences.length()>0);
    }
    private static JSONArray optional(JSONObject o,String key)throws JSONException{return !o.has(key)||o.isNull(key)?null:o.getJSONArray(key);}
    private static void limit(JSONArray a,int max){if(a!=null&&a.length()>max)throw new IllegalArgumentException("备份文件过大");}
    private static String id(JSONObject o)throws JSONException{String id=o.getString("id");WordStore.validId(id);return id;}
    String summary(){return L.t("备份时间：")+exported+L.t("\n\n收藏 ")+favorites+L.t(" 词 · 手动熟悉 ")+familiar+L.t(" 词\n有接触记录 ")+encountered+L.t(" 词\n正式复习记录 ")+reviews+L.t(" 词 · 遇见日期 ")+days+L.t(" 天\n易忘词状态 ")+weak+L.t(" 条（含已移出名单的历史）\n\n")+(settings?L.t("包含设置：可在下方选择是否恢复设置、自选组及浏览位置。"):L.t("不含设置：本机设置、自选组和队列位置保持原样。"))+L.t("\n\n以上为文件内容，合并后总数按双方记录和所选规则确定。学习记录合并，不会相加刷高次数；带时间的标记采用较新状态；旧标记冲突按下方选择处理。未匹配当前词库的历史也会保留。易忘词状态采用较新记录，旧版备份没有此项时保留本机状态。\n系统权限与语音引擎不在备份中。");}
}

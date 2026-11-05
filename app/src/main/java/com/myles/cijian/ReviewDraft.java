package com.myles.cijian;

import org.json.*;
import java.util.*;

/** 持久保存批次位置，通过修订号避免重复评分与推进。 / A durable batch cursor. Revision makes rating and advancing idempotent. */
final class ReviewDraft {
    String token=UUID.randomUUID().toString(),scope,mode,day;int revision,remembered,missed;boolean revealed,reverse;
    ReviewSession session;
    ReviewDraft(String scope,String mode,boolean reverse,Collection<String> ids){this.scope=scope;this.mode=mode;this.reverse=reverse;day=java.time.LocalDate.now().toString();session=new ReviewSession(ids,0);}
    static ReviewDraft parse(String text)throws JSONException{
        JSONObject o=new JSONObject(text);JSONArray ids=o.getJSONArray("ids");if(ids.length()>100)throw new JSONException("复习批次过大");List<String> queue=new ArrayList<>();for(int i=0;i<ids.length();i++){String id=ids.getString(i);WordStore.validId(id);queue.add(id);}
        ReviewDraft d=new ReviewDraft(o.getString("scope"),o.getString("mode"),o.getBoolean("reverse"),queue);d.token=o.getString("token");d.day=o.getString("day");java.time.LocalDate.parse(d.day);d.revision=o.getInt("revision");d.remembered=o.getInt("remembered");d.missed=o.getInt("missed");d.revealed=o.getBoolean("revealed");int p=o.getInt("position");if(p<0||p>queue.size()||d.revision<0||d.remembered<0||d.missed<0||d.remembered+d.missed>p||d.token.length()>100)throw new JSONException("复习位置无效");d.session.position=p;
        JSONObject attempts=o.getJSONObject("attempts");for(Iterator<String> it=attempts.keys();it.hasNext();){String id=it.next();int n=attempts.getInt(id);if(!queue.contains(id)||n<1||n>2)throw new JSONException("复习次数无效");d.session.attempts.put(id,n);}return d;
    }
    String json(){try{return new JSONObject().put("token",token).put("scope",scope).put("mode",mode).put("day",day).put("reverse",reverse).put("revision",revision).put("remembered",remembered).put("missed",missed).put("revealed",revealed).put("position",session.position).put("ids",new JSONArray(session.queue)).put("attempts",new JSONObject(session.attempts)).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
    ReviewDraft copy(){try{return parse(json());}catch(JSONException e){throw new IllegalStateException(e);}}
    boolean compatible(String wantedScope,String wantedMode,boolean wantedReverse){return scope.equals(wantedScope)&&mode.equals(wantedMode)&&reverse==wantedReverse;}
}

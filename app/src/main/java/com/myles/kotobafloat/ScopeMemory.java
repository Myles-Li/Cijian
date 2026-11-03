package com.myles.kotobafloat;

import org.json.*;

/** 位置按范围保存，学习记录和复习证据仍共用。 / Position history is scoped; progress and review evidence remain shared. */
final class ScopeMemory {
    private static final String KEY="scopeMemory";
    static synchronized void migrate(Prefs p){
        if(p.data.getBoolean("scopeMemoryMigrated",false))return;
        try{
            JSONObject all=read(p),entry=new JSONObject();String base=StudySession.baseScope(p);
            entry.put("group",p.data.getString("group.state",""));
            JSONObject positions=new JSONObject(),legacy=new JSONObject().put("id",p.data.getString("currentId","")).put("queue",p.data.getString("queue.state",""));
            positions.put("legacy",legacy);entry.put("positions",positions);all.remove(base);all.put(base,entry);
            // 最后保存的固定组可能属于当前未选择的范围。 / The last saved fixed group may belong to a range that is not currently selected.
            String oldGroup=p.data.getString("group.state","");int newline=oldGroup.indexOf('\n');
            if(newline>0){String groupScope=oldGroup.substring(0,newline);String[] parts=groupScope.split("\\|");if(groupScope.length()<=1024&&parts.length>=6&&parts[4].equals("SIZE")){JSONObject groupEntry=all.optJSONObject(groupScope);if(groupEntry==null)groupEntry=new JSONObject();groupEntry.put("group",oldGroup);all.put(groupScope,groupEntry);}}
            trim(all);
            if(!p.data.edit().putString(KEY,all.toString()).putBoolean("scopeMemoryMigrated",true).commit())throw new IllegalStateException("无法保存范围位置");
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    private static void trim(JSONObject all){while(all.length()>1&&(all.length()>48||all.toString().length()>2000000))all.remove(all.keys().next());}
    private static JSONObject read(Prefs p){try{return new JSONObject(p.data.getString(KEY,"{}"));}catch(JSONException bad){return new JSONObject();}}
    static synchronized String group(Prefs p,String scope){JSONObject e=read(p).optJSONObject(scope);return e==null?"":e.optString("group","");}
    static synchronized void group(Prefs p,String scope,String state){
        try{JSONObject all=read(p),e=all.optJSONObject(scope);if(e==null)e=new JSONObject();e.put("group",state);all.remove(scope);all.put(scope,e);trim(all);p.put(KEY,all.toString());p.put("group.state",state);}catch(JSONException bad){throw new IllegalStateException(bad);}
    }
    static synchronized String position(Prefs p,String base,String scope,boolean shuffle,String field){
        JSONObject e=read(p).optJSONObject(base);JSONObject positions=e==null?null:e.optJSONObject("positions");if(positions==null)return "";
        JSONObject value=positions.optJSONObject(scope+"|shuffle="+shuffle);if(value==null)value=positions.optJSONObject("legacy");return value==null?"":value.optString(field,"");
    }
    static synchronized void save(Prefs p,String base,String scope,boolean shuffle,String id,String queue){
        try{
            JSONObject all=read(p),e=all.optJSONObject(base);if(e==null)e=new JSONObject();JSONObject positions=e.optJSONObject("positions");if(positions==null)positions=new JSONObject();
            positions.remove("legacy");positions.remove(scope+"|shuffle="+shuffle);positions.put(scope+"|shuffle="+shuffle,new JSONObject().put("id",id).put("queue",queue));
            // 历史有容量上限，优先保留最近访问的范围。 / A bounded history, keeping the most recently visited scope entries.
            while(positions.length()>32)positions.remove(positions.keys().next());
            e.put("positions",positions);all.remove(base);all.put(base,e);
            trim(all);
            p.data.edit().putString(KEY,all.toString()).putString("currentId",id).putString("queue.state",queue).apply();
        }catch(JSONException bad){throw new IllegalStateException(bad);}
    }
    static void validate(String json)throws JSONException{
        JSONObject all=new JSONObject(json);if(all.length()>48||json.length()>2000000)throw new JSONException("范围历史过大");
        for(java.util.Iterator<String> keys=all.keys();keys.hasNext();){String key=keys.next();if(key.length()>1024)throw new JSONException("范围标记无效");JSONObject e=all.getJSONObject(key);if(e.has("group")&&!(e.get("group") instanceof String))throw new JSONException("分组历史无效");if(e.has("positions")&&!(e.get("positions") instanceof JSONObject))throw new JSONException("位置历史无效");JSONObject ps=e.optJSONObject("positions");if(ps!=null){if(ps.length()>11000)throw new JSONException("位置历史过大");for(java.util.Iterator<String> it=ps.keys();it.hasNext();){JSONObject v=ps.getJSONObject(it.next());if(!(v.get("id") instanceof String)||!(v.get("queue") instanceof String))throw new JSONException("位置历史无效");}}}
    }
}

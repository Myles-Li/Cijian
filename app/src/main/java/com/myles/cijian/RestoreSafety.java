package com.myles.cijian;

import android.content.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** 导入前保存私有快照，以恢复日志协调数据库与设置提交。 / A private pre-import snapshot and recovery journal across database/preferences commits. */
final class RestoreSafety {
    private static File file(Context c,String part){return new File(c.getFilesDir(),"restore-"+Integer.toHexString(c.getDatabasePath("cijian.db").getAbsolutePath().hashCode())+"-"+part+".json");}
    private static void write(File f,String data)throws IOException{AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(data.getBytes(StandardCharsets.UTF_8));a.finishWrite(out);if(!new String(a.readFully(),StandardCharsets.UTF_8).equals(data))throw new IOException("恢复快照未能完整保存");}catch(IOException|RuntimeException e){if(out!=null)a.failWrite(out);throw e;}}
    private static String read(File f)throws IOException{if(f.length()>64L*1024*1024)throw new IOException("快照过大");return new String(new AtomicFile(f).readFully(),StandardCharsets.UTF_8);}
    private static void clearPending(Context c)throws IOException{File f=file(c,"pending");new AtomicFile(f).delete();if(f.exists())throw new IOException("恢复日志未能安全清除");}
    static boolean available(Context c){return file(c,"snapshot").exists();}
    static synchronized void recover(Context c,WordStore store){File pending=file(c,"pending");if(!pending.exists())return;try{store.exactRestore(read(file(c,"snapshot")));clearPending(c);}catch(Exception e){throw new IllegalStateException("上次恢复尚未安全完成；已保留本机恢复快照，请保留应用数据后重试",e);}}
    static synchronized void apply(Context c,WordStore store,String json,boolean settings,boolean keepLocal)throws Exception{
        BackupPreview.inspect(json);recover(c,store);write(file(c,"snapshot"),store.localSnapshot());write(file(c,"pending"),"{}");
        try{store.restore(json,settings,keepLocal);store.discardDrafts();clearPending(c);}
        catch(Exception failure){try{store.exactRestore(read(file(c,"snapshot")));clearPending(c);}catch(Exception rollback){failure.addSuppressed(rollback);}throw failure;}
    }
    static synchronized void undo(Context c,WordStore store)throws Exception{String snapshot=read(file(c,"snapshot"));write(file(c,"pending"),"{}");store.exactRestore(snapshot);clearPending(c);new AtomicFile(file(c,"snapshot")).delete();}
    static JSONObject encodePreferences(SharedPreferences prefs)throws JSONException{
        JSONObject all=new JSONObject();for(Map.Entry<String,?> e:prefs.getAll().entrySet()){Object value=e.getValue();String kind=value instanceof Boolean?"b":value instanceof Integer?"i":value instanceof Long?"l":value instanceof Float?"f":value instanceof Set?"set":"s";all.put(e.getKey(),new JSONObject().put("type",kind).put("value",value instanceof Set?new JSONArray((Set<?>)value):value));}return all;
    }
    static void validatePreferences(JSONObject all)throws JSONException{if(all.toString().length()>5000000)throw new JSONException("本机设置快照过大");for(Iterator<String> it=all.keys();it.hasNext();){JSONObject e=all.getJSONObject(it.next());String t=e.getString("type");switch(t){case "b":if(!(e.get("value") instanceof Boolean))throw new JSONException("boolean");break;case "i":e.getInt("value");break;case "l":e.getLong("value");break;case "f":e.getDouble("value");break;case "s":e.getString("value");break;case "set":JSONArray a=e.getJSONArray("value");for(int i=0;i<a.length();i++)a.getString(i);break;default:throw new JSONException("设置快照类型无效");}}}
    static void restorePreferences(SharedPreferences prefs,JSONObject all)throws JSONException{
        validatePreferences(all);SharedPreferences.Editor edit=prefs.edit().clear();for(Iterator<String> it=all.keys();it.hasNext();){String key=it.next();JSONObject e=all.getJSONObject(key);switch(e.getString("type")){case "b":edit.putBoolean(key,e.getBoolean("value"));break;case "i":edit.putInt(key,e.getInt("value"));break;case "l":edit.putLong(key,e.getLong("value"));break;case "f":edit.putFloat(key,(float)e.getDouble("value"));break;case "s":edit.putString(key,e.getString("value"));break;case "set":Set<String> set=new HashSet<>();JSONArray a=e.getJSONArray("value");for(int i=0;i<a.length();i++)set.add(a.getString(i));edit.putStringSet(key,set);break;}}
        edit.putBoolean("session.enabled",false);if(!edit.commit())throw new IllegalStateException("本机设置未能保存");
    }
}

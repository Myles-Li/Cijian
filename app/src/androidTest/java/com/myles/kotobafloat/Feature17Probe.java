package com.myles.kotobafloat;
import android.app.*;import android.os.Bundle;import org.json.*;import android.database.sqlite.*;import java.util.*;
/** 专用模拟器测试数据及只读检查探针，不包含在正式安装包。 / Disposable emulator fixture and read-only checkpoint probe. Never in the production APK. */
public final class Feature17Probe extends Instrumentation {
    private String command="snapshot";
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null)command=b.getString("mode","snapshot");start();}
    @Override public void onStart(){Bundle out=new Bundle();try{
        WordStore store=WordStore.get(getTargetContext());java.lang.reflect.Field f=WordStore.class.getDeclaredField("db");f.setAccessible(true);SQLiteDatabase db=(SQLiteDatabase)f.get(store);
        if(command.equals("prepare")){db.delete("review_drafts",null,null);long old=System.currentTimeMillis()-8*ReviewPlan.DAY;for(Word w:store.words("N5","","all",false,false,25))store.rate(w,2,old);}
        if(command.equals("interruptRestore")){
            JSONObject imported=new JSONObject(store.backup());imported.getJSONObject("preferences").put("wordSize",20).put("accent","RED");RestoreSafety.apply(getTargetContext(),store,imported.toString(),true,true);
            java.io.File pending=new java.io.File(getTargetContext().getFilesDir(),"restore-"+Integer.toHexString(getTargetContext().getDatabasePath("kotoba.db").getAbsolutePath().hashCode())+"-pending.json");try(java.io.FileOutputStream stream=new java.io.FileOutputStream(pending)){stream.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));stream.getFD().sync();}
        }
        JSONObject result=new JSONObject().put("backup",new JSONObject(store.backup())).put("day",java.time.LocalDate.now().toString()),drafts=new JSONObject();
        for(String mode:new String[]{"TASK","LIGHT","DUE","PRACTICE","WEAK"}){ReviewDraft d=store.draft(mode);if(d!=null)drafts.put(mode,new JSONObject(d.json()));}result.put("drafts",drafts);
        out.putString("stream","KOTOBA_PROBE="+result.toString()+"\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

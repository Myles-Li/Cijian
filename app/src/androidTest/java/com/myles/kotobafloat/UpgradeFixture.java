package com.myles.kotobafloat;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.List;

/** 只使用 1.0 已有接口，以同一测试工具检查升级前后版本。 / Uses only the 1.0 public/package API so the same fixture can inspect both installed versions. */
public final class UpgradeFixture extends Instrumentation {
    private boolean seed,seed15;
    @Override public void onCreate(Bundle args){super.onCreate(args);seed=args!=null&&"seed".equals(args.getString("mode"));seed15=args!=null&&"seed15".equals(args.getString("mode"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            WordStore store=WordStore.get(getTargetContext());
            if(seed15)seed15(store);
            if(seed){
                List<Word> words=store.words("N5","","all",false,false,2);
                store.favorite(words.get(0),true);store.familiar(words.get(1),true);store.seen(words.get(0));
            }
            JSONObject data=new JSONObject(store.backup());data.remove("exported");
            result.putString("stream","KOTOBA_BACKUP="+data.toString()+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
    /** 只用于专用模拟器界面测试，不包含在正式安装包。 / Disposable-emulator UI fixtures only; this class is absent from the production APK. */
    private void seed15(WordStore store)throws Exception{
        java.lang.reflect.Field field=WordStore.class.getDeclaredField("db");field.setAccessible(true);android.database.sqlite.SQLiteDatabase db=(android.database.sqlite.SQLiteDatabase)field.get(store);
        List<Word> words=store.words("N3","HIGH_MID","","all",false,false,35);long now=System.currentTimeMillis(),day=86400000L;String yesterday=java.time.LocalDate.now().minusDays(1).toString();
        db.beginTransaction();try{
            for(String table:new String[]{"progress","encounters","exposures","reviews","review_started","review_days"})db.delete(table,null,null);
            for(int i=0;i<26;i++){Word w=words.get(i==25?30:i);db.execSQL("INSERT INTO progress(id,seen,last_seen,familiar) VALUES (?,1,?,?)",new Object[]{w.id,now-day,i==0?1:0});db.execSQL("INSERT INTO exposures VALUES (?,?)",new Object[]{w.id,yesterday});db.execSQL("INSERT INTO encounters VALUES (?,?)",new Object[]{yesterday,w.id});db.execSQL("INSERT INTO reviews VALUES (?,?,?,?,?)",new Object[]{w.id,i==25?now+7*day:now-day,i==25?3:2,2,now-4*day});db.execSQL("INSERT INTO review_started VALUES (?,?)",new Object[]{w.id,yesterday});}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        store.seen(words.get(0));store.seen(words.get(29));store.seen(store.words("N5","","all",false,false,1).get(0));
    }

}

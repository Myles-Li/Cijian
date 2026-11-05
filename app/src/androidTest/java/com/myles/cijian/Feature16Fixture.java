package com.myles.cijian;

import android.app.*;
import android.os.Bundle;
import java.util.*;

/** 仅操作专用模拟器数据，不包含在正式安装包。 / Only disposable-emulator data; excluded from the production APK. */
public final class Feature16Fixture extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle out=new Bundle();try{
        WordStore store=WordStore.get(getTargetContext());java.lang.reflect.Field f=WordStore.class.getDeclaredField("db");f.setAccessible(true);android.database.sqlite.SQLiteDatabase db=(android.database.sqlite.SQLiteDatabase)f.get(store);db.delete("weak_words",null,null);
        long noon=java.time.LocalDate.now().minusDays(3).atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        for(Word w:store.words("N5","","all",false,false,7)){store.rateWeak(w,0,noon);store.rateWeak(w,0,noon+ReviewPlan.DAY);}
        out.putString("stream","PASS: seeded seven weak words from two dates\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

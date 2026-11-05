package com.myles.cijian;
import android.app.*;import android.os.Bundle;
public final class Feature17Instrumentation extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle out=new Bundle();try{int n=Feature17Tests.run(getTargetContext());out.putString("stream","PASS: "+n+" isolated 1.7 checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

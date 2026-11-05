package com.myles.cijian;
import android.app.*;import android.os.Bundle;import java.util.*;
/** 仅使用归档 1.6 已有接口，只操作专用模拟器。 / Uses only APIs already present in immutable 1.6. Disposable emulator only. */
public final class Version17UpgradeBaseline extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  WordStore store=WordStore.get(getTargetContext());List<Word> n3=store.words("N3","HIGH_MID","","all",false,false,0),n5=store.words("N5","","all",false,false,2);long now=System.currentTimeMillis();
  store.favorite(n5.get(0),true);store.familiar(n5.get(1),true);store.seen(n5.get(0));store.rate(n3.get(0),0,now);store.rate(n5.get(0),0,now-2*ReviewPlan.DAY);store.rate(n5.get(0),0,now-ReviewPlan.DAY);
  List<String> ids=new ArrayList<>();for(Word w:n3)ids.add(w.id);StudyGroup group=new StudyGroup(ids,"","N3|HIGH_MID|all|false|SIZE|3",3);group.next();group.next();
  new Prefs(getTargetContext()).data.edit().putString("level","N3").putString("frequency","HIGH_MID").putString("studyMode","all").putString("groupMode","OFF").putInt("groupSize",3).putBoolean("hideFamiliar",false).putBoolean("shuffle",true).putBoolean("autoStart",false).putBoolean("autoSpeak",false).putBoolean("speakOnFlip",false).putString("voiceEngine",getTargetContext().getPackageName()+".test").putString("intervalSeconds","3600").putString("theme","DARK").putString("accent","BLUE").putString("group.state",group.save()).commit();
  out.putString("stream","PASS: seeded real 1.6 marks, encounters, exposures, reviews, daily ratings, weak evidence and inactive third group\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}

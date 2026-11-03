package com.myles.kotobafloat;

import android.content.Context;
import org.json.JSONObject;

/** 检查覆盖旧中英版时学习数据是否保留。 / Compatibility checks for replacing the archived bilingual build without losing study data. */
final class ChineseOnlyTests {
    static int run(Context target)throws Exception{
        Prefs prefs=new Prefs(target);WordStore store=WordStore.get(target);
        String old=prefs.data.getString("language",null),oldStatus=prefs.data.getString("backup.last",null);JSONObject saved=BackupSettings.export(prefs.data);
        try{
            prefs.data.edit().putString("language","EN").putString("level","ALL").putString("frequency","ALL").putString("groupMode","OFF").putString("studyMode","all").putBoolean("hideFamiliar",false).commit();
            if(StudySession.range(store,prefs).size()!=10641)throw new AssertionError("Legacy English preference must not filter Chinese words");
            Word word=store.words("N5","","all",false,false,1).get(0);
            if(!Ui.meaning(word,prefs).equals(word.meaning))throw new AssertionError("Chinese meanings remain selected with old English preferences");
            JSONObject legacy=new JSONObject().put("language","EN").put("currentId.en","old-word").put("queue.state.en","old-round").put("group.state.en","old-group");BackupSettings.validate(legacy);
            if(BackupSettings.export(prefs.data).has("language")||BackupSettings.export(prefs.data).has("queue.state.en"))throw new AssertionError("New Chinese backups must not expose the removed language mode");
            for(SpeechPlan.Segment part:SpeechPlan.bilingual("いち","一","一つください","请给我一个",1,1,true,true))if(!part.language.equals("ja")&&!part.language.equals("zh"))throw new AssertionError("Chinese audio includes an English segment");
            prefs.data.edit().putString("backup.last","Last saved file: 10-28 12:00").commit();
            if(!prefs.status("backup.last").equals("上次保存文件：10-28 12:00"))throw new AssertionError("Old English status text must render in Chinese with its timestamp retained");
            return 6;
        }finally{
            BackupSettings.restore(prefs.data,saved);
            if(oldStatus==null)prefs.data.edit().remove("backup.last").commit();else prefs.data.edit().putString("backup.last",oldStatus).commit();
            if(old==null)prefs.data.edit().remove("language").commit();else prefs.data.edit().putString("language",old).commit();
        }
    }
}

package com.myles.cijian;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.util.List;

public final class SmokeTests extends Instrumentation {
    private int checks;
    private void check(boolean condition,String message) { if(!condition)throw new AssertionError(message);checks++; }
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            Context target=getTargetContext();
            WordStore store=WordStore.get(target);
            check(store.stats()[3]==10641,"Full offline deck");
            check(store.scalar("SELECT count(*) FROM words WHERE level=?","N5")==807,"N5 count");
            check(store.scalar("SELECT count(*) FROM words WHERE level=?","N1")==4045,"N1 count");
            check(store.scalar("SELECT count(*) FROM words WHERE pos=''")==0,"Part of speech present");
            List<Word> n5=store.words("N5","","all",false,false,10);
            check(n5.size()==10 && n5.get(0).word.equals("一"),"Author order preserved");
            check(store.words("ALL","%","all",false,false,100).isEmpty(),"Literal search escapes percent");
            check(!store.words("ALL","たべる","all",false,false,100).isEmpty(),"Kana search");
            check(store.words("N3","HIGH","","all",false,false,0).size()==380,"N3 high frequency count");
            check(store.words("N2","MID","","all",false,false,0).size()==1301,"N2 medium frequency count");
            check(store.words("N1","LOW","","all",false,false,0).size()==2145,"N1 low frequency count");
            check(store.words("ALL","HIGH_MID","","all",false,false,0).size()==5135,"High plus medium filter");
            check(store.words("N3","MID_LOW","","all",false,false,0).size()==1437,"N3 medium plus low count");
            check(store.words("N2","MID_LOW","","all",false,false,0).size()==2366,"N2 medium plus low count");
            check(store.words("N1","MID_LOW","","all",false,false,0).size()==3447,"N1 medium plus low count");
            check(store.words("ALL","MID_LOW","","all",false,false,0).size()==7250,"All-level medium plus low excludes high and unclassified");
            check(store.words("ALL","MID_LOW","","all",false,false,0).stream().allMatch(x->x.frequency.equals("MID")||x.frequency.equals("LOW")),"Medium plus low never returns a high frequency card");
            check(store.words("ALL","NONE","","all",false,false,0).size()==1564,"Unclassified N4/N5 count");
            check(store.words("N5","HIGH","","all",false,false,0).size()==807,"N5 does not invent a frequency tier");
            check(store.words("N3","HIGH","","all",false,false,0).stream().allMatch(x->x.frequency.equals("HIGH")),"Source frequency tags decoded");
            check(Frequency.values("N5").length==1&&Frequency.values("N4").length==1,"N4 N5 expose only all frequencies");
            check(!java.util.Arrays.asList(Frequency.values("N3")).contains("NONE")&&!java.util.Arrays.asList(Frequency.values("N1")).contains("NONE"),"N3 N2 N1 exclude unclassified choice");
            check(java.util.Arrays.asList(Frequency.values("ALL")).contains("NONE"),"Mixed levels retain unclassified filter");
            check(Frequency.effective("N3","NONE").equals("ALL"),"Legacy unsupported unclassified filter falls back to all");
            check(Frequency.fromTags("eggrolls::N2高频").equals("HIGH"),"Original tag mapping");
            check(SpeechPlan.wordThenExample("ことば","例文",2,3,true).equals(java.util.Arrays.asList("ことば","ことば","例文","例文","例文")),"Word/example queue ordering and repeats");
            check(SpeechPlan.wordThenExample("ことば","",2,4,true).size()==2,"Missing example safely skipped");
            java.util.List<SpeechPlan.Segment> bilingual=SpeechPlan.bilingual("単語","词义","例文","例句译文",2,3,true,true);
            check(bilingual.size()==10&&bilingual.get(0).language.equals("ja")&&bilingual.get(1).language.equals("zh"),"Bilingual per-round repetition");
            check(bilingual.get(4).text.equals("例文")&&bilingual.get(5).text.equals("例句译文"),"Word translation before example translation");
            Context defaultsContext=new ContextWrapper(target){
                @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("smoke-defaults",mode);}
            };
            defaultsContext.getSharedPreferences("preferences",0).edit().clear().commit();
            check(!new Prefs(defaultsContext).autoStart(),"Auto-start is opt-in by default");
            Word w=n5.get(0);
            boolean favorite=w.favorite,familiar=w.familiar;
            store.favorite(w,true);
            check(store.words("N5","","favorites",false,false,0).stream().anyMatch(x->x.id.equals(w.id)),"Favorite saved");
            store.familiar(w,true);
            check(store.words("N5","","all",true,false,0).stream().noneMatch(x->x.id.equals(w.id)),"Familiar word excluded");
            store.seen(w);
            int today=store.stats()[0];store.seen(w);
            check(store.stats()[0]==today,"Daily unique encounters");
            String backup=store.backup();
            store.favorite(w,false);store.familiar(w,false);store.restore(backup);
            check(store.words("N5","","favorites",false,false,0).stream().noneMatch(x->x.id.equals(w.id)),"Older backup preserves a newer cancelled favorite");
            int before=store.stats()[2];
            try {store.restore("{\"format\":\"invalid\"}");throw new AssertionError("Invalid backup accepted");}catch(org.json.JSONException|IllegalArgumentException expected){}
            check(store.stats()[2]==before,"Invalid restore leaves data intact");
            check(Grammar.describe("自他動1").equals("自他动词／五段"),"Transitivity description");
            check(Grammar.describe("イ形").equals("い形容词"),"Adjective description");
            Prefs testPrefs=new Prefs(defaultsContext);
            testPrefs.put("favoritesOnly",true);check(testPrefs.studyMode().equals("favorites"),"Legacy favorite-only preference preserved");
            store.familiar(w,true); // Set up the following familiar-only query independently of restore semantics.
            testPrefs.put("studyMode","familiar");testPrefs.put("hideFamiliar",true);
            check(!testPrefs.effectiveHideFamiliar(),"Familiar review overrides skip familiar");
            testPrefs.put("level","N5");
            check(StudySession.load(store,testPrefs).words.stream().anyMatch(x->x.id.equals(w.id)&&x.familiar),"Familiar review includes marked word despite skip");
            check(StudySession.load(store,testPrefs).words.stream().allMatch(x->x.level.equals("N5")&&x.familiar),"Review respects current level");
            testPrefs.put("studyMode","all");testPrefs.put("hideFamiliar",false);testPrefs.put("groupMode","SIZE");testPrefs.put("groupSize",5);
            StudySession firstGroup=StudySession.load(store,testPrefs);
            check(firstGroup.words.size()==5&&firstGroup.group.page==0,"Fixed group activated");
            check(firstGroup.words.get(0).id.equals(StudySession.load(store,testPrefs).words.get(0).id),"Session group persists");
            StudySession.next(store,testPrefs);StudySession secondGroup=StudySession.load(store,testPrefs);
            check(secondGroup.group.page==1&&secondGroup.words.stream().noneMatch(x->firstGroup.words.stream().anyMatch(y->y.id.equals(x.id))),"Next group contains different cards");
            testPrefs.put("groupMode","CUSTOM");Word n1=store.words("N1","","all",false,false,1).get(0);
            testPrefs.put("customWords",w.id+"\n"+n1.id);StudySession custom=StudySession.load(store,testPrefs);
            check(custom.words.size()==2&&custom.words.get(1).level.equals("N1"),"Custom group spans levels and keeps selection order");
            testPrefs.put("studyMode","familiar");
            check(StudySession.load(store,testPrefs).words.stream().allMatch(x->x.familiar),"Custom group respects review source");
            testPrefs.put("studyMode","all");testPrefs.put("hideFamiliar",false);
            List<Word> entire=store.words("ALL","","all",false,false,0);java.util.List<String> allIds=new java.util.ArrayList<>();for(Word x:entire)allIds.add(x.id);
            testPrefs.put("customWords",String.join("\n",allIds));StudySession huge=StudySession.load(store,testPrefs);
            java.util.Map<String,String> identities=new java.util.LinkedHashMap<>();for(Word x:huge.words)identities.put(x.id,x.word+x.reading);
            check(!new ShuffleDeck(identities,"",huge.scope).save().isEmpty(),"Entire deck custom selection can persist random queue");
            check(KanaGuide.BASIC.length==11&&KanaGuide.VOICED.length==5&&KanaGuide.COMBINED.length==11,"Kana reference includes base voiced and combined rows");
            check(KanaGuide.katakana("きゃぱん").equals("キャパン"),"Hiragana katakana correspondence");
            org.json.JSONObject saved=new org.json.JSONObject(store.backup());store.restore(saved.toString());
            check(new org.json.JSONObject(store.backup()).getJSONArray("progress").toString().equals(saved.getJSONArray("progress").toString()),"Repeated restore never adds browse counts");
            check(saved.has("preferences")&&saved.has("reviews")&&saved.has("exposures"),"Full backup includes settings and review state");
            FirstPass pass=store.firstPass(java.util.Collections.singletonList(w),20);store.seen(w);
            check(pass.encountered==1&&store.firstPass(java.util.Collections.singletonList(w),20).encountered==1,"Repeated exposure never inflates first pass progress");
            long now=System.currentTimeMillis();store.rate(w,0,now);
            check(store.recall(java.util.Collections.singletonList(w),5,now).isEmpty(),"Future review does not appear early");
            check(store.recall(java.util.Collections.singletonList(w),5,now+60001).size()==1,"Forgotten word becomes due after one minute");
            org.json.JSONObject complete=new org.json.JSONObject(store.backup());
            check(complete.getString("format").equals(BackupFormat.CURRENT),"New backup format is versioned");
            org.json.JSONObject legacy=new org.json.JSONObject().put("format","kotoba-float-progress-v1").put("progress",complete.getJSONArray("progress")).put("encounters",complete.getJSONArray("encounters"));
            store.restore(legacy.toString());check(store.firstPass(java.util.Collections.singletonList(w),20).encountered==1,"Legacy backup still restores progress");
            android.content.SharedPreferences actual=new Prefs(target).data;
            org.json.JSONObject originalSettings=BackupSettings.export(actual);
            org.json.JSONObject settings=new org.json.JSONObject().put("dailyNewTarget",37).put("customWords",w.id).put("groupMode","CUSTOM").put("session.enabled",true);
            BackupSettings.restore(actual,settings);
            check(new Prefs(target).dailyNewTarget()==37&&new Prefs(target).customWords().contains(w.id),"Settings and custom group restore together");
            check(!BackupSettings.export(actual).has("session.enabled"),"Backup never starts a session by restoring runtime state");
            try{BackupSettings.validate(new org.json.JSONObject().put("width",-1));throw new AssertionError("Invalid settings accepted");}catch(org.json.JSONException expected){}
            check(new Prefs(target).dailyNewTarget()==37,"Invalid backup preferences leave existing settings intact");
            actual.edit().remove("dailyNewTarget").remove("customWords").remove("groupMode").apply();BackupSettings.restore(actual,originalSettings);
            store.favorite(w,favorite);store.familiar(w,familiar);
            org.json.JSONObject colors=new org.json.JSONObject().put("accent","CUSTOM").put("customColor","#2F80ED");BackupSettings.validate(colors);
            check(Accent.IDS.length==13&&java.util.Arrays.asList(Accent.IDS).contains("BLACK"),"Twelve preset themes include rainbow black and custom color");
            check(Accent.palette("CUSTOM","#FFFFFF",false)[0]!=0xffffffff,"White custom color adapts to keep white button text readable");
            check(Ui.themeResource(target)!=0,"Selected color resolves a native theme resource");
            checks+=MigrationTests.run(target);
            checks+=LearningProgressTests.run(target);
            checks+=ChineseOnlyTests.run(target);
            checks+=Feature16Tests.run(target);checks+=Feature17Tests.run(target);
            org.json.JSONObject badRestore=new org.json.JSONObject(store.backup());
            badRestore.getJSONArray("progress").put(new org.json.JSONObject().put("id","fixture-rollback").put("favorite",1).put("familiar",0).put("seen",0).put("last_seen",0));
            badRestore.getJSONArray("exposures").put(new org.json.JSONObject().put("id","fixture-rollback").put("first_day","invalid-date"));
            try{store.restore(badRestore.toString());throw new AssertionError("Bad date accepted");}catch(java.time.format.DateTimeParseException expected){}
            check(store.scalar("SELECT count(*) FROM progress WHERE id='fixture-rollback'")==0,"Malformed full backup rolls back partial records");
            StudyTasks tasks=store.tasks(java.util.Collections.singletonList(w),now);check(tasks.introduced==1&&tasks.todayNew<=1,"Formal new learning is tracked separately from exposure");
            store.rate(w,2,now+1000);check(store.scalar("SELECT stage FROM reviews WHERE id=?",w.id)==0,"Immediate retry cannot promote a forgotten card to a long interval");
            org.json.JSONObject extended=new org.json.JSONObject(store.backup());check(extended.has("review_started")&&extended.has("review_days"),"Full backup includes daily task history");
            runOnMainSync(()->{EditTextFixture.verify(target);});check(true,"Shaped dark input retains surface and muted hint color");
            java.io.File shared=BackupFiles.create(target,store.backup());android.content.Intent share=BackupFiles.share(target,shared);android.net.Uri uri=share.getParcelableExtra(Intent.EXTRA_STREAM);
            check(uri!=null&&uri.getScheme().equals("content")&&share.getClipData()!=null,"Backup sharing uses a grantable content URI and ClipData");
            check((share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"Backup sharing grants temporary read access");
            try(java.io.InputStream input=target.getContentResolver().openInputStream(uri)){check(input!=null&&input.read()=='{',"Generated shared backup is readable JSON");}
            try{target.getContentResolver().openFileDescriptor(uri,"w");throw new AssertionError("Backup provider permitted writes");}catch(java.io.FileNotFoundException expected){}check(true,"Shared backup provider rejects write access");
            try{target.getContentResolver().openFileDescriptor(android.net.Uri.parse("content://"+target.getPackageName()+".backups/..%2Fpreferences.xml"),"r");throw new AssertionError("Provider permitted traversal");}catch(java.io.FileNotFoundException expected){}check(true,"Shared backup cannot read outside export directory");
            check(share.getType().equals("application/octet-stream"),"Backup is offered as a general file for sharing apps");
            share.setClassName(target.getPackageName()+".test","com.myles.cijian.BackupCaptureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);target.startActivity(share);Thread.sleep(1200);
            Activity activity=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(activity!=null,"Main screen launches");
            waitForIdleSync();
            result.putString("stream","PASS: "+checks+" functional checks\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);
        }
    }
}

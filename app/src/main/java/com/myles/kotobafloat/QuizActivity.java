package com.myles.kotobafloat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** 识义练习与正式回忆证据隔离。 / Recognition practice never writes formal recall evidence. */
public final class QuizActivity extends Activity {
    private Prefs prefs;private Speech speech;private WordStore store;private LinearLayout root;
    private final ArrayList<String> ids=new ArrayList<>();private List<Word> pool;private int position,correct;private String chosen="";private boolean alive=true,foreground;
    private final Runnable relinquish=()->{if(speech!=null)speech.stop();finish();};
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(L.context(base));}
    @Override public void onCreate(Bundle state){
        setTheme(Ui.themeResource(this));Ui.applyTheme(this);super.onCreate(state);FloatService.stopForRestore();StudySurface.claim(relinquish);prefs=new Prefs(this);speech=new Speech(this);speech.setOnUnavailable(()->Toast.makeText(this,L.t("请在设置 → 发音检测日语声音。"),Toast.LENGTH_LONG).show());
        if(state!=null){ids.addAll(state.getStringArrayList("ids"));position=state.getInt("position");correct=state.getInt("correct");chosen=state.getString("chosen","");}
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);root.requestApplyInsets();setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        root.addView(Ui.text(this,L.t("正在准备四选一练习…"),16,Ui.GREEN));Async.IO.execute(()->{try{store=WordStore.get(this);List<Word> range=StudySession.range(store,prefs);pool=store.words("ALL","ALL","","all",false,false,0);if(ids.isEmpty()){Collections.shuffle(range);for(int i=0;i<Math.min(prefs.reviewBatch(),range.size());i++)ids.add(range.get(i).id);}Async.MAIN.post(()->{if(alive)show();});}catch(Exception e){Async.MAIN.post(()->{if(alive){root.removeAllViews();root.addView(Ui.text(this,L.t("练习加载失败，请返回重试。"),16,Ui.INK));}});}});
    }
    private QuizOptions.Entry entry(Word w){return new QuizOptions.Entry(w.id,w.word,w.reading,Ui.meaning(w,prefs));}
    private void show(){
        if(!alive)return;speech.stop();root.removeAllViews();root.addView(Ui.button(this,L.t("‹ 返回"),false,v->finish()));ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.column(this);Ui.pad(body,24);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(Ui.text(this,L.t("四选一识义 · 不改正式复习计划"),12,Ui.GREEN));Ui.gap(body,18);
        if(position>=ids.size()){body.addView(Ui.heading(this,L.t("本轮答对 ")+correct+" / "+ids.size(),26,Ui.INK));Ui.gap(body,16);body.addView(Ui.prose(this,L.t("这是识别释义的练习结果，不等于主动回忆或永久掌握。"),15,Ui.MUTED));return;}
        Word w=null;for(Word item:pool)if(item.id.equals(ids.get(position))){w=item;break;}if(w==null){position++;show();return;}final Word word=w;
        List<QuizOptions.Entry> candidates=new ArrayList<>();for(Word item:pool)if(item.level.equals(w.level))candidates.add(entry(item));List<QuizOptions.Entry> options=QuizOptions.make(entry(w),candidates);
        body.addView(Ui.text(this,L.t("第 ")+(position+1)+" / "+ids.size()+L.t(" 题"),12,Ui.MUTED));Ui.gap(body,16);body.addView(Ui.heading(this,w.word,32,Ui.INK));Ui.gap(body,12);body.addView(Ui.prose(this,Ui.reading(w)+" · "+L.grammar(Grammar.describe(w.pos)),15,Ui.GREEN));Ui.gap(body,20);
        if(options.isEmpty()){body.addView(Ui.prose(this,L.t("没有足够可靠且不同的四个选项，本词不强行出题。"),15,Ui.MUTED));Ui.gap(body,12);body.addView(Ui.button(this,L.t("改用回忆练习"),true,v->{startActivity(new Intent(this,ReviewActivity.class).putExtra("mode","PRACTICE"));finish();}));body.addView(Ui.button(this,L.t("跳过本题"),false,v->{position++;chosen="";show();}));return;}
        for(QuizOptions.Entry option:options){boolean picked=option.id.equals(chosen);TextView button=Ui.button(this,option.meaning,!chosen.isEmpty()&&option.id.equals(word.id),v->{if(!chosen.isEmpty())return;chosen=option.id;if(chosen.equals(word.id))correct++;show();});button.setGravity(android.view.Gravity.CENTER_VERTICAL);button.setEnabled(chosen.isEmpty());body.addView(button);Ui.gap(body,10);if(picked)button.setContentDescription(L.t("本题所选：")+option.meaning);}
        if(!chosen.isEmpty()){body.addView(Ui.prose(this,(chosen.equals(word.id)?L.t("答对了"):L.t("本题正确释义：")+Ui.meaning(word,prefs))+"\n"+word.example+"\n"+word.exampleMeaning,16,Ui.INK));Ui.gap(body,12);body.addView(Ui.button(this,L.t("下一题"),true,v->{position++;chosen="";show();}));}
        Ui.gap(body,12);body.addView(Ui.button(this,L.t("朗读"),false,v->speech.speak(word.spokenForm(),true)));Ui.gap(body,10);body.addView(Ui.prose(this,L.t("选项来自原词库；近义词仍可能有交集，可改用自评回忆。答对不会改变正式到期时间或熟悉标记。"),12,Ui.MUTED));
        if(chosen.isEmpty()&&foreground){Async.IO.execute(()->store.seen(word));if(foreground&&prefs.reviewAutoSpeak())speech.speak(word.spokenForm(),true);}
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putStringArrayList("ids",ids);state.putInt("position",position);state.putInt("correct",correct);state.putString("chosen",chosen);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;speech.stop();super.onPause();}
    @Override protected void onDestroy(){alive=false;speech.close();StudySurface.leave(relinquish);super.onDestroy();}
}

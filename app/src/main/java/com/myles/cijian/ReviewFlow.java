package com.myles.cijian;

import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.*;

/** 同一持久化回忆流程供两个界面使用。 / Both surfaces share one durable recall workflow. */
final class ReviewFlow {
    interface Host {void exit();void transfer();boolean prepareAudio();boolean visible();boolean overlay();}
    private final Context context;private final Prefs prefs;private final LinearLayout root;private final Host host;private final String mode;
    private final Speech speech;private WordStore store;private ReviewDraft draft;private Word current;private boolean alive=true,busy,visible=true;
    private String shown="",spoken="";private TextView voice;
    ReviewFlow(Context context,LinearLayout root,String mode,Host host,boolean resume){
        this.context=context;this.root=root;this.mode=Arrays.asList("TASK","DUE","LIGHT","WEAK","PRACTICE").contains(mode)?mode:"TASK";this.host=host;prefs=new Prefs(context);
        speech=new Speech(context);speech.setOnUnavailable(()->toast(L.t("没有可用日语声音，请到设置 → 发音检测；仍可完成回忆。")));
        speech.setOnChanged(()->{if(alive&&voice!=null)voice.setText(speech.status());});
        speech.setOnInterrupted(reason->toast(reason));
        loading(L.t("正在准备复习…"));busy=true;
        Async.IO.execute(()->{try{store=WordStore.get(context);ReviewDraft saved=store.draft(this.mode);Async.MAIN.post(()->{
            if(!alive)return;busy=false;
            if(saved!=null&&saved.compatible(scope(),this.mode,prefs.reverseRecall())&&saved.session.current()!=null){
                if(resume){draft=saved;card();}else offer(saved);
            }else load();
        });}catch(Exception e){Async.MAIN.post(()->error(L.t("复习加载失败，请返回重试。")));}});
    }
    void editFonts(android.app.Activity activity){if(!canLeave())return;speech.stop();FontPicker.showApp(activity,prefs,()->{if(alive&&draft!=null&&!busy)card();});}
    private float size(float value,int role){return host.overlay()?value:prefs.appSize(value,role);}
    private String scope(){return (prefs.groupMode().equals("CUSTOM")?"CUSTOM|"+String.join("|",prefs.customWords()):prefs.level()+"|"+Frequency.effective(prefs.level(),prefs.frequency()))+"|"+prefs.studyMode();}
    private int dp(float n){return Ui.dp(context,n);}
    private void toast(String message){if(alive)Toast.makeText(context,message,Toast.LENGTH_LONG).show();}
    boolean canLeave(){if(busy){toast(L.t("正在保存这一步，请稍候。"));return false;}return true;}
    void setVisible(boolean value){visible=value;if(!value){ReviewGuide.close(context);speech.stop();spoken="";}else if(current!=null&&!current.id.equals(shown)&&host.visible()){shown=current.id;Word displayed=current;Async.IO.execute(()->store.seen(displayed));}}
    void checkSettings(){if(alive&&!busy&&draft!=null&&!draft.compatible(scope(),mode,prefs.reverseRecall())){speech.stop();toast(L.t("学习范围或回忆方向已变化，重新安排这一轮。"));load();}}
    String snapshot(){return draft==null?"{}":draft.json();}
    void close(){alive=false;ReviewGuide.close(context);speech.close();}
    private boolean audible(){return alive&&visible&&host.visible()&&!ReviewGuide.showing(context);}
    private void frame(String title){
        root.removeAllViews();LinearLayout top=Ui.row(context);top.setPadding(dp(8),dp(6),dp(8),dp(6));
        TextView heading=Ui.text(context,title+"  ⓘ",13,Ui.GREEN);heading.setMinHeight(dp(44));heading.setGravity(android.view.Gravity.CENTER_VERTICAL);heading.setContentDescription(L.t("查看复习规则与评分说明"));heading.setOnClickListener(v->{if(!alive||!visible||!host.visible())return;speech.stop();ReviewGuide.show(context);});Ui.weighted(top,heading);
        top.addView(Ui.button(context,host.overlay()?L.t("App 内"):L.t("悬浮窗"),false,v->{if(canLeave()){speech.stop();host.transfer();}}));
        root.addView(top);Ui.gap(root,6);
    }
    private LinearLayout content(){ScrollView scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(dp(host.overlay()?4:16),dp(4),dp(host.overlay()?4:16),dp(12));LinearLayout body=Ui.column(context);body.setPadding(dp(16),dp(18),dp(16),dp(18));body.setBackground(Ui.shape(Ui.SURFACE,18,context));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return body;}
    private void loading(String message){frame(L.t("认真记忆"));content().addView(Ui.prose(context,message,15,Ui.MUTED));}
    private void offer(ReviewDraft saved){
        frame(L.t("继续上次复习"));LinearLayout body=content();body.addView(Ui.heading(context,L.t("有未完成的一轮"),23,Ui.INK));Ui.gap(body,14);
        body.addView(Ui.prose(context,L.t("第 ")+(saved.session.position+1)+" / "+saved.session.queue.size()+L.t(" 词 · 已评分内容已保存。"),14,Ui.MUTED));Ui.gap(body,20);
        body.addView(Ui.button(context,L.t("继续上次复习"),true,v->{draft=saved;card();}));Ui.gap(body,10);body.addView(Ui.button(context,L.t("放弃本轮，重新安排"),false,v->load()));
    }
    private void load(){
        if(!alive)return;busy=true;loading(L.t("按当前学习范围安排，到期词优先…"));
        Async.IO.execute(()->{try{
            List<Word> range=StudySession.range(store,prefs);StudyTasks tasks=store.tasks(range,System.currentTimeMillis());
            List<String> ids=mode.equals("WEAK")?store.weak(range,5):mode.equals("PRACTICE")?store.practice(range,prefs.reviewBatch()):tasks.plan(prefs.focusNewTarget(),mode.equals("LIGHT")?10:prefs.reviewBatch(),mode.equals("DUE")||mode.equals("LIGHT"));
            ReviewDraft next=new ReviewDraft(scope(),mode,prefs.reverseRecall(),ids);store.startDraft(next);
            Async.MAIN.post(()->{if(alive){draft=next;busy=false;card();}});
        }catch(Exception e){Async.MAIN.post(()->error(L.t("任务安排失败，请返回重试。")));}});
    }
    private void change(ReviewDraft next,Word word,int rating){
        if(!alive||busy)return;if(!draft.compatible(scope(),mode,prefs.reverseRecall())){checkSettings();return;}busy=true;speech.stop();ReviewDraft before=draft;
        Async.IO.execute(()->{try{ReviewDraft saved=store.saveDraft(before,next,word,rating);Async.MAIN.post(()->{
            if(!alive)return;busy=false;if(saved==null||!saved.token.equals(before.token)){error(L.t("复习批次已变化，请返回后重新进入。"));return;}draft=saved;if(!draft.compatible(scope(),mode,prefs.reverseRecall()))load();else card();
        });}catch(Exception e){Async.MAIN.post(()->error(L.t("保存失败，请返回重试；已保存评分保留。")));}});
    }
    private void card(){
        if(!alive)return;speech.stop();String id=draft.session.current();if(id==null){complete();return;}
        busy=true;Async.IO.execute(()->{try{Word word=store.byId(id);Async.MAIN.post(()->{
            if(!alive)return;busy=false;if(word==null){ReviewDraft next=draft.copy();next.session.position++;change(next,null,-1);return;}
            current=word;show();if(!id.equals(shown)&&host.visible()){shown=id;Async.IO.execute(()->store.seen(word));}
        });}catch(Exception e){Async.MAIN.post(()->error(L.t("词条读取失败，请返回重试。")));}});
    }
    private void show(){
        Word w=current;boolean revealed=draft.revealed;String title=mode.equals("WEAK")?L.t("易忘词练习"):mode.equals("PRACTICE")?L.t("自由练习"):mode.equals("LIGHT")?L.t("先复习一小批"):mode.equals("DUE")?L.t("只复习到期词"):L.t("每日记忆任务");
        frame(title);LinearLayout body=content();body.addView(Ui.text(context,L.t("本轮 ")+(draft.session.position+1)+" / "+draft.session.queue.size()+" · "+(prefs.reverseRecall()?L.t("看义想词"):L.t("看词想义")),12,Ui.MUTED));Ui.gap(body,18);
        body.addView(Ui.heading(context,prefs.reverseRecall()&&!revealed?Ui.meaning(w,prefs):w.word,prefs.reverseRecall()&&!revealed?size(host.overlay()?24:32,2):size(host.overlay()?24:32,0),Ui.INK));Ui.gap(body,12);
        if(!prefs.reverseRecall()||revealed)body.addView(Ui.prose(context,Ui.reading(w)+"  "+w.pitch+" · "+L.grammar(Grammar.describe(w.pos)),size(14,1),Ui.GREEN));
        Ui.gap(body,18);
        if(revealed){body.addView(Ui.prose(context,Ui.meaning(w,prefs),size(host.overlay()?17:20,2),Ui.INK));
            if(!w.example.isEmpty()){Ui.gap(body,18);body.addView(Ui.prose(context,w.example,size(16,2),Ui.INK));Ui.gap(body,6);body.addView(Ui.prose(context,w.exampleMeaning,size(14,1),Ui.MUTED));}
        }else body.addView(Ui.prose(context,prefs.reverseRecall()?L.t("先想出日语单词和读音，再揭晓答案。"):L.t("先回忆中文含义，再揭晓并评价这次回忆。"),14,Ui.MUTED));
        Ui.gap(body,18);if(!prefs.reverseRecall()||revealed)body.addView(Ui.button(context,L.t("朗读"),false,v->read(true)));
        Ui.gap(body,10);voice=Ui.prose(context,speech.status(),11,Ui.MUTED);body.addView(voice);
        Ui.gap(body,12);body.addView(Ui.button(context,L.t("暂时跳过"),false,v->{if(busy)return;ReviewDraft next=draft.copy();next.session.position++;next.revealed=false;change(next,null,-1);}));
        Ui.gap(body,12);body.addView(Ui.prose(context,mode.equals("WEAK")||mode.equals("PRACTICE")?L.t("练习不改变正式复习时间。"):L.t("自评会调整正式复习时间；收球或退出不会替你评分。"),11,Ui.MUTED));
        LinearLayout bottom=Ui.column(context);bottom.setPadding(dp(12),dp(8),dp(12),dp(10));bottom.setBackgroundColor(Ui.SURFACE);
        if(!revealed)bottom.addView(Ui.button(context,L.t("揭晓答案"),true,v->{if(busy)return;ReviewDraft next=draft.copy();next.revealed=true;change(next,null,-1);}));
        else{LinearLayout ratings=Ui.row(context);String[] labels={L.t("没想起"),L.t("模糊"),L.t("记得")};for(int i=0;i<3;i++){final int rating=i;TextView b=Ui.button(context,labels[i],i==2,v->answer(rating));b.setPadding(dp(4),dp(10),dp(4),dp(10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),0,dp(2),0);ratings.addView(b,p);}bottom.addView(ratings);}
        root.addView(bottom);Ui.tint(root);
        String key=draft.token+":"+draft.session.position+":"+revealed;
        if(prefs.reviewAutoSpeak()&&(!prefs.reverseRecall()||revealed)&&!key.equals(spoken)&&audible()){spoken=key;read(false);}
    }
    private void read(boolean explicit){if(!audible()||current==null||!host.prepareAudio())return;if(draft.revealed)speech.speakWord(current,true);else speech.speak(current.spokenForm(),true);}
    private void answer(int rating){
        if(!alive||busy||!draft.revealed)return;ReviewDraft next=draft.copy();if(rating==2)next.remembered++;else next.missed++;
        next.session.answer(rating,!mode.equals("LIGHT")&&!mode.equals("WEAK"));next.revealed=false;change(next,current,rating);
    }
    private void complete(){
        busy=false;frame(L.t("本轮结束"));LinearLayout body=content();body.addView(Ui.heading(context,draft.session.queue.isEmpty()?L.t("当前暂无可安排的词"):L.t("这一轮完成了"),24,Ui.INK));Ui.gap(body,16);
        body.addView(Ui.prose(context,L.t("记得 ")+draft.remembered+L.t(" 次 · 需要再看 ")+draft.missed+L.t(" 次\n跳过 ")+Math.max(0,draft.session.position-draft.remembered-draft.missed)+L.t(" 次\n\n")+L.t(mode.equals("PRACTICE")?"本轮练习已保存；正式复习时间未改变。":mode.equals("WEAK")?"易忘词表现已保存；正式复习时间未改变。":"已提交的评分已保存；剩余任务保留，跳过不算完成。"),15,Ui.MUTED));Ui.gap(body,18);
        TextView summary=Ui.prose(context,"",13,Ui.GREEN);body.addView(summary);Ui.gap(body,12);Async.IO.execute(()->{try{StudyTasks tasks=store.tasks(StudySession.range(store,prefs),System.currentTimeMillis());Async.MAIN.post(()->{if(alive)summary.setText(context.getString(R.string.task_summary,tasks.due,tasks.todayNew,prefs.focusNewTarget(),tasks.learningSoon));});}catch(Exception ignored){}});
        body.addView(Ui.button(context,mode.equals("LIGHT")?L.t("再复习一批（最多 10 个）"):L.t("继续下一轮"),true,v->load()));Ui.gap(body,10);body.addView(Ui.button(context,L.t("返回复习页"),false,v->host.exit()));
    }
    private void error(String message){if(!alive)return;busy=false;frame(L.t("复习"));content().addView(Ui.prose(context,message,15,Ui.INK));}
}

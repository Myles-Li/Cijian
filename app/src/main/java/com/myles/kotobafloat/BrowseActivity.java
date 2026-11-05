package com.myles.kotobafloat;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** 连续浏览共享范围和队列，标记时保留已绘制内容。 / Browsing shares scope and queue; marking retains the rendered content. */
public final class BrowseActivity extends Activity {
    private Prefs prefs;private WordStore store;private Speech speech;private LinearLayout root;private TextView status,timer,exampleAction;private ProgressBar progress;
    private List<Word> words=new ArrayList<>();private final Map<String,Integer> positions=new HashMap<>();private ShuffleDeck deck;private StudySession session;
    private String base="",pending="";private int index;private volatile int request;private boolean alive=true,foreground,touchingCard,paused=true,revealed,busy,pendingOverlay,loadPending,audioExpanded;private long due;
    private final Handler handler=new Handler(Looper.getMainLooper());private final MarkHistory<Mark> history=new MarkHistory<>();
    private static final class Mark {Word word;boolean familiar,old;String base,scope,queue;boolean shuffled;}
    private final Runnable relinquish=()->{paused=true;if(speech!=null)speech.stop();finish();};
    private final Runnable tick=new Runnable(){public void run(){if(!alive)return;updateClock();if(canCycle()&&SystemClock.elapsedRealtime()>=due)flip(1,false);handler.postDelayed(this,200);}};
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(L.context(base));}
    @Override public void onCreate(Bundle state){
        setTheme(Ui.themeResource(this));Ui.applyTheme(this);super.onCreate(state);FloatService.stopForRestore();StudySurface.claim(relinquish);prefs=new Prefs(this);speech=new Speech(this);
        speech.setOnUnavailable(()->toast(L.t("没有可用日语声音，请到设置 → 发音检测。")));
        speech.setOnInterrupted(reason->{paused=true;pending="";toast(reason);show();});
        speech.setOnChanged(()->{if(status!=null)status.setText(speech.status());Ui.exampleActionState(exampleAction,speech.isSpeakingExample());if(foreground&&!paused&&!pending.isEmpty()&&speech.isReady()&&current()!=null&&pending.equals(current().id)){pending="";speech.speakWord(current(),true);}updateClock();});
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);root.requestApplyInsets();setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        load(null,false);handler.post(tick);
    }
    private void toast(String text){if(alive)Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private Word current(){return words.isEmpty()?null:words.get(Math.min(index,words.size()-1));}
    private boolean canCycle(){return foreground&&!paused&&!touchingCard&&!busy&&!words.isEmpty()&&!speech.isSpeakingSequence()&&!(prefs.autoSpeak()&&speech.isInitializing());}
    private void updateClock(){if(timer==null)return;String label=busy?L.t("正在保存…"):speech.isSpeakingSequence()?L.t("朗读中"):paused?L.t("已暂停"):L.t("下个词 · ")+Interval.format(Math.max(0,due-SystemClock.elapsedRealtime()))+L.t(" 秒");timer.setText(label);if(progress!=null)progress.setProgress(paused||busy||speech.isSpeakingSequence()?0:(int)Math.max(0,Math.min(1000,1000*(prefs.intervalMillis()-Math.max(0,due-SystemClock.elapsedRealtime()))/prefs.intervalMillis())));}
    private void load(String preferred,boolean changedByMark){
        int mine=++request;busy=true;loadPending=true;speech.stop();pending="";String old=current()==null?"":current().id;
        if(session==null){root.removeAllViews();root.addView(Ui.prose(this,L.t("正在准备连续浏览…"),16,Ui.GREEN));}
        handler.postDelayed(()->{if(alive&&loadPending&&mine==request){request++;busy=false;loadPending=false;paused=true;showError(L.t("加载时间较长，请重试；学习记录保留。"));}},12000);
        Async.IO.execute(()->{try{if(!alive||mine!=request)return;store=WordStore.get(this);StudySession loaded=StudySession.load(store,prefs);String b=StudySession.baseScope(prefs);Map<String,String> candidates=new LinkedHashMap<>();for(Word w:loaded.words)candidates.put(w.id,w.word+"\u001f"+w.reading);
            ShuffleDeck saved=prefs.shuffle()?new ShuffleDeck(candidates,ScopeMemory.position(prefs,b,loaded.scope,true,"queue"),loaded.scope):null;
            String selected=preferred!=null&&candidates.containsKey(preferred)?preferred:saved==null?ScopeMemory.position(prefs,b,loaded.scope,false,"id"):saved.current();
            handler.post(()->{if(!alive||mine!=request)return;loadPending=false;session=loaded;base=b;words=loaded.words;deck=saved;positions.clear();index=0;for(int i=0;i<words.size();i++)positions.put(words.get(i).id,i);if(positions.containsKey(selected))index=positions.get(selected);busy=false;revealed=!prefs.conceal();reset();show();encounter(false);if(changedByMark&&foreground&&current()!=null&&!current().id.equals(old)&&prefs.speakOnFlip())speech.speakWord(current(),true);});
        }catch(Exception e){handler.post(()->{if(alive&&mine==request){busy=false;loadPending=false;paused=true;showError(L.t("加载失败，请重试；现有记录保留。"));}});}});
    }
    private void showError(String message){root.removeAllViews();Ui.pad(root,20);root.addView(Ui.prose(this,message,16,Ui.INK));root.addView(Ui.button(this,L.t("重试加载"),true,v->load(null,false)));root.addView(Ui.button(this,L.t("‹ 返回"),false,v->finish()));}
    private void reset(){due=SystemClock.elapsedRealtime()+prefs.intervalMillis();}
    private void encounter(boolean automatic){Word w=current();if(w==null)return;ScopeMemory.save(prefs,base,session.scope,deck!=null,w.id,deck==null?"":deck.save());if(foreground)Async.IO.execute(()->store.seen(w));if(automatic&&prefs.autoSpeak()&&!paused){if(speech.isReady())speech.speakWord(w,true);else if(speech.isInitializing())pending=w.id;}}
    private void flip(int amount,boolean manual){
        if(busy||words.isEmpty())return;speech.stop();pending="";if(deck!=null){String next=amount<0?deck.previous():deck.next();if(positions.containsKey(next))index=positions.get(next);}else index=(index+amount+words.size())%words.size();revealed=!prefs.conceal();reset();show();encounter(!manual);if(manual&&foreground&&prefs.speakOnFlip())speech.speakWord(current(),true);
    }
    private void mark(boolean familiar){
        if(busy||current()==null)return;Word w=current();boolean old=familiar?w.familiar:w.favorite;Mark item=new Mark();item.word=w;item.familiar=familiar;item.old=old;item.base=base;item.scope=session.scope;item.shuffled=deck!=null;item.queue=deck==null?"":deck.save();busy=true;
        Async.IO.execute(()->{try{if(familiar)store.familiar(w,!old);else store.favorite(w,!old);handler.post(()->{if(!alive)return;busy=false;history.push(item,SystemClock.elapsedRealtime());scheduleHistory();
            // 不改变当前词的收藏／熟悉操作不打断语音和倒计时。 / Marks that retain the current word never restart audio or the timer.
            boolean removes=familiar?(!old&&prefs.effectiveHideFamiliar()||old&&prefs.studyMode().equals("familiar")):old&&prefs.studyMode().equals("favorites");
            if(removes){String next=words.size()>1?(deck==null?words.get((index+1)%words.size()).id:deck.next()):null;if(deck!=null&&next!=null)ScopeMemory.save(prefs,base,session.scope,true,next,deck.save());load(next,true);}else if(familiar&&!old)flip(1,true);else show();
        });}catch(Exception e){handler.post(()->{busy=false;toast(L.t("标记保存失败，请重试；原记录保留。"));});}});
    }
    private void scheduleHistory(){handler.postDelayed(()->{if(alive&&!busy){if(history.peek(SystemClock.elapsedRealtime())==null)show();}},8100);}
    private void undo(){if(busy)return;Mark item=history.pop(SystemClock.elapsedRealtime());if(item==null){show();return;}busy=true;speech.stop();pending="";
        Async.IO.execute(()->{try{if(item.familiar)store.familiar(item.word,item.old);else store.favorite(item.word,item.old);handler.post(()->{if(!alive)return;busy=false;boolean same=base.equals(item.base)&&session.scope.equals(item.scope)&&item.shuffled==(deck!=null);if(same)ScopeMemory.save(prefs,base,session.scope,item.shuffled,item.word.id,item.queue);scheduleHistory();load(same?item.word.id:null,false);});}catch(Exception e){handler.post(()->{busy=false;history.push(item,SystemClock.elapsedRealtime());toast(L.t("撤销失败，请重试。"));show();});}});
    }
    private void show(){
        if(!alive||session==null)return;root.removeAllViews();LinearLayout top=Ui.row(this);Ui.pad(top,12);Ui.weighted(top,Ui.button(this,L.t("‹ 返回"),false,v->finish()));Ui.weighted(top,Ui.button(this,L.t("转到悬浮窗"),false,v->toOverlay()));TextView font=Ui.button(this,"Aa",false,v->{paused=true;pending="";speech.stop();reset();show();FontPicker.showApp(this,prefs,this::show);});font.setContentDescription(L.t("App 内学习字号"));top.addView(font);root.addView(top);
        LinearLayout clock=Ui.row(this);clock.setPadding(Ui.dp(this,24),Ui.dp(this,4),Ui.dp(this,24),Ui.dp(this,8));timer=Ui.bold(this,"",13,Ui.GREEN);Ui.weighted(clock,timer);if(history.peek(SystemClock.elapsedRealtime())!=null)clock.addView(Ui.button(this,L.t("撤销上次标记"),false,v->undo()));root.addView(clock);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);root.addView(progress,new LinearLayout.LayoutParams(-1,Ui.dp(this,3)));
        ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.column(this);Ui.pad(body,20);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(current()==null){body.addView(Ui.heading(this,L.t("当前范围没有可浏览的词"),23,Ui.INK));body.addView(Ui.prose(this,L.t("可返回调整收藏、熟悉、等级或分组筛选。"),15,Ui.MUTED));updateClock();return;}
        Word w=current();body.addView(Ui.text(this,L.t("连续浏览 · ")+L.group(session.label)+" · "+(index+1)+" / "+words.size(),12,Ui.MUTED));Ui.gap(body,14);
        body.addView(Ui.text(this,L.t("左右滑动翻词 · 上下滑动查看内容"),11,Ui.MUTED));Ui.gap(body,8);
        LinearLayout card=new SwipeCardLayout(this,new SwipeCardLayout.Listener(){public void touching(boolean value){touchingCard=value;}public void flip(int direction){if(alive&&foreground&&!busy)BrowseActivity.this.flip(direction,true);}});Ui.pad(card,22);card.setBackground(Ui.shape(Ui.SURFACE,22,this));body.addView(card);card.addView(Ui.heading(this,w.word,prefs.appSize(34,0),Ui.INK));Ui.gap(card,12);TextView reading=Ui.prose(this,Ui.reading(w)+" "+w.pitch+" · "+L.grammar(Grammar.describe(w.pos)),prefs.appSize(15,1),Ui.GREEN);reading.setContentDescription(L.t("查看这个词的音调说明"));reading.setMinHeight(Ui.dp(this,44));reading.setOnClickListener(v->PitchGuide.show(this,w));card.addView(reading);Ui.gap(card,22);
        TextView meaning=Ui.prose(this,revealed?Ui.meaning(w,prefs):L.t("点击揭晓释义"),prefs.appSize(21,2),Ui.INK);meaning.setMinHeight(Ui.dp(this,48));meaning.setOnClickListener(v->{revealed=!revealed;show();});card.addView(meaning);
        if(revealed&&!w.example.isEmpty()){Ui.gap(card,22);card.addView(Ui.prose(this,w.example,prefs.appSize(18,2),Ui.INK));Ui.gap(card,8);card.addView(Ui.prose(this,w.exampleMeaning,prefs.appSize(15,1),Ui.MUTED));Ui.gap(card,12);exampleAction=Ui.exampleAction(this,speech::isSpeakingExample,v->{if(!foreground)return;pending="";if(speech.isSpeakingExample())speech.stop();else speech.speakExample(w,true);});Ui.exampleActionState(exampleAction,speech.isSpeakingExample());card.addView(exampleAction);}
        Ui.gap(body,16);LinearLayout marks=Ui.row(this);Ui.weighted(marks,Ui.button(this,w.favorite?L.t("已收藏"):L.t("收藏"),false,v->mark(false)));Ui.weighted(marks,Ui.button(this,w.familiar?L.t("移回学习"):L.t("标为熟悉"),false,v->mark(true)));body.addView(marks);
        if(prefs.groupMode().equals("SIZE")){Ui.gap(body,12);body.addView(Ui.button(this,L.t("换下一组"),false,v->{if(busy)return;busy=true;speech.stop();Async.IO.execute(()->{try{StudySession.next(store,prefs);handler.post(()->{busy=false;load(null,false);});}catch(Exception e){handler.post(()->{busy=false;toast(L.t("换组失败，请重试。"));});}});}));}
        Ui.gap(body,16);LinearLayout audioGroup=Ui.column(this);audioGroup.setBackground(Ui.shape(Ui.SURFACE,14,this));Ui.pad(audioGroup,10);TextView audio=Ui.button(this,L.t("语音状态与说明")+(audioExpanded?"  ⌄":"  ›"),false,null);audio.setBackground(Ui.shape(Ui.SURFACE,10,this));audio.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);audio.setTextSize(12);status=Ui.prose(this,speech.status(),11,Ui.MUTED);status.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));status.setVisibility(audioExpanded?View.VISIBLE:View.GONE);audio.setOnClickListener(v->{audioExpanded=!audioExpanded;status.setVisibility(audioExpanded?View.VISIBLE:View.GONE);audio.setText(getString(R.string.fold_title,L.t("语音状态与说明"),audioExpanded?"⌄":"›"));});audioGroup.addView(audio);audioGroup.addView(status);body.addView(audioGroup);
        LinearLayout controls=Ui.row(this);Ui.pad(controls,12);Ui.weighted(controls,Ui.button(this,L.t("‹ 上一个"),false,v->flip(-1,true)));Ui.weighted(controls,Ui.button(this,paused?L.t("播放"):L.t("暂停"),true,v->{if(busy)return;paused=!paused;pending="";speech.stop();reset();show();if(!paused)encounter(true);}));Ui.weighted(controls,Ui.button(this,L.t("朗读"),false,v->speech.speakWord(current(),true)));Ui.weighted(controls,Ui.button(this,L.t("下一个 ›"),false,v->flip(1,true)));root.addView(controls);Ui.tint(root);updateClock();
    }
    private void toOverlay(){if(busy)return;if(!Settings.canDrawOverlays(this)){pendingOverlay=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){pendingOverlay=false;toast(L.t("请在系统设置中允许悬浮显示。"));}return;}speech.stop();pending="";paused=true;prefs.put("session.focused",false);prefs.put("session.paused",true);prefs.put("session.collapsed",false);try{startForegroundService(new Intent(this,FloatService.class));finish();}catch(RuntimeException e){toast(L.t("未能开启悬浮窗，浏览位置已保存。"));}}
    @Override protected void onResume(){super.onResume();foreground=true;if(session!=null&&!base.equals(StudySession.baseScope(prefs)))load(null,false);else if(current()!=null&&!busy){show();encounter(false);}if(pendingOverlay){pendingOverlay=false;if(Settings.canDrawOverlays(this))toOverlay();}}
    @Override protected void onPause(){foreground=false;paused=true;pending="";speech.stop();reset();super.onPause();}
    @Override protected void onDestroy(){alive=false;request++;handler.removeCallbacksAndMessages(null);speech.close();StudySurface.leave(relinquish);super.onDestroy();}
}

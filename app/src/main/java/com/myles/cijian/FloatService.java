package com.myles.cijian;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

public final class FloatService extends Service implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String STOP="com.myles.cijian.STOP", TOGGLE="com.myles.cijian.TOGGLE";
    static volatile boolean running;
    static final String STATE_CHANGED="com.myles.cijian.STATE_CHANGED";
    private boolean focused,focusQuiet,stopping;private String reviewMode="TASK";private ReviewFlow reviewFlow;private LinearLayout reviewPanel;
    private final Runnable relinquish=()->stopByUser();
    private void publishState(){StudySurface.changed();}
    private static java.lang.ref.WeakReference<FloatService> active=new java.lang.ref.WeakReference<>(null);
    static String stateDescription(){
        FloatService s=active.get();if(!running||s==null)return L.t("悬浮窗未运行");
        if(s.focused)return L.t("悬浮认真记忆 · 先揭晓，再评分");
        if(s.loading)return L.t("正在加载词库");
        if(!s.error.isEmpty())return s.error;
        if(s.paused)return L.t("自动轮换已暂停")+(s.pauseReason.isEmpty()?L.t("；手动翻词仍按独立开关朗读"):" · "+s.pauseReason);
        if(s.words.isEmpty())return L.t("当前筛选或分组没有可学单词");
        if(!s.activeScreen)return s.audioOption()?L.t("锁屏听词中"):L.t("锁屏暂停，解锁后恢复");
        if(s.collapsed)return s.audioOption()?L.t("收球听词中"):L.t("已收球，自动轮换暂停");
        if(s.detail)return L.t("正在看详情，自动轮换暂停");
        if(s.speech.isSpeakingSequence())return L.t("正在朗读，结束后继续计时");
        if(s.prefs.autoSpeak()&&s.speech.isInitializing())return L.t("等待语音引擎就绪");
        return L.t("自动轮换中 · 间隔 ")+s.prefs.intervalLabel()+L.t(" 秒");
    }
    private static final String CHANNEL="study";
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private View overlay;
    private Prefs prefs;
    private Speech speech;
    private PowerManager.WakeLock audioWake;
    private long wakeLeaseUntil;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private List<Word> words=new ArrayList<>();
    private int index, dragX, dragY;
    private volatile int generation;
    private ShuffleDeck shuffleDeck;
    private Map<String,Integer> wordPositions=new HashMap<>();
    private Rect usableBounds=new Rect(0,0,1,1);
    private boolean dragging,positionFramePending,marking;
    private boolean paused, collapsed, detail, revealed, activeScreen=true, destroyed, loading=true;
    private boolean receiverRegistered;
    private String error="",pauseReason="",activeScope="",activeBase="";
    private MarkUndo undo;
    private long undoToken;
    private final MarkHistory<MarkUndo> undoHistory=new MarkHistory<>();
    private String pendingAutoId="";
    private boolean initialRead=true,manualVoiceHinted,audioForeground;
    private static final class MarkUndo{Word word;String kind,scope,queue;boolean value,shuffled;long token;}
    private long due,lastScreenCheck;
    private TextView countdown,exampleAction;
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(destroyed||stopping) return;
            if(focused){syncScreen();handler.postDelayed(this,500);return;}
            long now=SystemClock.elapsedRealtime();tryAutoRead();
            if(now-lastScreenCheck>=500){syncScreen();maintainWakeLock();lastScreenCheck=now;}
            if(!collapsed&&activeScreen&&speech.isSpeakingSequence()&&countdown!=null)countdown.setText(headerStatus(L.t("朗读中")));
            if(canCycle()) {
                if(SystemClock.elapsedRealtime()>=due) advance(1);
                else if(countdown!=null) countdown.setText(headerStatus(getString(R.string.overlay_countdown,currentLevel(),remaining())));
            }
            handler.postDelayed(this,canCycle()?Math.max(16L,Math.min(250L,due-SystemClock.elapsedRealtime())):250L);
        }
    };
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){syncScreen();}
    };
    private boolean audioOption(){return !focused&&prefs.autoSpeak()&&(activeScreen?prefs.ballAudio():prefs.lockAudio());}
    private boolean canPresent(){
        if(activeScreen)return !collapsed||audioOption()&&speech.isReady();
        return audioOption()&&speech.isReady();
    }
    private void syncScreen(){
        boolean unlocked=((PowerManager)getSystemService(POWER_SERVICE)).isInteractive()&&!((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
        if(unlocked!=activeScreen){
            if(focused&&reviewFlow!=null)reviewFlow.setVisible(unlocked&&!collapsed);
            activeScreen=unlocked;dragging=false;
            if(!unlocked&&!audioOption())speech.stop();
            if(!audioOption())resetTimer();
            if(unlocked)encounter(false);
            if(!focused&&!paused&&canPresent()&&(!activeScreen||collapsed)&&!speech.isSpeakingSequence()&&current()!=null)readWord(current(),false);
            notifyState();
        }
        if(overlay!=null&&overlay.getVisibility()!=(activeScreen?View.VISIBLE:View.INVISIBLE))overlay.setVisibility(activeScreen?View.VISIBLE:View.INVISIBLE);
    }
    private void maintainWakeLock(){
        boolean needed=!focused&&!activeScreen&&!paused&&prefs.lockAudio()&&prefs.autoSpeak()&&speech.isReady()&&current()!=null;
        if(needed&&(!audioWake.isHeld()||SystemClock.elapsedRealtime()>wakeLeaseUntil-60000)){
            if(audioWake.isHeld())audioWake.release();
            audioWake.acquire(600000L);wakeLeaseUntil=SystemClock.elapsedRealtime()+600000L;
        }else if(!needed&&audioWake.isHeld())audioWake.release();
    }
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(L.context(base));}
    @Override public void onCreate() {
        super.onCreate();
        active=new java.lang.ref.WeakReference<>(this);prefs=new Prefs(this);setTheme(Ui.themeResource(this));Ui.applyTheme(this); windows=(WindowManager)getSystemService(WINDOW_SERVICE); usableBounds=calculateBounds(); speech=new Speech(this);speech.setOnUnavailable(()->openApp(true));
        speech.setOnInterrupted(reason->{if(destroyed||stopping)return;paused=true;pendingAutoId="";pauseReason=reason;prefs.put("session.paused",true);resetTimer();rebuild();maintainWakeLock();notifyState();android.widget.Toast.makeText(this,reason,android.widget.Toast.LENGTH_LONG).show();});
        speech.setPlaybackExpected(()->running&&!paused&&prefs.autoSpeak());
        audioWake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Cijian:Listening");
        audioWake.setReferenceCounted(false);
        speech.setOnChanged(()->{
            if(destroyed||stopping)return;
            Ui.exampleActionState(exampleAction,speech.isSpeakingExample());
            if(running)notifyState();tryAutoRead();
            if(!focused&&!paused&&canPresent()&&(!activeScreen||collapsed)&&current()!=null&&!speech.isSpeakingSequence())readWord(current(),false);
            maintainWakeLock();
        });
        NotificationChannel channel=new NotificationChannel(CHANNEL,L.t("悬浮学习"),NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(L.t("显示学习状态，并提供暂停和关闭入口"));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        activeScreen=((PowerManager)getSystemService(POWER_SERVICE)).isInteractive() && !((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
        IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(screenReceiver,filter);
        receiverRegistered=true;
        prefs.data.registerOnSharedPreferenceChangeListener(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && STOP.equals(intent.getAction())) { stopByUser();return START_NOT_STICKY; }
        if(intent==null&&!prefs.data.getBoolean("session.enabled",false)){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)) { stopSelf();return START_NOT_STICKY; }
        if(intent!=null&&intent.getBooleanExtra("fromAutoStart",false)&&!running){paused=true;pauseReason=L.t("自动启动后先暂停，点播放开始学习");prefs.put("session.paused",true);}
        if(Build.VERSION.SDK_INT>=34) startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE|(audioForeground?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0));
        else if(Build.VERSION.SDK_INT>=29)startForeground(7,notification(),audioForeground?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0);
        else startForeground(7,notification());
        stopping=false;prefs.put("session.enabled",true);
        if(intent!=null&&"BROWSE".equals(intent.getAction())&&focused){if(reviewFlow!=null)reviewFlow.close();reviewFlow=null;reviewPanel=null;focused=false;prefs.put("session.focused",false);running=false;}
        boolean focusRequest=intent!=null&&"FOCUS".equals(intent.getAction());
        if(focusRequest||!running&&prefs.data.getBoolean("session.focused",false)){
            focusQuiet=intent==null||intent.getBooleanExtra("fromAutoStart",false);if(reviewFlow!=null){reviewFlow.close();reviewFlow=null;reviewPanel=null;}focused=true;generation++;words=new ArrayList<>();shuffleDeck=null;reviewMode=focusRequest?intent.getStringExtra("mode"):prefs.data.getString("session.reviewMode","TASK");if(reviewMode==null)reviewMode="TASK";
            prefs.put("session.focused",true);prefs.put("session.reviewMode",reviewMode);running=true;loading=false;paused=true;pendingAutoId="";speech.stop();
            collapsed=!focusRequest&&prefs.data.getBoolean("session.collapsed",false);StudySurface.claim(relinquish);rebuild();handler.removeCallbacks(tick);handler.post(tick);publishState();return START_STICKY;
        }
        if(!running) {
            running=true;StudySurface.claim(relinquish);publishState();
            collapsed=prefs.data.getBoolean("session.collapsed",false);paused=prefs.data.getBoolean("session.paused",false);
            rebuild(); reload(); handler.post(tick);
        }
        if(intent!=null && TOGGLE.equals(intent.getAction())) togglePause();
        if(intent!=null && "REFRESH".equals(intent.getAction())) reload(intent.getStringExtra("preferredId"));
        return START_STICKY;
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FloatService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent toggle=focused?PendingIntent.getActivity(this,22,new Intent(this,ReviewActivity.class).putExtra("mode",reviewMode).putExtra("resume",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT):PendingIntent.getService(this,2,new Intent(this,FloatService.class).setAction(TOGGLE),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(L.t("词间 · ")+(focused?(focusQuiet?L.t("认真记忆 · 待开始"):L.t("认真记忆")):paused?L.t("已暂停"):collapsed?(canPresent()?L.t("收球听词中"):L.t("已收起")): L.t("悬浮学习中")))
            .setContentText(pauseReason.isEmpty()?L.t("点击返回设置 · 可在通知中暂停或关闭"):pauseReason).setContentIntent(open).setOngoing(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null,focused?L.t("App 内继续复习"):paused?L.t("继续"):L.t("暂停"),toggle).build())
            .addAction(new Notification.Action.Builder(null,L.t("关闭悬浮窗"),stop).build()).build();
    }
    private void notifyState() {if(destroyed||stopping||!running)return; publishState();getSystemService(NotificationManager.class).notify(7,notification()); }
    private Word current() { return words.isEmpty()?null:words.get(Math.min(index,words.size()-1)); }
    private String groupLabel="";
    private String currentLevel() { return current()==null?(prefs.level().equals("ALL")?L.t("全部等级"):prefs.level()):current().level; }
    private String headerStatus(String status){if(undo!=null)return L.t("撤销 ›\n上次标记");return prefs.groupMode().equals("SIZE")?L.t("换组 ›\n")+status:status;}
    private String remaining() {long ms=Math.max(0,due-SystemClock.elapsedRealtime());return ms>=10000?String.valueOf((ms+999)/1000):Interval.format(((ms+99)/100)*100);}
    private boolean canCycle() { return !focused && !paused && canPresent() && (!detail||!activeScreen) && !dragging && !marking && !speech.isSpeakingSequence() && !(prefs.autoSpeak()&&speech.isInitializing()) && !words.isEmpty() && !loading && error.isEmpty(); }
    private void resetTimer() { due=SystemClock.elapsedRealtime()+prefs.intervalMillis(); }
    private void reload() {reload(null);}
    private void reload(String preferredId) {reload(preferredId,false);}
    private void reload(String preferredId,boolean manualTransition) {
        final int request=++generation;
        handler.postDelayed(()->{if(!destroyed&&request==generation&&loading){generation++;loading=false;paused=true;pendingAutoId="";prefs.put("session.paused",true);error=L.t("加载时间较长，请重试；学习记录保留。");rebuild();notifyState();}},12000);
        speech.stop();pendingAutoId="";loading=true;error="";
        String old=preferredId!=null?preferredId:current()!=null?current().id:prefs.data.getString("currentId","");
        boolean shuffle=prefs.shuffle();
        Async.IO.execute(()->{
            try {
                if(destroyed||request!=generation)return;
                StudySession session=StudySession.load(WordStore.get(this),prefs);String base=StudySession.baseScope(prefs);
                List<Word> result=session.words;
                Map<String,String> candidates=new LinkedHashMap<>();
                for(Word w:result)candidates.put(w.id,w.word+"\u001f"+w.reading);
                String saved=ScopeMemory.position(prefs,base,session.scope,shuffle,"queue"),savedId=ScopeMemory.position(prefs,base,session.scope,shuffle,"id");
                ShuffleDeck prepared=shuffle?new ShuffleDeck(candidates,saved,session.scope):null;
                handler.post(()->{
                    if(destroyed||request!=generation)return;if(!prefs.data.getBoolean("session.enabled",false)){stopSelf();return;}
                    activeBase=base;activeScope=session.scope;groupLabel=session.label;words=result;index=0;shuffleDeck=prepared;wordPositions=new HashMap<>();
                    for(int i=0;i<words.size();i++)wordPositions.put(words.get(i).id,i);
                    String selected=shuffleDeck!=null?shuffleDeck.current():preferredId!=null?preferredId:savedId;
                    if(selected!=null&&wordPositions.containsKey(selected))index=wordPositions.get(selected);
                    boolean readNow=initialRead||current()!=null&&!current().id.equals(old);initialRead=false;loading=false;revealed=!prefs.conceal();resetTimer();rebuild();encounter(readNow);handler.removeCallbacks(tick);handler.post(tick);if(manualTransition&&current()!=null&&prefs.speakOnFlip()&&activeScreen&&!collapsed)readWord(current(),true);
                });
            }catch(Exception e){handler.post(()->{if(!destroyed&&request==generation){loading=false;paused=true;pendingAutoId="";error=L.t("词库读取失败，请关闭后重试");prefs.put("session.paused",true);rebuild();notifyState();}});}
        });
    }
    private void encounter(boolean speak) {
        if(loading||!error.isEmpty()||stopping)return;
        Word w=current();
        if(w==null)return;
        if(speak&&prefs.autoSpeak()&&!paused)pendingAutoId=w.id;
        if(!canPresent())return;
        ScopeMemory.save(prefs,activeBase,activeScope,shuffleDeck!=null,w.id,shuffleDeck==null?"":shuffleDeck.save());
        Async.IO.execute(()->WordStore.get(this).seen(w));
        if(speak&&prefs.autoSpeak()&&!paused){pendingAutoId=w.id;tryAutoRead();}
    }
    /** 开机只启动可见悬浮服务，实际请求语音时才提升为媒体播放服务。 / Boot only starts the visible special-use overlay; playback is promoted when speech is requested. */
    private boolean prepareAudio(){
        if(destroyed||!running)return false;if(audioForeground)return true;
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE|ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else if(Build.VERSION.SDK_INT>=29)startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            audioForeground=true;return true;
        }catch(RuntimeException blocked){
            paused=true;pendingAutoId="";pauseReason=L.t("系统暂不允许后台朗读，请返回 App 后再播放");prefs.put("session.paused",true);resetTimer();rebuild();notifyState();
            android.widget.Toast.makeText(this,pauseReason,android.widget.Toast.LENGTH_LONG).show();return false;
        }
    }
    private void readWord(Word word,boolean explicit){if(!loading&&error.isEmpty()&&prepareAudio())speech.speakWord(word,explicit);}
    private void tryAutoRead(){
        if(focused||destroyed||loading||!error.isEmpty()||paused||!prefs.autoSpeak()||!speech.isReady()||!canPresent()||speech.isSpeakingSequence())return;
        Word word=current();if(word==null||!word.id.equals(pendingAutoId))return;pendingAutoId="";readWord(word,false);
    }
    private void advance(int amount){advance(amount,false);}
    private void advance(int amount,boolean manual) {
        if(words.isEmpty()||marking||loading||stopping||!error.isEmpty()) return;
        speech.stop();pendingAutoId="";
        if(shuffleDeck!=null){
            String next=amount<0?shuffleDeck.previous():shuffleDeck.next();
            if(next!=null&&wordPositions.containsKey(next))index=wordPositions.get(next);
        }else index=(index+amount+words.size())%words.size();
        revealed=!prefs.conceal();
        resetTimer();rebuild();encounter(!manual);
        if(manual&&prefs.speakOnFlip()&&activeScreen&&!collapsed&&current()!=null){
            if(speech.isReady()||speech.isInitializing())readWord(current(),true);
            else if(!manualVoiceHinted){manualVoiceHinted=true;android.widget.Toast.makeText(this,L.t("尚无可用日语语音，可在发音设置中配置；仍可继续看词。"),android.widget.Toast.LENGTH_LONG).show();}
        }
    }
    private void togglePause(){
        if(focused)return;
        if(!error.isEmpty()){paused=true;reload();return;}
        paused=!paused;pauseReason="";prefs.put("session.paused",paused);if(paused){pendingAutoId="";speech.stop();}
        else {if(prefs.autoSpeak()&&!speech.isReady()&&!speech.isInitializing())speech.refresh();if(canPresent()&&prefs.autoSpeak()&&current()!=null){pendingAutoId=current().id;tryAutoRead();}}
        resetTimer();rebuild();maintainWakeLock();notifyState();
    }
    private void collapse(){
        collapsed=!collapsed;prefs.put("session.collapsed",collapsed);detail=false;
        if(collapsed&&!audioOption())speech.stop();
        if(!audioOption())resetTimer();
        rebuild();if(!focused)encounter(collapsed&&audioOption()&&!speech.isSpeakingSequence()&&!paused);
        maintainWakeLock();notifyState();
    }
    static void stopForRestore(){FloatService s=active.get();if(s!=null){s.paused=true;s.loading=true;s.pendingAutoId="";s.speech.stop();s.handler.removeCallbacks(s.tick);s.stopByUser();}}
    private void stopByUser(){if(stopping)return;stopping=true;generation++;handler.removeCallbacks(tick);pendingAutoId="";if(reviewFlow!=null)reviewFlow.setVisible(false);speech.stop();running=false;publishState();prefs.put("session.focused",false);prefs.put("session.enabled",false);prefs.put("session.collapsed",false);prefs.put("session.paused",false);stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(7);stopSelf();}
    private Rect calculateBounds() {
        if(Build.VERSION.SDK_INT>=30) {
            WindowMetrics metrics=windows.getCurrentWindowMetrics();
            Rect frame=metrics.getBounds();
            android.graphics.Insets safe=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            return new Rect(0,0,frame.width()-safe.left-safe.right,frame.height()-safe.top-safe.bottom);
        }
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(metrics);return new Rect(0,0,metrics.widthPixels,metrics.heightPixels);
    }
    private int screenWidth() { return usableBounds.width(); }
    private int screenHeight() { return usableBounds.height(); }
    private int d(float value) { return Ui.dp(this,value); }
    // 窗口横纵坐标始终采用物理方向，包括系统为从右到左语言时。 / Window x/y are physical coordinates, including when the system locale is RTL.
    @android.annotation.SuppressLint("RtlHardcoded")
    private void rebuild() {
        if(destroyed||stopping) return;
        if(overlay!=null) { try { windows.removeView(overlay); } catch(IllegalArgumentException ignored) {} overlay=null; }
        int width=collapsed?d(56):Math.min(d(prefs.width()),screenWidth()-d(16));
        DragLayout root=new DragLayout(this,new DragLayout.Listener(){
            public void start(){dragging=true;dragX=params.x;dragY=params.y;}
            public void move(float dx,float dy){params.x=dragX+Math.round(dx);params.y=dragY+Math.round(dy);clampCoordinates();schedulePosition();}
            public void finish(boolean cancelled){
                dragging=false;
                if(!cancelled&&prefs.snapEdges())params.x=params.x+overlay.getWidth()/2<screenWidth()/2?d(4):screenWidth()-overlay.getWidth()-d(4);
                clamp();String name=collapsed?"ball":"panel";prefs.put(name+"X",params.x);prefs.put(name+"Y",params.y);resetTimer();
            }
        });
        root.setPositionLocked(prefs.positionLocked());
        root.setBackground(Ui.shape(collapsed?Ui.GREEN:Ui.SURFACE,collapsed?28:18,this));
        root.setElevation(d(5));root.setAlpha(prefs.alpha()/100f);
        if(collapsed) {
            TextView ball=Ui.bold(this,L.t("词"),22,Ui.ON_GREEN);ball.setGravity(Gravity.CENTER);
            root.addView(ball,new LinearLayout.LayoutParams(d(56),d(56)));
            ball.setContentDescription(prefs.positionLocked()?L.t("词间悬浮球，点击展开；位置已锁定"):L.t("词间悬浮球，点击展开，任意位置可拖动"));root.setOnClickListener(v->collapse());
        } else if(focused){
            root.setPadding(d(4),d(4),d(4),d(4));LinearLayout tools=Ui.row(this);
            Ui.weighted(tools,control(L.t("● 收起"),L.t("收起复习悬浮窗"),v->{if(reviewFlow==null||reviewFlow.canLeave())collapse();},70));
            if(focusQuiet)tools.addView(control(L.t("开始"),L.t("开始本轮复习语音"),v->{focusQuiet=false;rebuild();},55));
            tools.addView(closeButton(false));root.addView(tools);
            if(reviewPanel==null){reviewPanel=Ui.column(this);}
            if(reviewPanel.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)reviewPanel.getParent()).removeView(reviewPanel);
            root.addView(reviewPanel,new LinearLayout.LayoutParams(-1,Math.max(d(180),Math.min(d(530),screenHeight()-d(72)))));
            if(!focusQuiet)reviewPanel.setPadding(0,0,0,0);
            if(reviewFlow==null&&!focusQuiet)reviewFlow=new ReviewFlow(this,reviewPanel,reviewMode,new ReviewFlow.Host(){
                public void exit(){transferReview(false);}public void transfer(){transferReview(true);}
                public boolean prepareAudio(){return FloatService.this.prepareAudio();}public boolean visible(){return running&&!destroyed&&activeScreen&&!collapsed&&!focusQuiet;}public boolean overlay(){return true;}
            },true);
            if(reviewFlow!=null)reviewFlow.setVisible(activeScreen);
            else if(reviewPanel.getChildCount()==0){Ui.pad(reviewPanel,18);reviewPanel.addView(Ui.heading(this,L.t("已恢复，先暂停"),22,Ui.INK));Ui.gap(reviewPanel,12);reviewPanel.addView(Ui.prose(this,L.t("点上方「开始」继续原来的复习批次。开始前不显示题目、不朗读、不评分。"),14,Ui.MUTED));}
        } else {
            root.setPadding(d(12),d(4),d(12),d(6));
            View grip=new View(this);grip.setBackground(Ui.shape(Ui.MUTED,2,this));grip.setAlpha(0.4f);
            LinearLayout.LayoutParams gripParams=new LinearLayout.LayoutParams(d(28),d(3));gripParams.gravity=Gravity.CENTER_HORIZONTAL;gripParams.setMargins(0,d(2),0,d(2));root.addView(grip,gripParams);
            LinearLayout header=Ui.row(this);
            Word headerWord=current();
            countdown=Ui.bold(this,headerStatus(currentLevel()+" · "+prefs.intervalLabel()+"s"),undo!=null?11:9,undo!=null||prefs.groupMode().equals("SIZE")?Ui.GREEN:Ui.MUTED);
            countdown.setMinHeight(d(32));countdown.setGravity(Gravity.CENTER_VERTICAL);
            countdown.setContentDescription(undo!=null?L.t("撤销上次标记"):prefs.groupMode().equals("SIZE")?L.t("一键换下一组"):L.t("拖动此处移动悬浮窗"));
            if(undo!=null||prefs.groupMode().equals("SIZE")){
                countdown.setBackground(Ui.shape(Ui.PALE,7,this));countdown.setGravity(Gravity.CENTER);
                countdown.setOnClickListener(v->{if(undo!=null)undoMark();else requestNextGroup();});
            }
            Ui.weighted(header,countdown);
            header.addView(new PositionLock(this,prefs.positionLocked(),v->prefs.put("positionLocked",!prefs.positionLocked())));
            header.addView(control(detail?L.t("简洁"):L.t("详情"),detail?L.t("收起详情"):L.t("打开词性说明和例句"),v->{detail=!detail;resetTimer();rebuild();},36));
            header.addView(control("●",L.t("收起为悬浮球"),v->collapse(),36));
            header.addView(closeButton(true));root.addView(header);
            if(!loading && error.isEmpty() && headerWord!=null) {
                // 词头独占完整宽度，不截断学习内容。 / Give the headword its own full-width row; never truncate study content.
                TextView heading=Ui.bold(this,headerWord.word,prefs.wordSize(),Ui.INK);
                heading.setContentDescription(prefs.positionLocked()?L.t("单词；位置已锁定"):L.t("单词；可从窗口任意位置拖动"));
                root.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            }
            if(loading||!error.isEmpty()||current()==null) {
                TextView empty=Ui.text(this,loading?L.t("正在打开本地词库…"):error.isEmpty()?(prefs.studyMode().equals("favorites")?L.t("当前只看收藏，但此范围没有可显示的收藏词。"):prefs.studyMode().equals("familiar")?L.t("当前只复习熟悉词，此范围还没有熟悉词。"):L.t("当前组或筛选下没有可学单词。可换组或调整学习范围。")):error,14,Ui.INK);
                empty.setPadding(0,d(12),0,d(12));root.addView(empty);
                if(!loading&&error.isEmpty()){
                    if(!prefs.studyMode().equals("all"))root.addView(Ui.button(this,L.t("查看全部词"),false,v->prefs.put("studyMode","all")));
                    else if(prefs.groupMode().equals("SIZE"))root.addView(Ui.button(this,L.t("换下一组"),false,v->requestNextGroup()));
                    else if(prefs.groupMode().equals("CUSTOM"))root.addView(Ui.button(this,L.t("关闭自选组"),false,v->prefs.put("groupMode","OFF")));
                    else if(!Frequency.effective(prefs.level(),prefs.frequency()).equals("ALL"))root.addView(Ui.button(this,L.t("清除词频筛选"),false,v->prefs.put("frequency","ALL")));
                    else if(prefs.hideFamiliar())root.addView(Ui.button(this,L.t("显示熟悉词"),false,v->prefs.put("hideFamiliar",false)));
                }
                Ui.gap(root,6);root.addView(Ui.button(this,L.t("返回词间 App"),false,v->openApp(false)));
                root.addView(Ui.button(this,L.t("重试加载"),false,v->reload()));
                root.addView(Ui.button(this,L.t("关闭悬浮窗"),false,v->stopByUser()));
            } else {
                Word w=current();
                TextView grammar=Ui.text(this,Ui.reading(w)+"  "+w.pitch+"   ·   "+w.pos+("NONE".equals(w.frequency)?"":" · "+L.t(Frequency.label(w.frequency))),prefs.readingSize(),Ui.GREEN);
                grammar.setPadding(0,d(2),0,d(2));grammar.setContentDescription(L.t("读音、词性和音调；点击查看音调说明"));grammar.setFocusable(true);grammar.setOnClickListener(v->openPitch(w));root.addView(grammar);
                TextView meaning=Ui.text(this,revealed?Ui.meaning(w,prefs):L.t("点这里揭晓释义"),prefs.meaningSize(),Ui.INK);
                if(!detail){meaning.setMaxLines(prefs.meaningLines());meaning.setEllipsize(android.text.TextUtils.TruncateAt.END);}
                meaning.setMinHeight(d(26));meaning.setGravity(Gravity.CENTER_VERTICAL);
                meaning.setOnClickListener(v->{revealed=!revealed;resetTimer();rebuild();});root.addView(meaning);
                if(detail) {
                    LinearLayout links=Ui.row(this);Ui.weighted(links,detailButton(L.t("App 内学习"),L.t("App 内继续浏览  ↗"),v->transferBrowse()));Space linkGap=new Space(this);links.addView(linkGap,new LinearLayout.LayoutParams(d(6),1));Ui.weighted(links,detailButton(L.t("返回主页"),L.t("返回词间 App  ↗"),v->openApp(false)));Ui.gap(root,4);root.addView(links);
                    ScrollView scroll=new ScrollView(this);LinearLayout extra=Ui.column(this);
                    Ui.gap(extra,8);extra.addView(Ui.prose(this,L.t("上下滑动看内容，左右拖动移窗口"),10,Ui.MUTED));Ui.gap(extra,8);
                    if(!prefs.groupMode().equals("OFF")){extra.addView(Ui.text(this,groupLabel,11,Ui.GREEN));Ui.gap(extra,8);}
                    extra.addView(Ui.bold(this,L.t("词性 · ")+L.grammar(Grammar.describe(w.pos)),12,Ui.GREEN));
                    Ui.gap(extra,8);extra.addView(Ui.text(this,L.t("词频 · ")+("NONE".equals(w.frequency)?L.t("原词库未分档"):L.t(Frequency.label(w.frequency))+L.t("（JLPT 真题）")),12,Ui.MUTED));
                    if(!w.example.isEmpty()) {
                        Ui.gap(extra,10);extra.addView(Ui.text(this,w.example,15,Ui.INK));
                        if(revealed) extra.addView(Ui.text(this,w.exampleMeaning,12,Ui.MUTED));
                        Ui.gap(extra,8);exampleAction=Ui.exampleAction(this,speech::isSpeakingExample,v->{pendingAutoId="";if(speech.isSpeakingExample())speech.stop();else if(prepareAudio())speech.speakExample(w,true);});Ui.exampleActionState(exampleAction,speech.isSpeakingExample());extra.addView(exampleAction);
                    }
                    if(!w.note.isEmpty()) extra.addView(Ui.text(this,w.note,12,Ui.MUTED));
                    if(prefs.groupMode().equals("SIZE")){Ui.gap(extra,8);extra.addView(detailButton(L.t("换下一组"),L.t("换下一组"),v->requestNextGroup()));}
                    scroll.addView(extra);
                    root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(d(prefs.detailHeight()),Math.max(d(90),screenHeight()-d(310)))));
                }
                LinearLayout actions=Ui.row(this);
                addAction(actions,"‹",L.t("上一个单词"),v->advance(-1,true));
                addAction(actions,paused?"▶":"Ⅱ",paused?L.t("继续自动轮换，并按设置朗读当前词"):L.t("暂停自动轮换"),v->togglePause());
                addAction(actions,L.t("音"),L.t("朗读单词"),v->{pendingAutoId="";resetTimer();readWord(w,true);});
                addAction(actions,w.favorite?"★":"☆",w.favorite?L.t("取消收藏"):L.t("收藏单词"),v->{
                    if(marking||loading)return;boolean old=w.favorite;boolean value=!old;marking=true;
                    Async.IO.execute(()->{try{WordStore.get(this).favorite(w,value);handler.post(()->{marking=false;if(destroyed||stopping)return;rememberMark(w,"favorite",old);if(!value&&prefs.studyMode().equals("favorites"))reload(null,true);else rebuild();});}catch(Exception failed){handler.post(()->{marking=false;android.widget.Toast.makeText(this,L.t("标记保存失败，请重试；原记录保留。"),android.widget.Toast.LENGTH_LONG).show();});}});
                });
                addAction(actions,w.familiar?L.t("已熟"):L.t("熟"),w.familiar?L.t("取消熟悉标记"):L.t("标为熟悉并切到下一个词"),v->markFamiliar());
                addAction(actions,"›",L.t("下一个单词"),v->advance(1,true));root.addView(actions);
            }
        }
        if(!collapsed&&!focused)fitShortScreen(root,width);
        if(focused&&collapsed&&reviewFlow!=null)reviewFlow.setVisible(false);
        params=new WindowManager.LayoutParams(width,collapsed?d(56):WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        String position=collapsed?"ball":"panel";
        params.x=prefs.data.getInt(position+"X",screenWidth()-width-d(8));
        params.y=prefs.data.getInt(position+"Y",d(140));
        overlay=root;overlay.setVisibility(activeScreen?View.VISIBLE:View.INVISIBLE);
        try { windows.addView(overlay,params);overlay.post(this::clamp); }
        catch(RuntimeException e) { overlay=null;stopSelf(); }
    }
    /** 短屏保留顶部及操作区，中间内容统一滚动。 / On short displays keep the header and actions visible, with one scrolling content area. */
    private void fitShortScreen(LinearLayout root,int width){
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int available=screenHeight()-d(8);
        if(root.getMeasuredHeight()<=available||root.getChildCount()<4)return;
        int last=root.getChildCount()-1,reserved=root.getPaddingTop()+root.getPaddingBottom();
        for(int index:new int[]{0,1,last}){
            View child=root.getChildAt(index);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();
            reserved+=child.getMeasuredHeight()+lp.topMargin+lp.bottomMargin;
        }
        LinearLayout content=Ui.column(this);int count=last-2;
        for(int i=0;i<count;i++){
            View child=root.getChildAt(2);root.removeViewAt(2);
            if(child instanceof ScrollView){ScrollView nested=(ScrollView)child;child=nested.getChildAt(0);nested.removeAllViews();}
            content.addView(child,new LinearLayout.LayoutParams(-1,-2));
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        root.addView(scroll,2,new LinearLayout.LayoutParams(-1,Math.max(d(32),available-reserved)));
    }
    private TextView detailButton(String label,String description,View.OnClickListener click){
        TextView button=Ui.button(this,label,false,click);button.setTextSize(12);button.setPadding(d(6),d(6),d(6),d(6));button.setMinHeight(d(44));button.setContentDescription(description);return button;
    }
    private TextView control(String label,String description,View.OnClickListener listener,int width) {
        TextView view=Ui.bold(this,label,12,Ui.GREEN);view.setGravity(Gravity.CENTER);view.setMinHeight(d(36));
        view.setLayoutParams(new LinearLayout.LayoutParams(d(width),-2));view.setContentDescription(description);view.setOnClickListener(listener);return view;
    }
    /** 关闭入口始终可见，并提供清楚的底色与触控区。 / Closing remains visible with a distinct background and touch target. */
    private TextView closeButton(boolean icon){TextView close=control(icon?"×":L.t("关闭"),L.t("关闭悬浮窗"),v->stopByUser(),icon?40:60);close.setTextSize(icon?20:14);close.setMinHeight(d(44));close.setBackground(Ui.shape(Ui.PALE,10,this));return close;}
    private void addAction(LinearLayout parent,String label,String description,View.OnClickListener listener) {
        TextView button=control(label,description,listener,40);button.setTextSize(label.equals(L.t("音"))||label.equals(L.t("熟"))||label.equals(L.t("已熟"))?13:20);
        button.setMinHeight(d(32));parent.addView(button,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void openPitch(Word word){speech.stop();pendingAutoId="";if(!collapsed){collapsed=true;prefs.put("session.collapsed",true);detail=false;rebuild();}startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("pitchWord",word.id));}
    private void requestNextGroup(){
        if(loading||!prefs.groupMode().equals("SIZE"))return;
        speech.stop();loading=true;
        Async.IO.execute(()->{
            try{StudySession.next(WordStore.get(this),prefs);}
            catch(Exception failed){handler.post(()->{if(!destroyed){loading=false;paused=true;pendingAutoId="";error=L.t("换组失败，请返回 App 重试");prefs.put("session.paused",true);rebuild();notifyState();}});}
        });
    }
    private MarkUndo captureMark(Word word,String kind,boolean value){MarkUndo mark=new MarkUndo();mark.word=word;mark.kind=kind;mark.value=value;mark.scope=activeScope;mark.shuffled=shuffleDeck!=null;mark.queue=shuffleDeck==null?"":shuffleDeck.save();return mark;}
    private void rememberMark(Word word,String kind,boolean value){rememberMark(captureMark(word,kind,value));}
    private void rememberMark(MarkUndo mark){
        mark.token=++undoToken;undoHistory.push(mark,SystemClock.elapsedRealtime());undo=mark;
        handler.postDelayed(()->{if(!destroyed&&undo!=null&&undoToken==mark.token){undoHistory.clear();undo=null;rebuild();}},8000);
    }
    private void undoMark(){
        if(marking)return;MarkUndo mark=undoHistory.pop(SystemClock.elapsedRealtime());if(mark==null)return;undo=undoHistory.peek(SystemClock.elapsedRealtime());marking=true;speech.stop();pendingAutoId="";long mine=++undoToken;handler.postDelayed(()->{if(!destroyed&&mine==undoToken){undoHistory.clear();undo=null;rebuild();}},8000);
        Async.IO.execute(()->{
            try{if(mark.kind.equals("favorite"))WordStore.get(this).favorite(mark.word,mark.value);else WordStore.get(this).familiar(mark.word,mark.value);}catch(Exception failed){handler.post(()->{marking=false;rememberMark(mark);android.widget.Toast.makeText(this,L.t("撤销失败，请重试。"),android.widget.Toast.LENGTH_LONG).show();});return;}
            handler.post(()->{marking=false;if(destroyed||stopping)return;boolean same=activeScope.equals(mark.scope)&&mark.shuffled==(shuffleDeck!=null);if(same){ScopeMemory.save(prefs,activeBase,activeScope,mark.shuffled,mark.word.id,mark.queue);prefs.put("currentId",mark.word.id);}reload(same?mark.word.id:null);android.widget.Toast.makeText(this,L.t("已撤销上次标记"),android.widget.Toast.LENGTH_SHORT).show();});
        });
    }
    private void markFamiliar() {
        Word w=current();if(w==null||marking||loading||stopping||!error.isEmpty())return;
        MarkUndo change=captureMark(w,"familiar",w.familiar);boolean value=!w.familiar;marking=true;
        boolean removes=(value&&prefs.effectiveHideFamiliar())||(!value&&prefs.studyMode().equals("familiar"));
        String next=removes?(shuffleDeck==null?words.get((index+1)%words.size()).id:shuffleDeck.next()):null;
        if(removes&&shuffleDeck!=null)ScopeMemory.save(prefs,activeBase,activeScope,true,next,shuffleDeck.save());
        Async.IO.execute(()->{
            try{WordStore.get(this).familiar(w,value);}catch(Exception failed){handler.post(()->{marking=false;android.widget.Toast.makeText(this,L.t("标记保存失败，请重试；原记录保留。"),android.widget.Toast.LENGTH_LONG).show();});return;}
            handler.post(()->{
                marking=false;if(destroyed||stopping)return;rememberMark(change);
                detail=false;
                if(removes)reload(next,true);
                else if(value&&current()!=null&&w.id.equals(current().id))advance(1,true);
                else rebuild();
            });
        });
    }
    private void transferReview(boolean resume){
        if(reviewFlow!=null&&!reviewFlow.canLeave())return;
        Intent next=new Intent(this,resume?ReviewActivity.class:MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(resume)next.putExtra("mode",reviewMode).putExtra("resume",true);else next.putExtra("openReview",true);
        try{startActivity(next);stopByUser();}catch(RuntimeException failed){android.widget.Toast.makeText(this,L.t("暂时无法打开 App，当前进度保留。"),android.widget.Toast.LENGTH_LONG).show();}
    }
    private void transferBrowse(){
        try{startActivity(new Intent(this,BrowseActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));stopByUser();}catch(RuntimeException failed){android.widget.Toast.makeText(this,L.t("暂时无法打开 App。"),android.widget.Toast.LENGTH_LONG).show();}
    }
    private void openApp(boolean voiceHelp) {
        if(!collapsed)collapse();
        Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if(voiceHelp)intent.putExtra("voiceHelp",true);
        try{startActivity(intent);}catch(ActivityNotFoundException ignored){}
    }
    private void clampCoordinates() {
        if(overlay==null||params==null)return;
        params.x=Math.max(0,Math.min(params.x,screenWidth()-overlay.getWidth()));
        params.y=Math.max(0,Math.min(params.y,screenHeight()-overlay.getHeight()-d(8)));
    }
    private void schedulePosition() {
        if(positionFramePending)return;
        positionFramePending=true;
        Choreographer.getInstance().postFrameCallback(time->{positionFramePending=false;applyPosition();});
    }
    private void applyPosition(){
        if(destroyed||overlay==null||params==null)return;
        try{windows.updateViewLayout(overlay,params);}catch(IllegalArgumentException ignored){}
    }
    private void clamp(){clampCoordinates();applyPosition();}
    @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) {
        if(destroyed||key==null)return;
        if(focused){if(reviewFlow!=null)reviewFlow.checkSettings();if(key.equals("theme")||key.equals("accent")||key.equals("customColor")){Ui.applyTheme(this);rebuild();}return;}
        if(key.equals("voiceEngine")||key.equals("voice.refresh")){speech.refresh();return;}
        if(key.equals("scopeMemory")||key.equals("scopeMemoryMigrated")||key.startsWith("appWord")||key.startsWith("appReading")||key.startsWith("appMeaning"))return;
        if(key.equals("group.state")||key.equals("speechRate")||key.equals("speechGap")||key.equals("focusNewTarget")||key.equals("learningStyle")||key.equals("reverseRecall")||key.equals("dailyNewTarget")||key.equals("showFirstPass")||key.startsWith("deck.")||key.startsWith("backup."))return;
        if(key.endsWith("X")||key.endsWith("Y")||key.equals("currentId")||key.startsWith("queue.")||key.startsWith("boot.")||key.startsWith("session."))return;
        if(key.equals("theme")||key.equals("accent")||key.equals("customColor")){setTheme(Ui.themeResource(this));Ui.applyTheme(this);rebuild();return;}
        if(key.equals("autoSpeak")){if(!prefs.autoSpeak()){pendingAutoId="";speech.stop();}else if(!paused&&current()!=null){pendingAutoId=current().id;tryAutoRead();}}
        if(key.equals("ballAudio")||key.equals("lockAudio")||key.equals("autoSpeak")){
            if((collapsed||!activeScreen)&&!audioOption())speech.stop();
            else if(!paused&&canPresent()&&(collapsed||!activeScreen)&&current()!=null&&!speech.isSpeakingSequence())readWord(current(),false);
            maintainWakeLock();
        }
        if(key.equals("level")||key.equals("shuffle")||key.equals("hideFamiliar")||key.equals("favoritesOnly")||key.equals("frequency")||key.equals("studyMode")||key.equals("groupMode")||key.equals("groupSize")||key.equals("customWords")||key.equals("groupRevision"))reload();
        else {resetTimer();rebuild();handler.removeCallbacks(tick);handler.post(tick);}
    }
    @Override public void onConfigurationChanged(Configuration config) {super.onConfigurationChanged(config);usableBounds=calculateBounds();setTheme(Ui.themeResource(this));Ui.applyTheme(this);rebuild();}
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
        final String[] output={""};
        Runnable collect=()->{
            try{
                org.json.JSONObject state=new org.json.JSONObject().put("focused",focused).put("reviewMode",reviewMode).put("collapsed",collapsed).put("paused",paused).put("dragging",dragging)
                    .put("pendingAutoId",pendingAutoId).put("canUndo",undo!=null).put("pauseReason",pauseReason).put("groupLabel",groupLabel).put("groupMode",prefs.groupMode()).put("studyMode",prefs.studyMode()).put("poolSize",words.size()).put("loading",loading).put("speaking",speech!=null&&speech.isSpeakingSequence()).put("speechReady",speech!=null&&speech.isReady()).put("speechInitializing",speech!=null&&speech.isInitializing()).put("audioForeground",audioForeground).put("shuffle",shuffleDeck!=null).put("screenActive",activeScreen).put("listeningWakeLock",audioWake!=null&&audioWake.isHeld());
                Word w=current();if(w!=null)state.put("id",w.id).put("word",w.word).put("pos",w.pos).put("frequency",w.frequency);
                if(reviewFlow!=null)state.put("review",new org.json.JSONObject(reviewFlow.snapshot()));
                org.json.JSONArray controls=new org.json.JSONArray();if(overlay!=null)collectControls(overlay,controls);state.put("controls",controls);
                output[0]=state.toString();
            }catch(Exception error){output[0]="{}";}finally{latch.countDown();}
        };
        if(Looper.myLooper()==Looper.getMainLooper())collect.run();else handler.post(collect);
        try{latch.await(1500,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        writer.println("CIJIAN_STATE="+output[0]);
    }
    private void collectControls(View view,org.json.JSONArray controls) throws org.json.JSONException{
        if(view.getVisibility()!=View.VISIBLE)return;
        if(!(view instanceof TextView)&&view.isClickable()&&view.getContentDescription()!=null){
            int[] point=new int[2];view.getLocationOnScreen(point);
            controls.put(new org.json.JSONObject().put("text","").put("description",view.getContentDescription().toString()).put("bounds",new org.json.JSONArray().put(point[0]).put(point[1]).put(view.getWidth()).put(view.getHeight())));
        }
        if(view instanceof TextView){
            TextView text=(TextView)view;int[] point=new int[2];view.getLocationOnScreen(point);
            org.json.JSONObject item=new org.json.JSONObject().put("text",text.getText().toString()).put("description",view.getContentDescription()==null?"":view.getContentDescription().toString())
                .put("bounds",new org.json.JSONArray().put(point[0]).put(point[1]).put(view.getWidth()).put(view.getHeight()));
            android.text.Layout layout=text.getLayout();
            if(layout!=null && layout.getLineCount()>0){
                int ellipsized=0;for(int i=0;i<layout.getLineCount();i++)ellipsized+=layout.getEllipsisCount(i);
                item.put("lineEnd",layout.getLineEnd(layout.getLineCount()-1)).put("ellipsis",ellipsized)
                    .put("textHeight",layout.getHeight()+text.getCompoundPaddingTop()+text.getCompoundPaddingBottom());
            }
            controls.put(item);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectControls(group.getChildAt(i),controls);}
    }
    @Override public IBinder onBind(Intent intent) {return null;}
    @Override public void onDestroy() {
        destroyed=true;running=false;publishState();StudySurface.leave(relinquish);if(reviewFlow!=null)reviewFlow.close();if(active.get()==this)active.clear();generation++;handler.removeCallbacksAndMessages(null);
        if(prefs!=null)prefs.data.unregisterOnSharedPreferenceChangeListener(this);
        if(receiverRegistered)unregisterReceiver(screenReceiver);
        if(overlay!=null)try{windows.removeView(overlay);}catch(IllegalArgumentException ignored){}
        if(audioWake!=null&&audioWake.isHeld())audioWake.release();
        if(speech!=null)speech.close();stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager.class).cancel(7);super.onDestroy();
    }
}

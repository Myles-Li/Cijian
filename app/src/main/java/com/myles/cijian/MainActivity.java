package com.myles.cijian;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MainActivity extends Activity {
    private Prefs prefs;
    private Speech speech;
    private WordStore store;
    private LinearLayout root, body, nav;
    private ScrollView scroll;
    private int tab, settingsCategory=-1;private volatile int searchGeneration;
    private volatile boolean alive=true;private boolean pendingStart, foreground;
    private String query="",libraryMode="all",libraryLevel="ALL",libraryFrequency="ALL";
    private TextView voiceStatus,exampleAction;
    private boolean shownOverlay;
    private final Runnable overlayState=()->{if(alive&&foreground&&store!=null&&tab==0&&shownOverlay!=FloatService.running){int y=scroll==null?0:scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}};
    private boolean stateRegistered;
    private final java.util.Set<String> openFolds=new java.util.HashSet<>();private AlertDialog languagePrompt;
    private boolean resumedOnce,voiceGuidePending;private String pitchPending="",settingsAnchor="";private boolean voiceAdvanced;
    private List<Word> preview;
    private WordStore.HomeProgress cachedHome;private StudyTasks cachedTasks;private String cachedHomeKey="",cachedTasksKey="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private static final int OVERLAY=10, NOTIFICATIONS=11, EXPORT=12, IMPORT=13;

    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(L.context(base));}
    @Override public void onCreate(Bundle state) {
        final int launchTheme=Ui.themeResource(this);setTheme(launchTheme);Ui.applyTheme(this);
        super.onCreate(state);
        prefs=new Prefs(this);prepareLanguagePrompt();StudySurface.observe(overlayState);stateRegistered=true;final String launchEngine=prefs.data.getString("voiceEngine","AUTO");speech=new Speech(this);speech.setOnUnavailable(this::voiceGuide);
        speech.setOnChanged(()->{if(alive&&voiceStatus!=null)voiceStatus.setText(speech.status());Ui.exampleActionState(exampleAction,speech.isSpeakingExample());});
        voiceGuidePending=getIntent().getBooleanExtra("voiceHelp",false);pitchPending=getIntent().getStringExtra("pitchWord");if(pitchPending==null)pitchPending="";
        if(getIntent().getBooleanExtra("openReview",false))tab=2;
        if(state!=null) {java.util.ArrayList<String> folds=state.getStringArrayList("openFolds");if(folds!=null)openFolds.addAll(folds);voiceAdvanced=state.getBoolean("voiceAdvanced");settingsCategory=state.getInt("settingsCategory",-1);tab=state.getInt("tab");query=state.getString("query","");libraryMode=state.getString("libraryMode","all");pendingStart=state.getBoolean("pendingStart");libraryLevel=state.getString("libraryLevel","ALL");libraryFrequency=state.getString("libraryFrequency","ALL");}
        root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        root.requestApplyInsets();
        root.addView(Ui.text(this,L.t("正在准备你的本地词库…"),16,Ui.GREEN));
        Async.IO.execute(()->{
            try {store=WordStore.get(this);ScopeMemory.migrate(prefs);handler.post(()->{if(alive){if(Ui.themeResource(this)!=launchTheme){recreate();return;}Ui.applyTheme(this);root.setBackgroundColor(Ui.BG);if(!launchEngine.equals(prefs.data.getString("voiceEngine","AUTO")))speech.refresh();render();openPendingPitch();if(voiceGuidePending){voiceGuidePending=false;voiceGuide();}}});}
            catch(Exception error) {handler.post(()->{if(alive){root.removeAllViews();Ui.pad(root,24);root.addView(Ui.text(this,L.t("词库加载失败。请关闭应用后重试。"),16,Ui.INK));}});}
        });
    }
    @Override protected void onResume() {
        super.onResume();foreground=true;
        if(pendingStart && Settings.canDrawOverlays(this)) {pendingStart=false;requestNotificationsThenStart();}
        if(resumedOnce)speech.refresh();resumedOnce=true;
        if(store!=null)render();
    }
    @Override protected void onPause(){foreground=false;speech.stop();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putStringArrayList("openFolds",new java.util.ArrayList<>(openFolds));state.putBoolean("voiceAdvanced",voiceAdvanced);state.putInt("settingsCategory",settingsCategory);state.putString("libraryLevel",libraryLevel);state.putString("libraryFrequency",libraryFrequency);state.putInt("tab",tab);state.putString("query",query);state.putString("libraryMode",libraryMode);state.putBoolean("pendingStart",pendingStart);super.onSaveInstanceState(state);
    }
    private int d(float n){return Ui.dp(this,n);}
    private void render() {
        searchGeneration++;voiceStatus=null;
        root.removeAllViews();
        LinearLayout masthead=Ui.row(this);masthead.setPadding(d(20),d(10),d(20),d(4));
        TextView logo=Ui.bold(this,L.t("词"),20,Ui.ON_GREEN);logo.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);logo.setGravity(Gravity.CENTER);logo.setBackground(Ui.shape(Ui.GREEN,13,this));
        masthead.addView(logo,new LinearLayout.LayoutParams(d(36),d(36)));
        LinearLayout brand=Ui.column(this);brand.setPadding(d(12),0,0,0);
        brand.addView(Ui.bold(this,L.t("词间"),20,Ui.INK));brand.addView(Ui.text(this,L.t("给自己的日语角落"),10,Ui.MUTED));
        Ui.weighted(masthead,brand);root.addView(masthead);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        body=Ui.column(this);body.setPadding(d(tab==0?20:24),d(tab==0?10:18),d(tab==0?20:24),d(tab==0?12:24));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=Ui.row(this);nav.setPadding(d(16),d(6),d(16),d(8));nav.setBackgroundColor(Ui.BG);
        String[] names={L.t("学习"),L.t("词库"),L.t("复习"),L.t("设置")};
        for(int i=0;i<names.length;i++) {
            final int selected=i;
            TextView item=Ui.button(this,names[i],tab==i,v->{tab=selected;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(d(4),0,d(4),0);nav.addView(item,p);
        }
        root.addView(nav);
        if(tab==0)home();else if(tab==1)library();else if(tab==2)reviewPage();else settings();Ui.tint(root);scrollToSetting();showLanguagePrompt();
    }
    private void title(String eyebrow,String heading,String subtitle) {
        TextView overline=Ui.text(this,eyebrow,10,Ui.GREEN);overline.setLetterSpacing(.05f);body.addView(overline);Ui.gap(body,10);
        body.addView(Ui.heading(this,heading,tab==3?25:27,Ui.INK));Ui.gap(body,12);
        TextView sub=Ui.prose(this,subtitle,13,Ui.MUTED);body.addView(sub);Ui.gap(body,24);
    }
    private LinearLayout card(int color) {
        LinearLayout c=Ui.column(this);Ui.pad(c,18);c.setBackground(Ui.shape(color,20,this));body.addView(c);return c;
    }
    /** 主要操作在首屏，辅助工具按需展开。 / Primary actions stay above the fold; optional tools expand on demand. */
    private void fold(String label,Runnable populate){
        fold(label,openFolds.contains(label),open->{if(open)openFolds.add(label);else openFolds.remove(label);},populate);
    }
    /** 让标题与子内容共享边框，并用缩进显示归属。 / Keep header and children inside one border, with an inset content area. */
    private void fold(String label,boolean opened,java.util.function.Consumer<Boolean> changed,Runnable populate){
        LinearLayout holder=Ui.column(this);android.graphics.drawable.GradientDrawable bg=Ui.shape(Ui.SURFACE,16,this);bg.setStroke(d(1),Ui.PALE);holder.setBackground(bg);Ui.pad(holder,8);body.addView(holder);
        TextView toggle=Ui.button(this,getString(R.string.fold_title,label,opened?"⌄":"›"),false,null);toggle.setBackground(Ui.shape(Ui.SURFACE,10,this));toggle.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);toggle.setMinHeight(d(44));if(Build.VERSION.SDK_INT>=28)toggle.setAccessibilityHeading(true);holder.addView(toggle);
        LinearLayout frame=Ui.row(this),content=Ui.column(this);View rail=new View(this);rail.setBackgroundColor(Ui.GREEN);rail.setAlpha(.4f);frame.addView(rail,new LinearLayout.LayoutParams(d(2),-1));content.setPadding(d(12),d(8),d(4),d(8));Ui.weighted(frame,content);holder.addView(frame);frame.setVisibility(opened?View.VISIBLE:View.GONE);content.setContentDescription(L.t("分组内容：")+label);
        ScrollView owner=scroll;
        toggle.setOnClickListener(v->{boolean open=frame.getVisibility()!=View.VISIBLE;frame.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(getString(R.string.fold_title,label,open?"⌄":"›"));changed.accept(open);if(open)holder.postDelayed(()->{if(holder.isAttachedToWindow()&&owner==scroll&&frame.getVisibility()==View.VISIBLE){android.graphics.Rect rect=new android.graphics.Rect();holder.getDrawingRect(rect);owner.offsetDescendantRectToMyCoords(holder,rect);owner.smoothScrollTo(0,Math.max(0,rect.top-d(8)));}},60);});
        LinearLayout parent=body;body=content;try{populate.run();}finally{body=parent;}
    }
    private void rerenderHere(){int y=scroll==null?0:scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}

    private void overlayButton(){
        body.addView(Ui.button(this,FloatService.running?L.t("关闭悬浮学习"):L.t("开启悬浮学习  ↗"),!FloatService.running,v->{if(FloatService.running){FloatService.stopForRestore();}else{prefs.put("session.focused",prefs.learningStyle().equals("FOCUS"));beginOverlay();}}));
    }
    private void home() {
        shownOverlay=FloatService.running;
        body.addView(Ui.heading(this,L.t("今天，学一点日语。"),22,Ui.INK));Ui.gap(body,12);
        LinearLayout styles=Ui.row(this);Ui.weighted(styles,Ui.button(this,L.t("浏览一遍"),prefs.learningStyle().equals("ROAM"),v->{if(v.isAttachedToWindow())chooseStyle("ROAM");}));Space styleGap=new Space(this);styles.addView(styleGap,new LinearLayout.LayoutParams(d(8),1));Ui.weighted(styles,Ui.button(this,L.t("认真记忆"),prefs.learningStyle().equals("FOCUS"),v->{if(v.isAttachedToWindow())chooseStyle("FOCUS");}));body.addView(styles);
        TextView range=Ui.text(this,(prefs.groupMode().equals("CUSTOM")?L.t("自选范围"):prefs.level().equals("ALL")?L.t("全部等级"):prefs.level())+" · "+L.t(Frequency.label(Frequency.effective(prefs.level(),prefs.frequency())))+"  ›",12,Ui.GREEN);range.setMinHeight(d(36));range.setGravity(Gravity.CENTER_VERTICAL);range.setOnClickListener(v->{tab=3;settingsCategory=1;render();});body.addView(range);Ui.gap(body,6);
        LinearLayout stats=Ui.row(this);String[] labels={L.t("今日遇见 · 全库"),L.t("手动熟悉"),L.t("已收藏")};TextView[] counts=new TextView[3];
        for(int i=0;i<3;i++){LinearLayout stat=Ui.column(this);counts[i]=Ui.bold(this,"—",21,Ui.INK);stat.addView(counts[i]);Ui.gap(stat,3);stat.addView(Ui.text(this,labels[i],10,Ui.MUTED));Ui.weighted(stats,stat);}body.addView(stats);stats.setOnClickListener(v->statisticsGuide());stats.setContentDescription(L.t("学习统计说明"));Ui.gap(body,12);
        java.util.function.Consumer<WordStore.HomeProgress> update=progress->{};java.util.function.Consumer<StudyTasks> taskUpdate=tasks->{};if(prefs.learningStyle().equals("FOCUS"))taskUpdate=taskCard(false);else if(prefs.showFirstPass())update=firstPassCard();else body.addView(Ui.button(this,L.t("显示学习进度"),false,v->{prefs.put("showFirstPass",true);render();}));refreshHome(counts,update,taskUpdate);Ui.gap(body,12);
        overlayButton();Ui.gap(body,8);body.addView(Ui.button(this,prefs.learningStyle().equals("FOCUS")?L.t("App 内认真记忆"):L.t("App 内连续浏览"),true,v->{if(prefs.learningStyle().equals("FOCUS"))startReview("TASK");else startActivity(new Intent(this,BrowseActivity.class));}));Ui.gap(body,12);
        fold(L.t("学习工具"),()->{body.addView(Ui.button(this,L.t("学习范围与分组"),false,v->{tab=3;settingsCategory=1;render();}));if(prefs.groupMode().equals("SIZE")){Ui.gap(body,8);body.addView(Ui.button(this,L.t("换下一组"),false,v->nextGroup()));}Ui.gap(body,8);body.addView(Ui.button(this,L.t("进入复习"),false,v->{tab=2;render();}));Ui.gap(body,12);overlayPreview();Ui.gap(body,10);body.addView(Ui.button(this,L.t("日语发音 · 检测与设置"),false,v->voiceGuide()));Ui.gap(body,8);body.addView(Ui.button(this,L.t("零基础 · 五十音图"),false,v->KanaGuide.show(this,speech)));});
    }

    private void overlayPreview(){
        LinearLayout heading=Ui.row(this);heading.setMinimumHeight(d(36));Ui.weighted(heading,Ui.bold(this,L.t("悬浮窗外观"),13,Ui.INK));heading.addView(Ui.text(this,L.t("点击调整 ›"),11,Ui.GREEN));body.addView(heading);Ui.gap(body,8);
        LinearLayout holder=Ui.row(this);holder.setGravity(Gravity.CENTER);LinearLayout preview=Ui.column(this);preview.setPadding(d(12),d(8),d(12),d(8));preview.setBackground(Ui.shape(Ui.SURFACE,18,this));preview.setAlpha(prefs.alpha()/100f);
        holder.addView(preview,new LinearLayout.LayoutParams(Math.min(d(prefs.width()),getResources().getDisplayMetrics().widthPixels-d(48)),-2));body.addView(holder);
        LinearLayout top=Ui.row(this);TextView word=Ui.bold(this,"…",prefs.wordSize(),Ui.INK);TextView badge=Ui.text(this,prefs.intervalLabel()+"s",9,Ui.MUTED);Ui.weighted(top,badge);TextView more=Ui.text(this,L.t("  详情  ●"),11,Ui.GREEN);top.addView(more);preview.addView(top);preview.addView(word,new LinearLayout.LayoutParams(-1,-2));
        TextView reading=Ui.text(this,"",prefs.readingSize(),Ui.GREEN);preview.addView(reading);Ui.gap(preview,4);TextView meaning=Ui.text(this,"",prefs.meaningSize(),Ui.INK);meaning.setMaxLines(prefs.meaningLines());meaning.setEllipsize(android.text.TextUtils.TruncateAt.END);preview.addView(meaning);Ui.gap(preview,8);LinearLayout controls=Ui.row(this);for(String label:new String[]{"‹","Ⅱ",L.t("音"),"☆",L.t("熟"),"›"}){TextView item=Ui.text(this,label,13,Ui.GREEN);item.setGravity(Gravity.CENTER);Ui.weighted(controls,item);}preview.addView(controls);
        TextView note=Ui.text(this,L.t("静态外观示意，不计入学习记录。"),10,Ui.MUTED);Ui.gap(body,5);body.addView(note);
        View.OnClickListener adjust=v->{tab=3;settingsCategory=2;render();};preview.setOnClickListener(adjust);heading.setOnClickListener(adjust);
        int request=searchGeneration;String current=prefs.data.getString("currentId","");Async.IO.execute(()->{if(!alive||request!=searchGeneration)return;StudySession session=StudySession.load(store,prefs);Word selected=null;for(Word w:session.words)if(w.id.equals(current)){selected=w;break;}if(selected==null&&!session.words.isEmpty())selected=session.words.get(0);Word w=selected;handler.post(()->{if(!alive||request!=searchGeneration)return;if(w==null){word.setText(L.t("暂无匹配单词"));meaning.setText(L.t("调整学习范围即可继续"));}else{word.setText(w.word);reading.setText(getString(R.string.reading_grammar,w.reading,w.pitch,Ui.grammar(w)));meaning.setText(prefs.conceal()?L.t("点释义可揭晓答案"):Ui.meaning(w,prefs));badge.setText(getString(R.string.overlay_countdown,w.level,prefs.intervalLabel()));}});});
    }
    private void beginOverlay() {
        if(!Settings.canDrawOverlays(this)) {
            pendingStart=true;
            new ThemedDialog.Builder(this).setTitle(L.t("允许词间悬浮显示"))
                .setMessage(L.t("接下来会打开系统设置。找到「词间」，开启「允许显示在其他应用上层」，然后返回这里。"))
                .setPositiveButton(L.t("去设置"),(dialog,which)->{
                    try {startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY);}
                    catch(ActivityNotFoundException e){pendingStart=false;toast(L.t("请在系统设置中手动允许词间显示悬浮窗"));}
                }).setNegativeButton(L.t("稍后"),(dialog,which)->pendingStart=false).setOnCancelListener(dialog->pendingStart=false).show();
        } else requestNotificationsThenStart();
    }
    private void requestNotificationsThenStart() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);
        else startOverlay();
    }
    private void startOverlay() {
        if(!Settings.canDrawOverlays(this))return;
        try {startForegroundService(new Intent(this,FloatService.class).setAction(prefs.data.getBoolean("session.focused",false)?"FOCUS":"BROWSE"));handler.postDelayed(()->{if(alive)render();},250);}
        catch(RuntimeException e){toast(L.t("无法启动悬浮窗，请返回应用后重试"));}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==NOTIFICATIONS) startOverlay();
    }
    private void library() {
        title(L.t("我的词库"),L.t("每一个词，都算数。"),L.t("按日语、假名或中文查找，随时回看收藏。"));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextSize(15);input.setHint(L.t("搜索日语、假名、罗马字或中文"));input.setText(query);
        input.setBackground(Ui.shape(Ui.SURFACE,14,this));input.setPadding(d(14),d(12),d(14),d(12));body.addView(input,Ui.inputSize(input,52));
        Ui.gap(body,12);
        LinearLayout filters=Ui.row(this);
        String[] labels={L.t("全部"),L.t("收藏"),L.t("已熟悉")},modes={"all","favorites","familiar"};
        for(int i=0;i<3;i++){final String mode=modes[i];TextView filter=Ui.button(this,labels[i],mode.equals(libraryMode),v->{libraryMode=mode;render();});filter.setMinHeight(d(44));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(d(2),0,d(2),0);filters.addView(filter,p);}
        body.addView(filters);Ui.gap(body,14);
        TextView resultHint=Ui.text(this,"",11,Ui.MUTED);body.addView(resultHint);Ui.gap(body,10);
        LinearLayout results=Ui.column(this);body.addView(results);
        Runnable search=()->loadLibrary(results,resultHint);
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();handler.removeCallbacks(search);handler.postDelayed(search,220);}
            public void afterTextChanged(Editable e){}
        });
        LinearLayout ranges=Ui.row(this);
        Spinner levels=new ChoiceSpinner(this),frequencies=new ChoiceSpinner(this);
        String[] levelIds={"ALL","N5","N4","N3","N2","N1"};
        String[] levelLabels={L.t("全部等级"),"N5","N4","N3","N2","N1"};
        levels.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,levelLabels));
        libraryFrequency=Frequency.effective(libraryLevel,libraryFrequency);
        frequencyOptions(frequencies,libraryLevel,libraryFrequency);
        for(int i=0;i<levelIds.length;i++)if(levelIds[i].equals(libraryLevel))levels.setSelection(i,false);
        levels.setContentDescription(L.t("词库等级筛选"));frequencies.setContentDescription(L.t("词库词频筛选"));
        ranges.addView(levels,new LinearLayout.LayoutParams(0,-2,1));ranges.addView(frequencies,new LinearLayout.LayoutParams(0,-2,1));
        body.addView(ranges,1);
        levels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){if(!libraryLevel.equals(levelIds[pos])){libraryLevel=levelIds[pos];libraryFrequency=Frequency.effective(libraryLevel,libraryFrequency);frequencyOptions(frequencies,libraryLevel,libraryFrequency);loadLibrary(results,resultHint);}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        frequencies.setEnabled(Frequency.supported(libraryLevel));
        frequencies.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){String[] options=Frequency.values(libraryLevel);if(pos<options.length&&!libraryFrequency.equals(options[pos])){libraryFrequency=options[pos];loadLibrary(results,resultHint);}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        loadLibrary(results,resultHint);
    }
    private void frequencyOptions(Spinner spinner,String level,String selected){
        String[] values=Frequency.values(level),labels=new String[values.length];int selection=0;
        String effective=Frequency.effective(level,selected);
        for(int i=0;i<values.length;i++){labels[i]=L.t(Frequency.label(values[i]));if(values[i].equals(effective))selection=i;}
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        spinner.setSelection(selection,false);spinner.setEnabled(Frequency.supported(level));
    }
    private void loadLibrary(LinearLayout results,TextView hint) {
        final int request=++searchGeneration;String requestedQuery=query,mode=libraryMode,level=libraryLevel,frequency=libraryFrequency;
        Async.IO.execute(()->{
            List<Word> matches=store.words(level,frequency,requestedQuery,mode,false,false,101);
            handler.post(()->{
                if(!alive||tab!=1||request!=searchGeneration)return;
                results.removeAllViews();
                hint.setText(matches.isEmpty()?L.t("还没有符合条件的词。"):matches.size()>100?L.t("显示前 100 个结果，请输入关键词缩小范围。"):L.t("找到 ")+matches.size()+L.t(" 个词"));
                for(int i=0;i<Math.min(100,matches.size());i++){
                    Word w=matches.get(i);
                    LinearLayout row=Ui.column(this);Ui.pad(row,14);row.setBackground(Ui.shape(Ui.SURFACE,14,this));
                    LinearLayout top=Ui.row(this);Ui.weighted(top,Ui.bold(this,w.word,prefs.appSize(19,0),Ui.INK));top.addView(Ui.text(this,Frequency.badge(w)+(w.favorite?" ★":""),11,Ui.GREEN));row.addView(top);
                    Ui.gap(row,5);row.addView(Ui.text(this,Ui.reading(w)+"  ·  "+Ui.grammar(w),prefs.appSize(12,1),Ui.GREEN));Ui.gap(row,5);
                    TextView meaning=Ui.text(this,Ui.meaning(w,prefs),prefs.appSize(13,2),Ui.MUTED);meaning.setMaxLines(2);row.addView(meaning);
                    row.setOnClickListener(v->wordDialog(w));results.addView(row);Ui.gap(results,8);
                }
            });
        });
    }
    private void wordDialog(Word w) {
        LinearLayout content=Ui.column(this);Ui.pad(content,22);ScrollView scroller=new ScrollView(this);scroller.addView(content);
        content.addView(Ui.heading(this,w.word,prefs.appSize(26,0),Ui.INK));Ui.gap(content,14);
        TextView pitch=Ui.text(this,Frequency.badge(w)+"  ·  "+Ui.reading(w)+"  "+w.pitch+"  ›",prefs.appSize(14,1),Ui.GREEN);pitch.setMinHeight(d(40));pitch.setFocusable(true);pitch.setContentDescription(L.t("查看这个词的音调说明"));pitch.setOnClickListener(v->PitchGuide.show(this,w));content.addView(pitch);Ui.gap(content,14);
        content.addView(Ui.bold(this,L.t("词性 · ")+L.grammar(Grammar.describe(w.pos)),prefs.appSize(14,1),Ui.GREEN));Ui.gap(content,8);
        content.addView(Ui.text(this,L.t("词频 · ")+("NONE".equals(w.frequency)?L.t("原词库未分档"):L.t(Frequency.label(w.frequency))+L.t("（JLPT 真题）")),12,Ui.MUTED));Ui.gap(content,12);
        content.addView(Ui.text(this,Ui.meaning(w,prefs),prefs.appSize(17,2),Ui.INK));Ui.gap(content,18);
        if(!w.example.isEmpty()){
            content.addView(Ui.text(this,w.example,prefs.appSize(17,2),Ui.INK));Ui.gap(content,7);content.addView(Ui.text(this,w.exampleMeaning,prefs.appSize(13,1),Ui.MUTED));
            Ui.gap(content,10);exampleAction=Ui.exampleAction(this,speech::isSpeakingExample,v->{if(speech.isSpeakingExample())speech.stop();else speech.speakExample(w,true);});Ui.exampleActionState(exampleAction,speech.isSpeakingExample());content.addView(exampleAction);Ui.gap(content,12);
        }
        if(!w.note.isEmpty()){content.addView(Ui.text(this,w.note,13,Ui.MUTED));Ui.gap(content,12);}
        content.addView(Ui.button(this,L.t("朗读单词"),false,v->speech.speakWord(w,true)));Ui.gap(content,8);
        TextView[] buttons=new TextView[2],undoView={null};boolean[] saving={false};MarkHistory<Object[]> marks=new MarkHistory<>();Runnable[] sync={null};
        java.util.function.BiConsumer<String,Boolean> apply=(kind,value)->{if(saving[0])return;boolean old=kind.equals("favorite")?w.favorite:w.familiar;saving[0]=true;
            Async.IO.execute(()->{try{if(kind.equals("favorite"))store.favorite(w,value);else store.familiar(w,value);handler.post(()->{saving[0]=false;if(!alive)return;marks.push(new Object[]{kind,old},SystemClock.elapsedRealtime());sync[0].run();handler.postDelayed(()->{if(alive)sync[0].run();},8100);refreshService();});}catch(Exception error){handler.post(()->{saving[0]=false;toast(L.t("标记保存失败，请重试；原记录保留。"));});}});};
        undoView[0]=Ui.button(this,L.t("撤销上次标记"),false,v->{if(saving[0])return;Object[] entry=marks.pop(SystemClock.elapsedRealtime());if(entry==null)return;String kind=(String)entry[0];boolean value=(Boolean)entry[1];saving[0]=true;Async.IO.execute(()->{try{if(kind.equals("favorite"))store.favorite(w,value);else store.familiar(w,value);handler.post(()->{saving[0]=false;if(alive){sync[0].run();refreshService();}});}catch(Exception error){handler.post(()->{saving[0]=false;marks.push(entry,SystemClock.elapsedRealtime());toast(L.t("撤销失败，请重试。"));});}});});undoView[0].setVisibility(View.GONE);
        buttons[0]=Ui.button(this,w.favorite?L.t("★ 已收藏"):L.t("☆ 收藏这个词"),false,v->apply.accept("favorite",!w.favorite));content.addView(buttons[0]);Ui.gap(content,8);
        buttons[1]=Ui.button(this,w.familiar?L.t("移回学习列表"):L.t("标为熟悉"),false,v->apply.accept("familiar",!w.familiar));content.addView(buttons[1]);Ui.gap(content,8);content.addView(undoView[0]);
        sync[0]=()->{buttons[0].setText(w.favorite?L.t("★ 已收藏"):L.t("☆ 收藏这个词"));buttons[1].setText(w.familiar?L.t("移回学习列表"):L.t("标为熟悉"));undoView[0].setVisibility(marks.peek(SystemClock.elapsedRealtime())==null?View.GONE:View.VISIBLE);};
        new ThemedDialog.Builder(this).setView(scroller).setPositiveButton(L.t("完成"),(dialog,which)->{if(tab==1)render();}).show();
    }
    private void refreshService() {
        if(FloatService.running)startService(new Intent(this,FloatService.class).setAction("REFRESH"));
    }
    private void section(String text){Ui.gap(body,24);body.addView(Ui.heading(this,text,16,Ui.INK));Ui.gap(body,14);}
    private static final String[] SETTING_NAMES={"语言与外观","学习内容","悬浮窗","开机与后台","语音与朗读","备份与帮助","关于词间"};
    private static final String[] SETTING_HINTS={"界面语言、浅深模式、主题色与 App 内学习字号","等级词频、浏览目标、收藏熟悉筛选与分组","切词间隔、文字大小、详情高度与拖动锁定","开机自启动、通知、悬浮权限与后台运行","语音检测、引擎、单词例句连读与锁屏听词","分享备份、恢复撤回、教程与日语入门说明","版本信息、词库来源和许可说明"};
    private void settings() {
        if(settingsCategory<0||settingsCategory>=SETTING_NAMES.length){
            settingsCategory=-1;body.addView(Ui.heading(this,L.t("设置"),25,Ui.INK));Ui.gap(body,8);body.addView(Ui.prose(this,L.t("按类别调整，所有功能都在这里。"),13,Ui.MUTED));Ui.gap(body,16);
            EditText find=new EditText(this);find.setSingleLine(true);find.setTextSize(14);find.setHint(L.t("搜索设置，如锁屏、字号、备份"));find.setContentDescription(L.t("搜索设置"));body.addView(find);Ui.gap(body,12);LinearLayout matches=Ui.column(this);body.addView(matches);find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence t,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence t,int a,int b,int c){matches.removeAllViews();String q=TextSearch.normalize(t.toString().trim());if(q.isEmpty())return;int count=0;for(String[] item:SettingIndex.ITEMS)if(TextSearch.normalize(item[1]+" "+L.t(item[1])+" "+item[2]).contains(q)){int category=Integer.parseInt(item[0]);matches.addView(Ui.button(MainActivity.this,L.t(item[1])+" · "+L.t(SETTING_NAMES[category])+"  ›",false,v->{((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(find.getWindowToken(),0);goToSettings(category,L.t(item[1]));}));Ui.gap(matches,7);count++;}if(count==0)matches.addView(Ui.prose(MainActivity.this,L.t("未找到设置，可按下方分类或使用教程查找。"),12,Ui.MUTED));}});
            Ui.gap(body,12);body.addView(Ui.button(this,L.t("使用教程 · 按场景找答案  ›"),false,v->Tutorial.show(this,this::voiceGuide)));Ui.gap(body,16);
            for(int i=0;i<SETTING_NAMES.length;i++){
                final int category=i;LinearLayout item=card(Ui.SURFACE);Ui.pad(item,14);
                item.addView(Ui.bold(this,L.t(SETTING_NAMES[i])+"  ›",17,Ui.INK));Ui.gap(item,6);
                item.addView(Ui.prose(this,L.t(SETTING_HINTS[i]),12,Ui.MUTED));
                item.setContentDescription(L.t("设置分类：")+L.t(SETTING_NAMES[i]));item.setOnClickListener(v->{settingsCategory=category;render();});Ui.gap(body,10);
            }
            return;
        }
        TextView back=Ui.text(this,L.t("‹ 全部设置"),14,Ui.GREEN);back.setPadding(0,d(8),0,d(8));back.setMinHeight(d(36));back.setFocusable(true);back.setOnClickListener(v->{settingsCategory=-1;render();});body.addView(back);Ui.gap(body,8);
        title(L.t("设置"),L.t(SETTING_NAMES[settingsCategory]),L.t(SETTING_HINTS[settingsCategory]));
        switch(settingsCategory){
            case 0:settings0();break;
            case 1:settings1();break;
            case 2:settings2();break;
            case 3:settings3();break;
            case 4:settings4();break;
            case 5:settings5();break;
            case 6:settings6();break;
        }
        if(settingsCategory<SettingIndex.RESET.length){Ui.gap(body,24);body.addView(Ui.button(this,L.t("恢复本页默认设置"),false,v->new ThemedDialog.Builder(this).setTitle(L.t("恢复本页默认设置？")).setMessage(L.t("只恢复「")+L.t(SETTING_NAMES[settingsCategory])+L.t("」的应用内选项。学习记录、收藏、自选词清单和其他页面保持不变，系统权限不受影响。")+(settingsCategory==0?L.t("\n\n界面语言保持不变，只恢复本页的外观选项。"):"")).setPositiveButton(L.t("恢复默认"),(d,w)->{SharedPreferences.Editor edit=prefs.data.edit();for(String key:SettingIndex.RESET[settingsCategory])edit.remove(key);if(edit.commit())recreate();else toast(L.t("设置未能保存，请重试"));}).setNegativeButton(L.t("取消"),null).show()));}
    }
    void goToSettings(int category){goToSettings(category,"");}
    private void goToSettings(int category,String anchor){tab=3;settingsCategory=category;settingsAnchor=anchor;if(category==4)voiceAdvanced=true;render();}
    private void scrollToSetting(){if(settingsAnchor.isEmpty())return;String anchor=settingsAnchor;settingsAnchor="";scroll.post(()->{View target=findSetting(body,anchor);if(target!=null){android.graphics.Rect bounds=new android.graphics.Rect();target.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(target,bounds);scroll.smoothScrollTo(0,Math.max(0,bounds.top-d(12)));}});}
    private View findSetting(View view,String anchor){if(view instanceof TextView&&((TextView)view).getText().toString().contains(anchor))return view;if(view.getContentDescription()!=null&&view.getContentDescription().toString().contains(anchor))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findSetting(group.getChildAt(i),anchor);if(found!=null)return found;}}return null;}
    @Override public void onBackPressed(){
        if(tab==3&&settingsCategory>=0){settingsCategory=-1;render();return;}
        super.onBackPressed();
    }
    private void settings0() {
        body.addView(Ui.button(this,L.t("App 内学习字号")+" · "+prefs.appWordSize()+" / "+prefs.appReadingSize()+" / "+prefs.appMeaningSize()+"  ›",false,v->FontPicker.showApp(this,prefs,this::render)));Ui.gap(body,12);

        body.addView(Ui.heading(this,"界面语言 / Language / 言語",15,Ui.INK));Ui.gap(body,8);
        String[] languageIds=UiLanguage.IDS;Spinner languages=new ChoiceSpinner(this);languages.setContentDescription(L.t("界面语言"));languages.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,UiLanguage.NAMES));
        String selected=UiLanguage.resolve(prefs.data.getString("uiLanguage",null),android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0));for(int i=0;i<languageIds.length;i++)if(languageIds[i].equals(selected))languages.setSelection(i,false);body.addView(languages);
        languages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int position,long id){if(!selected.equals(languageIds[position])){FloatService.stopForRestore();prefs.put("uiLanguage",languageIds[position]);L.init(MainActivity.this);recreate();}}public void onNothingSelected(AdapterView<?> a){}});
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("界面语言只改变菜单和说明；日语词条与中文释义保持原样。"),12,Ui.MUTED));Ui.gap(body,18);

        String[] themeIds={"AUTO","LIGHT","DARK"},themeLabels={L.t("跟随系统"),L.t("浅色"),L.t("深色")};
        Spinner theme=new ChoiceSpinner(this);theme.setContentDescription(L.t("外观模式"));theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,themeLabels));
        for(int i=0;i<themeIds.length;i++)if(themeIds[i].equals(prefs.theme()))theme.setSelection(i,false);
        body.addView(theme,new LinearLayout.LayoutParams(-1,-2));
        theme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(!prefs.theme().equals(themeIds[position])){prefs.put("theme",themeIds[position]);recreate();}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        Ui.gap(body,18);body.addView(Ui.text(this,L.t("主题颜色"),14,Ui.INK));
        body.addView(Ui.button(this,L.t(Accent.LABELS[Accent.index(prefs.accent())])+"  ›",false,v->ThemePicker.show(this,prefs,this::customColorDialog,this::recreate)));
        Ui.gap(body,8);body.addView(Ui.button(this,L.t("输入自定义色号"),false,v->customColorDialog()));
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("颜色同时用于 App 与悬浮窗。自定义色会在浅／深色下调整深浅，以保持文字可读。"),12,Ui.MUTED));
    }
    private void customColorDialog(){
        LinearLayout content=Ui.column(this);Ui.pad(content,20);content.addView(Ui.text(this,L.t("输入六位颜色，例如 #2F80ED。"),13,Ui.INK));
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(prefs.customColor());input.setContentDescription(L.t("自定义颜色色号"));content.addView(input,Ui.inputSize(input,48));Ui.gap(content,12);
        View swatch=new View(this);swatch.setBackgroundColor(Accent.parse(prefs.customColor()));content.addView(swatch,new LinearLayout.LayoutParams(-1,d(48)));Ui.tint(content);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){try{swatch.setBackgroundColor(Accent.parse(s.toString()));}catch(IllegalArgumentException ignored){}}});
        AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("自定义主题颜色")).setView(content).setPositiveButton(L.t("应用"),null).setNegativeButton(L.t("取消"),(d,w)->render()).setOnCancelListener(d->render()).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String color=Accent.hex(Accent.parse(input.getText().toString()));prefs.data.edit().putString("customColor",color).putString("accent","CUSTOM").apply();dialog.dismiss();recreate();}catch(IllegalArgumentException invalid){input.setError(L.t(invalid.getMessage()));}}));dialog.show();
    }
    private void settings1() {
        String[] levels={"N5","N4","N3","N2","N1","ALL"},names=new String[6];
        for(int i=0;i<levels.length;i++)names[i]=(levels[i].equals("ALL")?L.t("全部"):levels[i])+" · "+(levels[i].equals("ALL")?store.scalar("SELECT count(*) FROM words"):store.scalar("SELECT count(*) FROM words WHERE level=?",levels[i]))+L.t(" 词");
        section(L.t("词表范围"));body.addView(Ui.bold(this,L.t("当前等级"),13,Ui.MUTED));Ui.gap(body,6);
        Spinner spinner=new ChoiceSpinner(this);spinner.setContentDescription(L.t("学习等级"));
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        int selected=0;for(int i=0;i<levels.length;i++)if(levels[i].equals(prefs.level()))selected=i;
        spinner.setSelection(selected,false);spinner.setEnabled(!prefs.groupMode().equals("CUSTOM"));body.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(body,14);body.addView(Ui.bold(this,L.t("词频范围"),13,Ui.MUTED));Ui.gap(body,6);
        Spinner frequency=new ChoiceSpinner(this);frequency.setContentDescription(L.t("学习词频"));
        frequencyOptions(frequency,prefs.level(),prefs.frequency());
        frequency.setEnabled(Frequency.supported(prefs.level())&&!prefs.groupMode().equals("CUSTOM"));
        body.addView(frequency,new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(body,6);TextView frequencyHint=Ui.prose(this,Frequency.supported(prefs.level())?L.t("高／中／低频来自原词库的 JLPT 真题分档；「未分档」为 N4/N5 无词频标记的词，不代表低频。顺序模式保留作者排序。"):L.t("N4、N5 原词库未分词频，自动使用全部词条。"),12,Ui.MUTED);body.addView(frequencyHint);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View v,int position,long id){
                if(!prefs.level().equals(levels[position])){
                    String nextLevel=levels[position],nextFrequency=Frequency.effective(nextLevel,prefs.frequency());
                    prefs.data.edit().putString("level",nextLevel).putString("frequency",nextFrequency).apply();
                    frequencyOptions(frequency,nextLevel,nextFrequency);
                }
                boolean supported=Frequency.supported(levels[position]);frequency.setEnabled(supported&&!prefs.groupMode().equals("CUSTOM"));
                frequencyHint.setText(supported?L.t("高／中／低频来自原词库的 JLPT 真题分档；「未分档」为 N4/N5 无词频标记的词，不代表低频。顺序模式保留作者排序。"):L.t("N4、N5 原词库未分词频，自动使用全部词条。"));
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
        frequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String[] options=Frequency.values(prefs.level());if(position<options.length&&!Frequency.effective(prefs.level(),prefs.frequency()).equals(options[position]))prefs.put("frequency",options[position]);}
            public void onNothingSelected(AdapterView<?> parent){}
        });
        Ui.gap(body,18);body.addView(Ui.bold(this,L.t("学习来源"),13,Ui.MUTED));Ui.gap(body,6);
        Spinner source=new ChoiceSpinner(this);source.setContentDescription(L.t("学习来源"));
        String[] modeIds={"all","favorites","familiar"},modeNames={L.t("全部词"),L.t("只看收藏"),L.t("只复习熟悉词")};
        source.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modeNames));
        for(int i=0;i<modeIds.length;i++)if(modeIds[i].equals(prefs.studyMode()))source.setSelection(i,false);
        body.addView(source,new LinearLayout.LayoutParams(-1,-2));
        Ui.gap(body,6);TextView reviewHint=Ui.prose(this,L.t("熟悉词复习会忽略「跳过熟悉词」；等级与词频仍然生效，自选组可跨等级。"),12,Ui.MUTED);body.addView(reviewHint);
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(!prefs.studyMode().equals(modeIds[pos]))prefs.put("studyMode",modeIds[pos]);}
            public void onNothingSelected(AdapterView<?> p){}
        });
        section(L.t("浏览目标"));
        toggle(L.t("显示学习进度"),L.t("控制首页浏览进度；隐藏后也能在首页恢复"),"showFirstPass",prefs.showFirstPass());
        Ui.gap(body,6);body.addView(Ui.button(this,L.t("每天新接触目标 · ")+prefs.dailyNewTarget()+L.t(" 词"),false,v->dailyGoalDialog()));
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("这个目标只计算第一次接触，不限制轮换。正式记忆任务的新学目标在「复习」页单独设置。"),12,Ui.MUTED));
        section(L.t("浏览方式"));
        toggle(L.t("随机顺序"),L.t("整轮不重复，保存轮次，并尽量避开最近 20 个词"),"shuffle",prefs.shuffle());
        toggle(L.t("跳过熟悉词"),L.t("熟悉是手动标记；可在词库随时移回学习列表"),"hideFamiliar",prefs.hideFamiliar());
        toggle(L.t("先回忆，再看答案"),L.t("释义默认隐藏，轻点即可揭晓"),"conceal",prefs.conceal());
        toggle(L.t("繁体中文释义"),L.t("词条说明与例句翻译保留原始简体"),"traditional",prefs.traditional());
        groupSettings();
    }
    private void settings2() {
        toggle(L.t("锁定悬浮窗位置"),L.t("锁定后仍可点击、翻词和滚动；关闭此项即可重新拖动"),"positionLocked",prefs.positionLocked());
        body.addView(Ui.text(this,L.t("自动切词间隔（秒）"),14,Ui.INK));
        EditText interval=new EditText(this);interval.setSingleLine(true);interval.setTextSize(15);interval.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        interval.setText(prefs.intervalLabel());interval.setHint(L.t("例如 0.5、1.25、15"));interval.setContentDescription(L.t("自定义切词秒数"));
        body.addView(interval,Ui.inputSize(interval,48));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("保存切词间隔"),false,v->{
            try{
                long millis=Interval.parseMillis(interval.getText().toString());
                String value=Interval.format(millis);prefs.put("intervalSeconds",value);interval.setText(value);
                interval.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(interval.getWindowToken(),0);
                toast(L.t("切词间隔已设为 ")+value+L.t(" 秒"));
            }catch(IllegalArgumentException error){interval.setError(L.t(error.getMessage()));}
        }));
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("支持 0.1～3600 秒、最多三位小数。朗读尚未结束时，会等读完再切词。"),12,Ui.MUTED));Ui.gap(body,16);
        body.addView(Ui.button(this,L.t("文字大小 · ")+prefs.wordSize()+" / "+prefs.readingSize()+" / "+prefs.meaningSize()+"  ›",false,v->FontPicker.show(this,prefs,this::render)));Ui.gap(body,12);
        slider(L.t("详情内容高度"),"detailHeight",100,420,10,prefs.detailHeight()," dp");
        body.addView(Ui.prose(this,L.t("调整可滚动详情区的高度；屏幕不足时自动缩短，单词与底部操作保持可达。"),12,Ui.MUTED));Ui.gap(body,12);
        slider(L.t("窗口宽度"),"width",220,360,10,prefs.width()," dp");
        slider(L.t("释义显示行数"),"meaningLines",1,3,1,prefs.meaningLines(),L.t(" 行"));
        toggle(L.t("松手后自动贴边"),L.t("关闭时停在手指松开的位置"),"snapEdges",prefs.snapEdges());
        slider(L.t("窗口不透明度"),"alpha",45,100,5,prefs.alpha()," %");
        body.addView(Ui.prose(this,L.t("紧凑模式始终显示词性，例句只在详情里显示。\n窗口会限制在屏幕内；大字体或长单词可能增加高度。"),12,Ui.MUTED));
    }
    private void settings3() {
        body.addView(Ui.button(this,L.t("运行状态检查  ›"),false,v->Diagnostics.show(this,speech,this::voiceGuide)));Ui.gap(body,14);
        toggle(L.t("开机自动开启悬浮窗"),L.t("开机出现窗口后先暂停，点播放开始；仍需系统允许自启动"),"autoStart",prefs.autoStart());
        body.addView(Ui.prose(this,L.t("不同品牌的菜单名称可能不同。请在系统「应用信息」或「电池」中查找自启动、后台运行及电池优化设置，并允许词间运行。应用内开关不能代替系统权限；被强行停止后，需先手动打开一次。"),12,Ui.MUTED));
        Ui.gap(body,12);body.addView(Ui.button(this,L.t("打开系统应用设置"),false,v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));
        String bootStatus=L.status(prefs.status("boot.status"));
        if(!bootStatus.isEmpty()){Ui.gap(body,8);body.addView(Ui.text(this,bootStatus,11,Ui.MUTED));}
    }
    private void settings4() {
        toggle(L.t("手动翻词时朗读"),L.t("点左右箭头后读当前词，暂停轮换时也生效；默认开启"),"speakOnFlip",prefs.speakOnFlip());
        toggle(L.t("复习时自动朗读"),L.t("回忆卡片读词；反向回忆揭晓后才读日语。"),"reviewAutoSpeak",prefs.reviewAutoSpeak());
        Switch autoRead=toggle(L.t("自动轮换时朗读"),L.t("首次开启、自动切词及恢复播放时朗读；与手动翻词分开控制"),"autoSpeak",prefs.autoSpeak());
        Switch ballAudio=toggle(L.t("收球后继续听"),L.t("开启时同时启用自动朗读；没有可用日语语音时停留"),"ballAudio",prefs.ballAudio());
        Switch lockAudio=toggle(L.t("锁屏后继续听"),L.t("默认关闭；启用后会在息屏时维持语音学习"),"lockAudio",prefs.lockAudio());
        ballAudio.setOnCheckedChangeListener((button,value)->{prefs.put("ballAudio",value);if(value)autoRead.setChecked(true);});
        lockAudio.setOnCheckedChangeListener((button,value)->{prefs.put("lockAudio",value);if(value)autoRead.setChecked(true);});
        autoRead.setOnCheckedChangeListener((button,value)->{prefs.put("autoSpeak",value);if(!value){ballAudio.setChecked(false);lockAudio.setChecked(false);}});
        slider(L.t("单词朗读次数"),"wordRepeats",1,5,1,prefs.wordRepeats(),L.t(" 次"));
        toggle(L.t("单词后连读例句"),L.t("不打开详情也能连读；没有例句的词会跳过"),"readExample",prefs.readExample());
        slider(L.t("例句朗读次数"),"exampleRepeats",1,5,1,prefs.exampleRepeats(),L.t(" 次"));
        toggle(L.t("朗读中文译文"),L.t("每轮先读日语再读中文；引擎不支持中文时跳过"),"readChinese",prefs.readChinese());
        slider(L.t("朗读语速"),"speechRate",50,150,5,Math.round(prefs.speechRate()*100)," %");
        slider(L.t("每段朗读后停顿"),"speechGap",0,3000,100,prefs.speechGap(),L.t(" 毫秒"));
        body.addView(Ui.prose(this,L.t("语速和停顿从下一段生效。耳机断开、来电或音频焦点被其他应用取得时暂停，需手动继续。"),11,Ui.MUTED));
        body.addView(Ui.prose(this,L.t("单词和例句分别按设置次数循环；开启中文后，每轮为「日语 → 中文」。切词间隔是最短停留时间，尚未朗读完会等待；手动暂停会停止声音；收球和锁屏是否继续，由上方开关决定。"),12,Ui.MUTED));Ui.gap(body,10);
        voiceStatus=Ui.text(this,speech.status(),12,Ui.MUTED);body.addView(voiceStatus);Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("试读日语"),false,v->speech.speak("こんにちは。日本語の音声を確認します。",true)));Ui.gap(body,8);body.addView(Ui.button(this,L.t("试读中文"),false,v->speech.testChinese()));Ui.gap(body,8);body.addView(Ui.prose(this,L.t("离线能力是引擎报告的信息。请在断网状态分别试读确认；本应用不会自动更换引擎中的声音。"),12,Ui.MUTED));
        body.addView(Ui.button(this,L.t("发音设置指引 · 无 Google Play 也可查看"),false,v->voiceGuide()));Ui.gap(body,8);
        fold(L.t("高级发音设置"),voiceAdvanced,open->voiceAdvanced=open,()->{
        java.util.List<Speech.Engine> engines=Speech.engines(this);
        String[] engineIds=new String[engines.size()+1],engineLabels=new String[engines.size()+1];engineIds[0]="AUTO";engineLabels[0]=L.t("自动选择支持日语的引擎");
        for(int i=0;i<engines.size();i++){engineIds[i+1]=engines.get(i).id;engineLabels[i+1]=engines.get(i).label;}
        Spinner engine=new ChoiceSpinner(this);engine.setContentDescription(L.t("日语语音引擎"));engine.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,engineLabels));
        for(int i=0;i<engineIds.length;i++)if(engineIds[i].equals(prefs.data.getString("voiceEngine","AUTO")))engine.setSelection(i,false);
        body.addView(engine,new LinearLayout.LayoutParams(-1,-2));
        engine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(!engineIds[position].equals(prefs.data.getString("voiceEngine","AUTO"))){prefs.put("voiceEngine",engineIds[position]);speech.refresh();}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        body.addView(Ui.button(this,L.t("检查日语发音"),false,v->speech.speak("こんにちは。日本語の音声です。",true)));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("系统日语语音设置"),false,v->{
            try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(ActivityNotFoundException e){toast(L.t("请打开系统设置，搜索「文字转语音」"));}
        }));
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("不内置录音。使用已安装的日语 TTS 引擎；自动模式会尝试 Google 或其他兼容引擎，不修改系统全局默认设置。"),12,Ui.MUTED));
        });
    }
    private void settings5() {
        body.addView(Ui.button(this,L.t("备份学习记录"),false,v->backupOptions()));Ui.gap(body,8);
        String last=L.status(prefs.status("backup.last"));if(!last.isEmpty()){body.addView(Ui.prose(this,last,11,Ui.MUTED));Ui.gap(body,8);}
        TextView reminder=Ui.prose(this,L.status(BackupReminder.message(System.currentTimeMillis(),prefs.data.getLong("backup.savedAt",0),prefs.data.getLong("backup.generatedAt",0),store.scalar("SELECT count(*) FROM progress WHERE seen>0 OR favorite=1 OR familiar=1")>0)),12,Ui.MUTED);body.addView(reminder);Ui.gap(body,16);
        body.addView(Ui.button(this,L.t("恢复学习记录"),false,v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){toast(L.t("未找到文件选择器"));}
        }));Ui.gap(body,8);
        if(RestoreSafety.available(this)){body.addView(Ui.button(this,L.t("撤回上次导入 · 返回导入前"),false,v->undoRestore()));Ui.gap(body,8);}else{body.addView(Ui.prose(this,L.t("撤回上次导入：当前没有本机恢复快照，确认导入后才会生成。"),12,Ui.MUTED));Ui.gap(body,8);}
        body.addView(Ui.button(this,L.t("备份内容与恢复规则"),false,v->backupGuide()));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("零基础 · 五十音图"),false,v->KanaGuide.show(this,speech)));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("音调标记说明"),false,v->PitchGuide.show(this,null)));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("词性标记说明"),false,v->grammarGuide()));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("后台运行设置"),false,v->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException e){toast(L.t("请在系统设置中打开词间的应用信息"));}
        }));Ui.gap(body,8);
        body.addView(Ui.prose(this,L.t("若悬浮窗被系统关闭，可在应用信息中允许后台运行、取消电池限制。若自动启动受系统限制，可手动打开词间恢复；也可在「开机与后台」中检查设置。"),12,Ui.MUTED));
    }
    private void settings6() {
        body.addView(Ui.prose(this,Ui.version(this)+L.t(" · 个人使用\n无账号、无广告、无联网权限。\n学习记录仅保存在本机；卸载前请先备份。"),12,Ui.MUTED));Ui.gap(body,12);
        String version=store.provenance();try{org.json.JSONObject meta=new org.json.JSONObject(version);body.addView(Ui.text(this,L.t("词库版本 · ")+meta.optString("commit","").substring(0,Math.min(8,meta.optString("commit","").length())),12,Ui.MUTED));}catch(org.json.JSONException ignored){}
        String deckStatus=L.status(prefs.status("deck.status"));if(!deckStatus.isEmpty())body.addView(Ui.text(this,deckStatus,12,Ui.MUTED));Ui.gap(body,8);
        body.addView(Ui.button(this,L.t("词库来源与授权"),false,v->{
            LinearLayout about=Ui.column(this);Ui.pad(about,22);
            TextView copy=Ui.text(this,L.t("词库：egg rolls · JLPT N1–N5\n词条数量以本机词库为准 · CC BY-NC 4.0\n\n来源：https://github.com/5mdld/anki-jlpt-decks\n\n本应用将 Anki 字段转换为本地词库，去除了显示用 HTML 与音频标记，保留简繁释义、原词性、音调和第一条例句。发音改用系统 TTS，不包含原始音频。\n\n词库仅作个人非商业学习使用。JLPT 分级及词性以原词库为准。"),13,Ui.INK);copy.setTextIsSelectable(true);about.addView(copy);
            new ThemedDialog.Builder(this).setTitle(L.t("感谢 egg rolls")).setView(about).setPositiveButton(L.t("查看原项目"),(a,b)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/5mdld/anki-jlpt-decks")))).setNegativeButton(L.t("关闭"),null).show();
        }));
    }
    private void nextGroup(){Async.IO.execute(()->{StudySession.next(store,prefs);handler.post(()->{if(alive){toast(L.t("已切换到下一组"));render();}});});}
    private void groupSettings(){
        section(L.t("分组循环"));
        String[] ids={"OFF","SIZE","CUSTOM"},names={L.t("不分组 · 全范围循环"),L.t("每组固定词数循环"),L.t("自选单词循环（可跨等级）")};
        Spinner group=new ChoiceSpinner(this);group.setContentDescription(L.t("分组方式"));group.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        for(int i=0;i<ids.length;i++)if(ids[i].equals(prefs.groupMode()))group.setSelection(i,false);
        body.addView(group,new LinearLayout.LayoutParams(-1,-2));
        group.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(!prefs.groupMode().equals(ids[pos])){prefs.put("groupMode",ids[pos]);render();}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        if(prefs.groupMode().equals("SIZE")){
            EditText count=new EditText(this);count.setSingleLine(true);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setText(String.valueOf(prefs.groupSize()));count.setContentDescription(L.t("每组词数"));body.addView(count,Ui.inputSize(count,48));
            body.addView(Ui.button(this,L.t("保存每组词数"),false,v->{
                try{int n=Integer.parseInt(count.getText().toString().trim());if(n<1||n>10641)throw new NumberFormatException();prefs.put("groupSize",n);count.clearFocus();render();toast(L.t("每组 ")+n+L.t(" 词"));}
                catch(NumberFormatException invalid){count.setError(L.t("请输入 1～10641 的整数"));}
            }));Ui.gap(body,8);
            TextView status=Ui.text(this,L.t("正在读取分组…"),12,Ui.GREEN);body.addView(status);
            Async.IO.execute(()->{StudySession session=StudySession.load(store,prefs);handler.post(()->{if(alive)status.setText(session.label);});});
            Ui.gap(body,8);body.addView(Ui.button(this,L.t("换下一组"),false,v->nextGroup()));Ui.gap(body,8);
            body.addView(Ui.button(this,L.t("按当前筛选重新分组"),false,v->{ScopeMemory.group(prefs,StudySession.baseScope(prefs),"");prefs.put("groupRevision",prefs.data.getInt("groupRevision",0)+1);render();}));
        }
        if(prefs.groupMode().equals("CUSTOM")){
            body.addView(Ui.text(this,L.t("已选 ")+prefs.customWords().size()+L.t(" 个词；等级和词频由你的选择决定。学习来源与跳过熟悉词仍然生效。"),12,Ui.GREEN));Ui.gap(body,8);
            body.addView(Ui.button(this,L.t("编辑自选组"),false,v->customGroupPicker()));
        }
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("当前组会反复循环，手动换组才进入下一组，最后一组可少于指定词数。标为熟悉后可从当前组排除，不会悄悄补进新词；全组学完可换组。随机只改变组内顺序。"),12,Ui.MUTED));
    }
    private void customGroupPicker(){
        java.util.LinkedHashSet<String> selected=new java.util.LinkedHashSet<>(prefs.customWords());
        LinearLayout content=Ui.column(this);Ui.pad(content,16);
        TextView total=Ui.text(this,L.t("已选 ")+selected.size()+L.t(" 词"),13,Ui.GREEN);content.addView(total);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint(L.t("搜索日语、假名、罗马字或中文"));search.setContentDescription(L.t("自选组搜索"));content.addView(search,Ui.inputSize(search,48));
        Spinner level=new ChoiceSpinner(this);String[] levels={"ALL","N5","N4","N3","N2","N1"};
        level.setContentDescription(L.t("自选组等级"));level.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{L.t("全部等级"),"N5","N4","N3","N2","N1"}));content.addView(level,new LinearLayout.LayoutParams(-1,-2));
        Switch onlySelected=new Switch(this);onlySelected.setText(L.t("只看已选（不受搜索和等级限制）"));onlySelected.setTextColor(Ui.INK);onlySelected.setTextSize(12);content.addView(onlySelected);
        TextView pageHint=Ui.text(this,"",11,Ui.MUTED);content.addView(pageHint);
        ScrollView listScroll=new ScrollView(this);LinearLayout list=Ui.column(this);listScroll.addView(list);content.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pagination=Ui.row(this);content.addView(pagination);
        int[] page={0},generation={0};boolean[] open={true};Runnable[] load={null};
        load[0]=()->{
            int request=++generation[0];String filter=search.getText().toString(),lev=levels[level.getSelectedItemPosition()];boolean checked=onlySelected.isChecked();
            java.util.Set<String> selection=new java.util.HashSet<>(selected);
            Async.IO.execute(()->{
                List<Word> all=store.words(checked?"ALL":lev,"ALL",checked?"":filter,"all",false,false,0);
                if(checked)all.removeIf(w->!selection.contains(w.id));
                handler.post(()->{
                    if(!alive||!open[0]||request!=generation[0])return;
                    int pages=Math.max(1,(all.size()+49)/50);page[0]=Math.min(page[0],pages-1);list.removeAllViews();listScroll.scrollTo(0,0);
                    pageHint.setText(all.isEmpty()?L.t("没有匹配单词"):L.t("找到 ")+all.size()+L.t(" 词 · 第 ")+(page[0]+1)+" / "+pages+L.t(" 页"));
                    for(int i=page[0]*50;i<Math.min(all.size(),(page[0]+1)*50);i++){
                        Word w=all.get(i);CheckBox box=new CheckBox(this);box.setText(getString(R.string.custom_word,w.word,w.reading,w.level,w.meaning));box.setTextColor(Ui.INK);box.setTextSize(13);box.setMaxLines(3);box.setMinHeight(d(60));box.setChecked(selected.contains(w.id));
                        box.setOnCheckedChangeListener((b,value)->{if(value)selected.add(w.id);else selected.remove(w.id);total.setText(getString(R.string.selected_words,selected.size()));});list.addView(box);
                    }
                    pagination.removeAllViews();
                    Ui.weighted(pagination,Ui.button(this,L.t("上一页"),false,v->{if(page[0]>0){page[0]--;load[0].run();}}));
                    Ui.weighted(pagination,Ui.button(this,L.t("下一页"),false,v->{if(page[0]+1<pages){page[0]++;load[0].run();}}));
                });
            });
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){page[0]=0;handler.removeCallbacks(load[0]);handler.postDelayed(load[0],200);}});
        level.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){page[0]=0;load[0].run();}public void onNothingSelected(AdapterView<?> p){}});
        onlySelected.setOnCheckedChangeListener((b,value)->{page[0]=0;load[0].run();});
        AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("自选循环组")).setView(content).setPositiveButton(L.t("保存并使用"),null).setNegativeButton(L.t("取消"),null).setNeutralButton(L.t("清空勾选"),null).create();
        dialog.setOnDismissListener(d->{open[0]=false;handler.removeCallbacks(load[0]);});
        dialog.setOnShowListener(d->{
            dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.9));Ui.tint(content);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(selected.isEmpty()){toast(L.t("请至少选择一个词"));return;}
                prefs.data.edit().putString("customWords",String.join("\n",selected)).putString("groupMode","CUSTOM").apply();dialog.dismiss();render();toast(L.t("自选组已保存，共 ")+selected.size()+L.t(" 词"));
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{selected.clear();total.setText(L.t("已选 0 词"));load[0].run();});load[0].run();
        });dialog.show();
    }
    private void backupGuide(){
        new ThemedDialog.Builder(this).setTitle(L.t("完整备份如何工作"))
            .setMessage(L.t("可以分享 JSON 文件给微信、QQ、邮件或云盘，也能一键保存到「下载 / Cijian」，或者自选位置。分享面板由你选择应用和接收者；生成文件不代表已经发出。没有自动云备份。\n\n新版包含：收藏、熟悉标记、浏览记录、首次遇见日期、回忆复习记录、易忘词表现、设置、自选组、分组与随机队列位置、每日目标。\n\n恢复规则：\n• 收藏／熟悉带有修改时间时采用较新的状态；没有时间的旧标记按预览中的冲突选项处理。\n• 浏览次数和最后浏览时间取较大值，不相加；每天的记录去重。\n• 首次遇见保留更早日期；复习采用较新的一次。\n• 易忘词采用较新状态，旧版备份没有此项时保留本机。\n• 可只恢复学习记录；勾选同时恢复设置后，才导入设置、自选组和浏览位置。\n\n不含设置的旧备份保留本机设置。恢复前自动保存一份本机快照，可撤回最近一次导入；撤回也会撤回导入后的新增记录。未完成复习批次不随文件迁移，恢复后重新安排。暂时不在新词库中的词条记录仍保留，以备重新加入。\n\n不包含系统权限、语音引擎应用及语音数据；悬浮窗需手动开启。词库内容仍随安装包提供。"))
            .setPositiveButton(L.t("明白了"),null).show();
    }
    private void statisticsGuide(){
        new ThemedDialog.Builder(this).setTitle(L.t("学习统计说明")).setMessage(L.t("今日遇见 · 全库：今天在整个词库中看过或进入音频学习的不同词，回看旧词也算；同一天同一词只计一次。\n\n本范围今日初见：当前学习范围中，今天才第一次接触的词，用来计算浏览新词目标。它通常小于今日遇见。\n\n手动熟悉：你自己标记的状态，不是自动判定已经掌握。\n间隔回忆较稳：正式复习至少到第三阶段，且最近一次评分为记得。之后仍需复习。"))
            .setPositiveButton(L.t("明白了"),null).show();
    }
    private void chooseStyle(String style){if(!alive||tab!=0||prefs.learningStyle().equals(style))return;prefs.put("learningStyle",style);render();}
    private String homeKey(){return java.time.LocalDate.now()+"|"+StudySession.baseScope(prefs)+"|"+prefs.dailyNewTarget();}
    private void refreshHome(TextView[] counts,java.util.function.Consumer<WordStore.HomeProgress> progress,java.util.function.Consumer<StudyTasks> tasksUpdate){
        String key=homeKey();if(cachedHome!=null&&key.equals(cachedHomeKey)){for(int i=0;i<3;i++)counts[i].setText(String.valueOf(cachedHome.all[i]));progress.accept(cachedHome);}if(cachedTasks!=null&&key.equals(cachedTasksKey))tasksUpdate.accept(cachedTasks);
        int request=searchGeneration;Runnable[] tick={null};tick[0]=()->{
            if(!alive||!foreground||tab!=0||request!=searchGeneration)return;
            String requestedKey=homeKey();Async.IO.execute(()->{if(!alive||request!=searchGeneration)return;List<Word> range=StudySession.range(store,prefs);WordStore.HomeProgress snapshot=store.homeProgress(range,prefs.dailyNewTarget());StudyTasks tasks=store.tasks(range,System.currentTimeMillis());handler.post(()->{
                if(!alive||!foreground||tab!=0||request!=searchGeneration)return;if(!requestedKey.equals(homeKey())){tick[0].run();return;}
                cachedHome=snapshot;cachedHomeKey=requestedKey;cachedTasks=tasks;cachedTasksKey=requestedKey;
                for(int i=0;i<3;i++)counts[i].setText(String.valueOf(snapshot.all[i]));progress.accept(snapshot);tasksUpdate.accept(tasks);handler.postDelayed(tick[0],4000);
            });});
        };tick[0].run();
    }
    private java.util.function.Consumer<WordStore.HomeProgress> firstPassCard(){
        LinearLayout panel=card(Ui.SURFACE);Ui.pad(panel,14);LinearLayout row=Ui.row(this);Ui.weighted(row,Ui.heading(this,L.t("浏览进度"),17,Ui.INK));row.addView(Ui.text(this,L.t("调整目标 ›"),12,Ui.GREEN));panel.addView(row);Ui.gap(panel,7);
        String scope=prefs.groupMode().equals("CUSTOM")?L.t("自选范围"):(prefs.level().equals("ALL")?L.t("全部等级"):prefs.level())+" · "+L.t(Frequency.label(Frequency.effective(prefs.level(),prefs.frequency())));
        panel.addView(Ui.prose(this,scope+(prefs.studyMode().equals("favorites")?L.t(" · 收藏词"):prefs.studyMode().equals("familiar")?L.t(" · 手动熟悉词"):""),12,Ui.GREEN));Ui.gap(panel,7);
        LinearLayout coverage=Ui.row(this);TextView number=Ui.heading(this,"—",27,Ui.INK);coverage.addView(number);TextView total=Ui.text(this,"",13,Ui.MUTED);total.setPadding(d(8),d(4),0,0);Ui.weighted(coverage,total);TextView percent=Ui.bold(this,"",13,Ui.GREEN);coverage.addView(percent);panel.addView(coverage);Ui.gap(panel,6);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.PALE));panel.addView(bar,new LinearLayout.LayoutParams(-1,d(6)));Ui.gap(panel,6);
        TextView today=Ui.prose(this,L.t("正在计算…"),13,Ui.INK);panel.addView(today);Ui.gap(panel,6);TextView estimate=Ui.prose(this,"",11,Ui.MUTED);panel.addView(estimate);panel.setOnClickListener(v->dailyGoalDialog());panel.setContentDescription(L.t("浏览进度，点击调整每日目标"));
        return snapshot->{FirstPass pass=snapshot.pass;number.setText(String.valueOf(pass.encountered));total.setText(getString(R.string.first_pass_total,pass.total));percent.setText(getString(R.string.first_pass_percent,pass.percent()));bar.setProgress(pass.percent());today.setText(getString(R.string.first_pass_today,snapshot.rangeToday,pass.today,pass.target));estimate.setText(pass.total==0?L.t("当前范围没有词，可调整筛选。"):pass.remaining()==0?L.t("这个范围已浏览一遍，可以继续回看或进入复习。"):getString(R.string.first_pass_estimate,pass.today>=pass.target?L.t("今日初见目标已完成 · "):"",pass.days()));};
    }
    private void dailyGoalDialog(){
        LinearLayout content=Ui.column(this);Ui.pad(content,20);
        content.addView(Ui.text(this,L.t("每天新接触多少个词？目标不会限制自动轮换，也不要求分组。"),13,Ui.INK));
        EditText count=new EditText(this);count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);count.setSingleLine(true);count.setText(String.valueOf(prefs.dailyNewTarget()));count.setContentDescription(L.t("每天新接触词数"));content.addView(count,Ui.inputSize(count,48));Ui.tint(content);Ui.gap(content,12);
        content.addView(Ui.prose(this,L.t("同一个词首次显示或进入音频学习计一次，之后复习不再算新词。「过一遍」表示接触过，不能证明已记住。进度按所选等级／词频、学习来源或自选范围计算，忽略分组和跳过熟悉词，避免总数随学习缩小。预计天数按每日目标计算，未计休息日。"),12,Ui.MUTED));
        AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("每日新词目标")).setView(content).setPositiveButton(L.t("保存"),null).setNeutralButton(L.t("隐藏首页进度"),(d,w)->{prefs.put("showFirstPass",false);render();}).setNegativeButton(L.t("取消"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{int value=Integer.parseInt(count.getText().toString().trim());if(value<1||value>10641)throw new NumberFormatException();prefs.data.edit().putInt("dailyNewTarget",value).apply();dialog.dismiss();render();}
            catch(NumberFormatException invalid){count.setError(L.t("请输入 1～10641 的整数"));}
        }));dialog.show();
    }
    private void startReview(String mode){speech.stop();startActivity(new Intent(this,ReviewActivity.class).putExtra("mode",mode));}
    private void evidenceCard(){
        LinearLayout panel=card(Ui.SURFACE);panel.addView(Ui.heading(this,L.t("接触与记忆"),17,Ui.INK));Ui.gap(panel,14);
        TextView[] values=new TextView[3];String[] labels={L.t("已接触"),L.t("手动熟悉"),L.t("间隔回忆较稳")};
        for(int i=0;i<3;i++){LinearLayout row=Ui.row(this);Ui.weighted(row,Ui.text(this,labels[i],13,Ui.MUTED));values[i]=Ui.heading(this,"—",20,Ui.INK);row.addView(values[i]);panel.addView(row);if(i<2)Ui.gap(panel,12);}
        Ui.gap(panel,14);panel.addView(Ui.prose(this,L.t("这三项可以重叠；看过或手动标熟悉，都不等于已经记住。"),11,Ui.MUTED));Ui.gap(panel,10);panel.addView(Ui.button(this,L.t("这些数字代表什么？"),false,v->statisticsGuide()));
        int request=searchGeneration;Async.IO.execute(()->{if(!alive||request!=searchGeneration)return;int[] evidence=store.evidence(StudySession.range(store,prefs),System.currentTimeMillis());handler.post(()->{if(alive&&request==searchGeneration)for(int i=0;i<3;i++)values[i].setText(getString(R.string.word_count,evidence[i]));});});
    }
    private java.util.function.Consumer<StudyTasks> taskCard(boolean full){
        LinearLayout panel=card(Ui.SURFACE);panel.addView(Ui.heading(this,L.t("每日记忆任务"),17,Ui.INK));Ui.gap(panel,12);TextView info=Ui.prose(this,L.t("正在安排…"),14,Ui.INK);panel.addView(info);Ui.gap(panel,10);TextView next=Ui.prose(this,"",11,Ui.MUTED);panel.addView(next);
        LinearLayout small=Ui.column(this);if(full){Ui.gap(panel,16);panel.addView(Ui.button(this,L.t("开始今日任务"),true,v->startReview("TASK")));Ui.gap(small,10);small.addView(Ui.button(this,L.t("先复习 10 个"),false,v->startReview("LIGHT")));Ui.gap(small,10);small.addView(Ui.prose(this,L.t("每批最多 10 个到期词，不加新词或追加题目。遗忘词稍后再来；剩余任务会保留。"),12,Ui.MUTED));panel.addView(small);}small.setVisibility(View.GONE);
        java.util.function.Consumer<StudyTasks> apply=tasks->{small.setVisibility(full&&tasks.due>0?View.VISIBLE:View.GONE);info.setText(getString(R.string.task_dashboard,tasks.due,tasks.todayNew,prefs.focusNewTarget(),tasks.todayReviewed));next.setText(tasks.nextDue==Long.MAX_VALUE?L.t("新词先少量开始；到期复习优先。"):L.t("下次安排：")+java.time.Instant.ofEpochMilli(tasks.nextDue).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))+L.t(" · 未到时间也可自由练习"));};
        String key=homeKey();if(cachedTasks!=null&&key.equals(cachedTasksKey))apply.accept(cachedTasks);
        if(full){int request=searchGeneration;Async.IO.execute(()->{if(!alive||request!=searchGeneration)return;StudyTasks tasks=store.tasks(StudySession.range(store,prefs),System.currentTimeMillis());handler.post(()->{if(!alive||request!=searchGeneration||!key.equals(homeKey()))return;cachedTasks=tasks;cachedTasksKey=key;apply.accept(tasks);});});}
        return apply;
    }
    private void weakCard(){
        LinearLayout panel=card(Ui.SURFACE);panel.addView(Ui.heading(this,L.t("易忘词 · 小份回忆"),16,Ui.INK));Ui.gap(panel,8);TextView count=Ui.text(this,L.t("正在读取…"),13,Ui.MUTED);panel.addView(count);Ui.gap(panel,10);TextView begin=Ui.button(this,L.t("练 5 个易忘词"),false,v->startReview("WEAK"));begin.setEnabled(false);panel.addView(begin);Ui.gap(panel,8);TextView why=Ui.text(this,L.t("如何判定和退出名单？  ›"),12,Ui.GREEN);why.setMinHeight(d(36));why.setGravity(Gravity.CENTER_VERTICAL);why.setOnClickListener(v->new ThemedDialog.Builder(this).setTitle(L.t("易忘词如何判定")).setMessage(L.t("同一词在两个不同日期评为没想起才入列，同一天反复忘记只算一次。\n\n最近一次遗忘后，在三个不同日期连续评为记得才退出；同日立刻答对不算稳定回忆，模糊或没想起会中断连续表现。\n\n每批最多五词，不追加题目，不占正式新学额度，也不改正式复习时间。仅使用 1.6 起记录的表现，不追溯猜测旧记录。")).setPositiveButton(L.t("明白了"),null).show());panel.addView(why);
        int request=searchGeneration;Async.IO.execute(()->{if(!alive||request!=searchGeneration)return;int size=store.weak(StudySession.range(store,prefs),0).size();handler.post(()->{if(alive&&request==searchGeneration){count.setText(size==0?L.t("当前范围暂无易忘词；不同日期的复习表现会逐渐形成名单。"):L.t("当前范围 ")+size+L.t(" 词 · 每次最多练 5 个"));begin.setEnabled(size>0);begin.setAlpha(size>0?1f:.55f);}});});
    }
    private void reviewPage(){

        body.addView(Ui.heading(this,L.t("复习"),26,Ui.INK));Ui.gap(body,8);body.addView(Ui.prose(this,L.t("先完成今日任务，其他练习按需选择。"),13,Ui.MUTED));Ui.gap(body,8);body.addView(Ui.button(this,L.t("复习怎么用？"),false,v->ReviewGuide.show(this)));Ui.gap(body,12);
        String scope=prefs.groupMode().equals("CUSTOM")?L.t("自选范围"):(prefs.level().equals("ALL")?L.t("全部等级"):prefs.level())+" · "+L.t(Frequency.label(Frequency.effective(prefs.level(),prefs.frequency())));body.addView(Ui.text(this,L.t("当前范围：")+scope+" · "+(prefs.studyMode().equals("all")?L.t("全部词"):prefs.studyMode().equals("favorites")?L.t("收藏词"):L.t("熟悉词")),12,Ui.GREEN));Ui.gap(body,14);taskCard(true);Ui.gap(body,16);fold(L.t("其他练习"),()->{body.addView(Ui.button(this,L.t("只复习到期词"),false,v->startReview("DUE")));Ui.gap(body,8);body.addView(Ui.button(this,L.t("自由练习 · 不改正式计划"),false,v->startReview("PRACTICE")));Ui.gap(body,8);body.addView(Ui.button(this,L.t("四选一识义练习 · 不改正式计划"),false,v->startActivity(new Intent(this,QuizActivity.class))));Ui.gap(body,12);weakCard();});Ui.gap(body,14);fold(L.t("学习记录"),()->evidenceCard());Ui.gap(body,14);fold(L.t("复习选项"),()->{
        body.addView(Ui.button(this,L.t("每日新学目标 · ")+prefs.focusNewTarget()+L.t(" 词"),false,v->focusTargetDialog()));Ui.gap(body,10);
        body.addView(Ui.text(this,L.t("回忆方向"),14,Ui.INK));Spinner direction=new ChoiceSpinner(this);direction.setContentDescription(L.t("回忆方向"));direction.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{L.t("看日语，回忆中文"),L.t("看中文，回忆日语与读音")}));direction.setSelection(prefs.reverseRecall()?1:0,false);body.addView(direction,new LinearLayout.LayoutParams(-1,-2));direction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(prefs.reverseRecall()!=(pos==1))prefs.put("reverseRecall",pos==1);}public void onNothingSelected(AdapterView<?> p){}});
        Ui.gap(body,14);
        body.addView(Ui.text(this,L.t("每批词数"),14,Ui.INK));batchPicker();
        Ui.gap(body,8);body.addView(Ui.prose(this,L.t("每批控制一轮，日目标控制当天正式新词；两者不同。可自定义 1～50 词，下次新安排生效。"),12,Ui.MUTED));
        toggle(L.t("复习时自动朗读"),L.t("看词想义时读日语；反向回忆揭晓后才读，避免提前给出答案。"),"reviewAutoSpeak",prefs.reviewAutoSpeak());
        Ui.gap(body,12);body.addView(Ui.button(this,L.t("查看复习规则与评分说明"),false,v->ReviewGuide.show(this)));Ui.gap(body,8);body.addView(Ui.button(this,L.t("调整学习范围"),false,v->{tab=3;settingsCategory=1;render();}));});
    }
    private void batchPicker(){
        ChoiceSpinner batch=new ChoiceSpinner(this);batch.setContentDescription(L.t("每批词数"));int[] values=ReviewBatch.PRESETS;String[] labels=new String[values.length+1];int chosen=values.length;
        for(int i=0;i<values.length;i++){labels[i]=getString(R.string.word_count,values[i]);if(values[i]==prefs.reviewBatch())chosen=i;}labels[values.length]=L.t("自定义…")+(chosen==values.length?" · "+getString(R.string.word_count,prefs.reviewBatch()):"");
        batch.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));batch.setSelection(chosen,false);body.addView(batch);
        batch.setOnChoice(pos->{if(pos<values.length){prefs.put("reviewBatch",values[pos]);batch.setSelection(pos);}else batchDialog();});
    }
    private void batchDialog(){
        LinearLayout content=Ui.column(this);Ui.pad(content,20);content.addView(Ui.prose(this,L.t("请输入 1～50 的整数。改变每批词数不会截短正在进行的一轮；没想起的词最多追加一次，因此实际题目可能更多。"),13,Ui.MUTED));
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(prefs.reviewBatch()));input.setContentDescription(L.t("自定义每批词数"));content.addView(input,Ui.inputSize(input,48));Ui.tint(content);
        ScrollView editor=new ScrollView(this);editor.addView(content);
        AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("自定义每批词数（1～50）")).setView(editor).setPositiveButton(L.t("保存"),null).setNegativeButton(L.t("取消"),(v,w)->rerenderHere()).setOnCancelListener(v->rerenderHere()).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{int value=ReviewBatch.parse(input.getText().toString());prefs.put("reviewBatch",value);dialog.dismiss();rerenderHere();}catch(IllegalArgumentException invalid){input.setError(L.t(invalid.getMessage()));}}));dialog.show();
    }
    private void focusTargetDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(prefs.focusNewTarget()));input.setContentDescription(L.t("每日正式新学词数"));Ui.pad(input,16);Ui.tint(input);
        AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("每日正式新学词数（1～200）")).setView(input).setPositiveButton(L.t("保存"),null).setNegativeButton(L.t("取消"),null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int n=Integer.parseInt(input.getText().toString().trim());if(n<1||n>200)throw new NumberFormatException();prefs.put("focusNewTarget",n);dialog.dismiss();render();}catch(NumberFormatException e){input.setError(L.t("请输入 1～200 的整数"));}}));dialog.show();
    }
    private void backupOptions(){
        new ThemedDialog.Builder(this).setTitle(L.t("备份到哪里？")).setItems(new String[]{L.t("分享备份文件 · 微信／QQ／邮件等"),L.t("保存到下载文件夹 · 推荐"),L.t("选择其他保存位置")},(d,which)->{if(which==2||which==1&&Build.VERSION.SDK_INT<29){if(which==1)toast(L.t("这台手机需要系统保存窗口，请选择「下载」；文件名已填好"));chooseBackupLocation();return;}generateBackup(which==0);}).setNegativeButton(L.t("取消"),null).show();
    }
    private void chooseBackupLocation(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"cijian-backup-"+java.time.LocalDate.now()+".json");intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"));
        try{startActivityForResult(intent,EXPORT);}catch(ActivityNotFoundException e){toast(L.t("未找到文件选择器，请使用分享备份"));}
    }
    private void generateBackup(boolean share){
        toast(L.t("正在生成完整备份…"));Async.IO.execute(()->{try{String json=store.backup();File file=BackupFiles.create(this,json);if(!share)BackupFiles.downloads(this,json);handler.post(()->{if(!alive)return;recordBackup(!share,share?"上次生成分享备份：":"上次保存到下载：");if(share)shareBackup(file);else new ThemedDialog.Builder(this).setTitle(L.t("备份已保存")).setMessage(L.t("位置：下载 / Cijian\n在手机文件管理器中打开「下载」，找到 Cijian 文件夹。\n也可以把这份备份分享给微信文件传输助手、QQ、邮件或云盘。最终发送由你在对应应用里完成。")).setPositiveButton(L.t("分享这份备份"),(d,w)->shareBackup(file)).setNegativeButton(L.t("完成"),(d,w)->render()).show();});}catch(Exception e){handler.post(()->{if(alive)new ThemedDialog.Builder(this).setTitle(L.t("备份未完成")).setMessage(L.t("请换一个保存方式，现有记录不会受影响。")).setPositiveButton(L.t("选择方式"),(d,w)->backupOptions()).setNegativeButton(L.t("关闭"),null).show();});}});
    }
    private void shareBackup(File file){try{startActivity(Intent.createChooser(BackupFiles.share(this,file),L.t("分享词间完整备份")));}catch(ActivityNotFoundException e){toast(L.t("没有可接收文件的应用，可改为保存到下载"));}}
    private Switch toggle(String title,String subtitle,String key,boolean checked) {
        LinearLayout row=Ui.row(this);row.setPadding(0,d(12),0,d(12));LinearLayout words=Ui.column(this);words.setPadding(0,0,d(16),0);
        words.addView(Ui.bold(this,title,14,Ui.INK));Ui.gap(words,6);words.addView(Ui.prose(this,subtitle,12,Ui.MUTED));Ui.weighted(row,words);
        Switch toggle=new Switch(this);toggle.setContentDescription(title);toggle.setChecked(checked);toggle.setOnCheckedChangeListener((button,value)->prefs.put(key,value));row.addView(toggle);body.addView(row);return toggle;
    }
    private void slider(String title,String key,int min,int max,int step,int current,String suffix) {
        Ui.gap(body,8);TextView label=Ui.text(this,title+" · "+current+suffix,14,Ui.INK);body.addView(label);
        SeekBar seek=new SeekBar(this);seek.setMax((max-min)/step);seek.setProgress((current-min)/step);seek.setContentDescription(title);
        body.addView(seek,new LinearLayout.LayoutParams(-1,d(46)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int value,boolean user){label.setText(getString(R.string.slider_value,title,min+value*step,suffix));}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){prefs.put(key,min+s.getProgress()*step);}
        });
    }
    private void grammarGuide() {
        String guide=L.t("名：名词\n代：代词\nイ形：い形容词\nナ形：な形容词\n自動：自动词，不直接带宾语\n他動：他动词，可以带宾语\n自他動：兼有自动词、他动词用法\n\n动词后数字：\n1 → 五段动词\n2 → 一段动词\n3 → サ変／カ変等不规则动词\n\n例：他動1 = 他动词、五段活用\n名・自動3 = 名词，也可作サ変自动词\n\n副：副词\n連体：连体词\n接：接续词\n接頭／接尾：前缀／后缀\n感：感叹词\n連語／成句：固定搭配／惯用语\n造：构词成分\n\n⓪①② 等圈号为音调标记，不是动词分类。\n词性来自原词库；一个词的具体用法仍需结合例句。");
        TextView copy=Ui.text(this,guide,15,Ui.INK);Ui.pad(copy,22);copy.setTextIsSelectable(true);ScrollView scroller=new ScrollView(this);scroller.addView(copy);
        new ThemedDialog.Builder(this).setTitle(L.t("读懂词性标记")).setView(scroller).setPositiveButton(L.t("明白了"),null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==OVERLAY && Settings.canDrawOverlays(this) && pendingStart){pendingStart=false;requestNotificationsThenStart();return;}
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT)Async.IO.execute(()->{
            try{
                try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException();output.write(store.backup().getBytes(StandardCharsets.UTF_8));}
                handler.post(()->{recordBackup(true,"上次保存文件：");toast(L.t("完整备份已保存到你选择的位置"));});
            }catch(Exception e){handler.post(()->toast(L.t("备份失败，请换一个保存位置")));}
        });
        if(request==IMPORT)previewRestore(uri);

    }
    private void previewRestore(Uri uri){
        toast(L.t("正在读取备份预览…"));
        Async.IO.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new IOException();ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(bytes.size()+count>32*1024*1024)throw new IOException();bytes.write(buffer,0,count);}
                String json=bytes.toString(StandardCharsets.UTF_8.name());BackupPreview preview=BackupPreview.inspect(json);
                handler.post(()->{if(!alive||isFinishing())return;
                    LinearLayout panel=Ui.column(this);Ui.pad(panel,20);TextView copy=Ui.prose(this,preview.summary(),14,Ui.INK);copy.setTextIsSelectable(true);panel.addView(copy);Ui.gap(panel,14);
                    CheckBox settings=new CheckBox(this);settings.setText(L.t("同时恢复设置、自选组与浏览位置"));settings.setTextColor(Ui.INK);settings.setChecked(preview.settings);settings.setEnabled(preview.settings);panel.addView(settings);Ui.gap(panel,12);
                    panel.addView(Ui.prose(this,L.t("没有修改时间的旧标记，发生冲突时："),13,Ui.INK));RadioGroup legacy=new RadioGroup(this);String[] labels={L.t("保留本机已有标记，仅导入新词的标记"),L.t("合并双方已选标记（可能加回旧收藏）")};for(int i=0;i<2;i++){RadioButton option=new RadioButton(this);option.setId(i==0?R.id.restore_marks_local:R.id.restore_marks_merge);option.setText(labels[i]);option.setTextColor(Ui.INK);legacy.addView(option);}legacy.check(R.id.restore_marks_local);panel.addView(legacy);Ui.gap(panel,12);panel.addView(Ui.prose(this,L.t("导入前自动保存一份本机快照，可在备份与帮助撤回。恢复会停止悬浮学习；未完成复习批次重新安排，已提交评分保留。"),12,Ui.MUTED));Ui.tint(panel);ScrollView view=new ScrollView(this);view.addView(panel);
                    AlertDialog dialog=new ThemedDialog.Builder(this).setTitle(L.t("恢复前预览")).setView(view).setPositiveButton(L.t("确认合并恢复"),(d,w)->restoreBackup(json,settings.isChecked(),legacy.getCheckedRadioButtonId()==R.id.restore_marks_local)).setNegativeButton(L.t("取消"),null).create();dialog.show();Ui.dialogWindow(dialog,this);
                });
            }catch(Exception e){handler.post(()->{if(alive)new ThemedDialog.Builder(this).setTitle(L.t("无法预览这个备份")).setMessage(L.t("文件不是有效的词间完整备份，或无法读取。本机记录和设置没有改变，请重新选择。")).setPositiveButton(L.t("明白了"),null).show();});}
        });
    }
    private void restoreBackup(String json,boolean includeSettings,boolean keepLocal){
        FloatService.stopForRestore();toast(L.t("正在保存恢复快照并合并记录…"));Async.IO.execute(()->{
            try{RestoreSafety.apply(this,store,json,includeSettings,keepLocal);handler.post(()->{if(alive){toast(L.t("备份已恢复；可在备份与帮助撤回本次导入"));recreate();}});}
            catch(Exception e){handler.post(()->{if(alive)new ThemedDialog.Builder(this).setTitle(L.t("恢复未完成")).setMessage(L.t("已保留导入前快照。请保留应用数据；若恢复中断，重新打开会尝试回到导入前状态。")).setPositiveButton(L.t("明白了"),null).show();});}
        });
    }
    private void undoRestore(){new ThemedDialog.Builder(this).setTitle(L.t("撤回上次导入？")).setMessage(L.t("返回导入前的本机记录和设置。导入之后新增的学习记录也会撤回。这里只保留最近一次导入前快照，不能代替手机之外的备份。")).setPositiveButton(L.t("确认返回导入前"),(d,w)->{FloatService.stopForRestore();Async.IO.execute(()->{try{RestoreSafety.undo(this,store);handler.post(()->{if(alive){toast(L.t("已返回导入前状态"));recreate();}});}catch(Exception e){handler.post(()->toast(L.t("未能完成撤回，请保留应用数据后重试")));}});}).setNegativeButton(L.t("取消"),null).show();}
    private void recordBackup(boolean saved,String label){
        long now=System.currentTimeMillis();prefs.data.edit().putString("backup.last",label+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))).putLong(saved?"backup.savedAt":"backup.generatedAt",now).apply();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);String pitchId=intent.getStringExtra("pitchWord");if(pitchId!=null){pitchPending=pitchId;openPendingPitch();}
        if(intent.getBooleanExtra("openReview",false)){tab=2;if(store!=null)render();}
        if(intent.getBooleanExtra("voiceHelp",false)){if(store!=null)voiceGuide();else voiceGuidePending=true;}
    }
    private void voiceGuide(){
        if(!alive||isFinishing())return;
        LinearLayout content=Ui.column(this);Ui.pad(content,22);ScrollView scroller=new ScrollView(this);scroller.addView(content);
        content.addView(Ui.heading(this,L.t("让日语读出来"),21,Ui.INK));Ui.gap(content,10);content.addView(Ui.prose(this,speech.status(),13,Ui.GREEN));Ui.gap(content,16);
        content.addView(Ui.bold(this,L.t("已有兼容引擎"),15,Ui.INK));Ui.gap(content,7);content.addView(Ui.prose(this,L.t("在引擎中下载或启用日语声音，再返回重新检测。词间会在应用内选择引擎，不必更改手机全局默认。"),13,Ui.MUTED));Ui.gap(content,8);
        content.addView(Ui.button(this,L.t("打开系统文字转语音设置"),false,v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(ActivityNotFoundException e){toast(L.t("请在系统设置中搜索「文字转语音」"));}}));Ui.gap(content,8);
        content.addView(Ui.button(this,L.t("打开当前语音引擎"),false,v->{String id=speech.activeEngine();Intent open=id.isEmpty()?null:getPackageManager().getLaunchIntentForPackage(id);if(open!=null)startActivity(open);else toast(L.t("此引擎没有独立入口，请使用系统文字转语音设置"));}));Ui.gap(content,18);
        content.addView(Ui.bold(this,L.t("没有 Google Play · 三步配置"),15,Ui.INK));Ui.gap(content,8);content.addView(Ui.prose(this,L.t("1  打开下方作者国内下载页，下载安装 TTS Server。下载页密码为 1234。\n\n2  打开 TTS Server，添加／启用 ja-JP 日语声音并先在其中试听；需要中文连读时再配置中文声音。不同版本菜单可能不同。\n\n3  返回词间点「重新检测」，在发音设置选自动，或选刚安装的引擎。"),13,Ui.INK));Ui.gap(content,10);
        content.addView(Ui.button(this,L.t("打开作者国内下载页"),true,v->openLink("https://jing332.lanzn.com/b09jpjd2d")));Ui.gap(content,8);
        content.addView(Ui.button(this,L.t("复制下载密码 1234"),false,v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(L.t("TTS Server 作者下载页密码"),"1234"));toast(L.t("已复制密码 1234"));}));Ui.gap(content,10);
        content.addView(Ui.prose(this,L.t("这是作者在项目说明中列出的下载页，无需浏览 GitHub。TTS Server 是可选第三方应用；常用在线声音需要联网，服务可用性由其提供方决定。安装与声音配置仍需你在系统和引擎中确认。词间不下载或安装第三方 APK，也不增加联网权限。"),12,Ui.MUTED));Ui.gap(content,12);
        content.addView(Ui.button(this,L.t("Google 语音官方页面"),false,v->openLink("https://play.google.com/store/apps/details?id=com.google.android.tts")));Ui.gap(content,8);content.addView(Ui.button(this,L.t("查看 TTS Server 项目来源"),false,v->openLink("https://github.com/jing332/tts-server-android")));
        AlertDialog d=new ThemedDialog.Builder(this).setTitle(L.t("获取日语语音")).setView(scroller).setPositiveButton(L.t("重新检测"),(dialog,which)->{speech.refresh();prefs.put("voice.refresh",String.valueOf(SystemClock.elapsedRealtime()));}).setNegativeButton(L.t("继续看词"),null).create();d.show();Ui.dialogWindow(d,this);
    }
    private void openPendingPitch(){String id=pitchPending;if(id.isEmpty()||store==null)return;pitchPending="";Async.IO.execute(()->{Word word=store.byId(id);handler.post(()->{if(alive&&word!=null)PitchGuide.show(this,word);});});}
    private void prepareLanguagePrompt(){
        if(prefs.data.contains("languagePromptSeen")||prefs.data.contains("languagePromptPending"))return;
        boolean existing=getDatabasePath("cijian.db").exists()||!prefs.data.getAll().isEmpty();
        prefs.data.edit().putBoolean("languagePromptPending",!existing).putBoolean("languagePromptSeen",existing).apply();
    }
    private void showLanguagePrompt(){
        if(!prefs.data.getBoolean("languagePromptPending",false)||isFinishing()||languagePrompt!=null&&languagePrompt.isShowing())return;
        LinearLayout panel=Ui.column(this);Ui.pad(panel,20);panel.addView(Ui.heading(this,"词间 · Cijian",23,Ui.INK));Ui.gap(panel,10);
        panel.addView(Ui.prose(this,"选择界面语言 / Choose a language / 表示言語を選択",14,Ui.INK));Ui.gap(panel,10);
        panel.addView(Ui.prose(this,"本应用用中文释义学习日语，切换界面不会翻译词库。\n\nLearn Japanese with Chinese meanings. Changing the interface language does not translate the dictionary.\n\n中国語の意味で日本語を学ぶアプリです。表示言語を変えても、辞書の訳は中国語のままです。",12,Ui.MUTED));Ui.gap(panel,12);
        RadioGroup choices=new RadioGroup(this);String selected=UiLanguage.resolve(prefs.data.getString("uiLanguage",null),android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0));String[] picked={selected};
        for(int i=0;i<UiLanguage.IDS.length;i++){RadioButton item=new RadioButton(this);item.setId(View.generateViewId());item.setText(UiLanguage.NAMES[i]);item.setTextColor(Ui.INK);item.setMinHeight(d(48));String id=UiLanguage.IDS[i];item.setOnCheckedChangeListener((button,checked)->{if(checked)picked[0]=id;});choices.addView(item);item.setChecked(id.equals(selected));}panel.addView(choices);Ui.tint(panel);
        ScrollView scroller=new ScrollView(this);scroller.addView(panel);languagePrompt=new ThemedDialog.Builder(this).setView(scroller).setPositiveButton("开始 / Start / はじめる",(dialog,which)->{prefs.data.edit().putString("uiLanguage",picked[0]).putBoolean("languagePromptPending",false).putBoolean("languagePromptSeen",true).apply();L.init(this);recreate();}).setCancelable(false).create();languagePrompt.show();Ui.dialogWindow(languagePrompt,this);
    }
    private void openLink(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast(L.t("未找到浏览器"));}}
    private void toast(String value){if(alive)Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(languagePrompt!=null)languagePrompt.dismiss();if(stateRegistered)StudySurface.forget(overlayState);alive=false;searchGeneration++;handler.removeCallbacksAndMessages(null);speech.close();super.onDestroy();}
}

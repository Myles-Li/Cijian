package com.myles.kotobafloat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.*;

/** 仅在本应用尝试已安装引擎，不改变手机全局默认。 / Tries installed engines within this app; never changes the phone's global default. */
final class Speech {
    static final String GOOGLE="com.google.android.tts";
    static final class Engine {
        final String id,label;
        Engine(String id,String label){this.id=id;this.label=label;}
    }
    private final Context context;
    private final AudioGuard audio;
    private java.util.function.Consumer<String> interrupted;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Engine> candidates=new ArrayList<>();
    private TextToSpeech tts;
    private boolean ready,checking,closed,pendingExplicit,speaking,sequenceExplicit,exampleSequence;
    private long serial;
    private String activeUtterance="";
    private List<SpeechPlan.Segment> activePlan=Collections.emptyList();
    private int segment;
    private boolean chineseUnavailable,warnedChinese;
    private int generation,candidate;
    private String engineLabel="",engineId="",lastFailure="";
    String activeEngine(){return engineId;}
    private List<SpeechPlan.Segment> pending;
    private Runnable unavailable,changed;
    Speech(Context context){this.context=context.getApplicationContext();audio=new AudioGuard(this.context,reason->{stop();if(interrupted!=null)interrupted.accept(reason);else android.widget.Toast.makeText(this.context,reason,android.widget.Toast.LENGTH_SHORT).show();});refresh();}
    void setPlaybackExpected(java.util.function.BooleanSupplier expected){audio.setExpected(expected);}
    void setOnInterrupted(java.util.function.Consumer<String> listener){interrupted=listener;}
    static List<Engine> engines(Context context) {
        ArrayList<Engine> engines=new ArrayList<>();
        for(ResolveInfo info:context.getPackageManager().queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),0)){
            if(info.serviceInfo==null)continue;
            String id=info.serviceInfo.packageName;
            if(engines.stream().noneMatch(e->e.id.equals(id)))engines.add(new Engine(id,info.loadLabel(context.getPackageManager()).toString()));
        }
        return engines;
    }
    void setOnUnavailable(Runnable callback){unavailable=callback;}
    void setOnChanged(Runnable callback){changed=callback;}
    String status(){return checking?L.t("正在检测日语语音…"):ready?L.t("日语语音已就绪 · ")+engineLabel+(chineseUnavailable?L.t(" · 中文不可用"):"")+"\n"+voiceAvailability("ja")+(lastFailure.isEmpty()?"":"\n"+lastFailure):L.t("缺少可用的日语语音；点击「获取日语语音」")+(lastFailure.isEmpty()?"":"\n"+lastFailure);}
    String voiceAvailability(String language){
        if(!ready||tts==null)return L.t("语音尚未就绪");
        try{
            boolean local=false,network=false;Set<android.speech.tts.Voice> voices=tts.getVoices();if(voices==null||voices.isEmpty())return L.t("引擎未报告离线能力，请断网试读确认");
            for(android.speech.tts.Voice voice:voices)if(voice.getLocale().getLanguage().equals(language)&&!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)){if(voice.isNetworkConnectionRequired())network=true;else local=true;}
            String label=language.equals("ja")?L.t("日语"):L.t("中文");
            return label+"："+(local?L.t("引擎报告有本地声音；请断网试读确认"):network?L.t("引擎报告声音需要网络"):L.t("未报告已安装的可用声音"));
        }catch(RuntimeException e){return L.t("引擎未能报告离线能力，请断网试读确认");}
    }
    void testChinese(){sequence(Collections.singletonList(new SpeechPlan.Segment("你好，这是词间的中文试读。","zh")),true);}
    private void playbackFailed(boolean explicit,String reason){stop();ready=false;checking=false;lastFailure=reason;notifyChanged();if(interrupted!=null)interrupted.accept(reason+L.t("；请重新检测语音后手动继续"));if(explicit)help();}
    boolean isReady(){return ready;}
    boolean isInitializing(){return checking;}
    void refresh() {
        if(closed)return;
        stop();lastFailure="";chineseUnavailable=false;warnedChinese=false;generation++;handler.removeCallbacksAndMessages(null);ready=false;checking=true;candidate=0;candidates.clear();
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        List<Engine> installed=engines(context);
        String preferred=new Prefs(context).data.getString("voiceEngine","AUTO");
        if(!"AUTO".equals(preferred)) {
            for(Engine e:installed)if(e.id.equals(preferred))candidates.add(e);
        }else{
            String system=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.TTS_DEFAULT_SYNTH);
            for(String id:new String[]{GOOGLE,system})if(id!=null)for(Engine e:installed)if(e.id.equals(id)&&candidates.stream().noneMatch(x->x.id.equals(e.id)))candidates.add(e);
            for(Engine e:installed)if(candidates.stream().noneMatch(x->x.id.equals(e.id)))candidates.add(e);
        }
        notifyChanged();tryNext();
    }
    private void tryNext() {
        if(closed)return;
        if(candidate>=candidates.size()){
            checking=false;ready=false;notifyChanged();
            if(pendingExplicit){pending=null;pendingExplicit=false;help();}
            return;
        }
        if(tts!=null){tts.shutdown();tts=null;}
        Engine engine=candidates.get(candidate++);engineLabel=engine.label;engineId=engine.id;
        final int request=++generation;
        tts=new TextToSpeech(context,status->handler.post(()->initialized(request,status)),engine.id);
        handler.postDelayed(()->{if(!closed&&request==generation&&checking){generation++;tryNext();}},8000);
    }
    private void initialized(int request,int status) {
        if(closed||request!=generation)return;
        boolean supported=false;
        if(status==TextToSpeech.SUCCESS&&tts!=null){
            int result=tts.setLanguage(Locale.JAPAN);
            supported=result>=TextToSpeech.LANG_AVAILABLE;
            if(supported){tts.setSpeechRate(new Prefs(context).speechRate());tts.setAudioAttributes(AudioGuard.attributes());}
        }
        if(!supported){tryNext();return;}
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){handler.post(()->utteranceFinished(id,false));}
            public void onError(String id){handler.post(()->utteranceFinished(id,true));}
            @Override public void onStop(String id,boolean interrupted){handler.post(()->{if(id!=null&&id.equals(activeUtterance))stop();});}
        });
        ready=true;checking=false;notifyChanged();
        if(pending!=null){List<SpeechPlan.Segment> texts=pending;boolean explicit=pendingExplicit,example=exampleSequence;pending=null;pendingExplicit=false;sequence(texts,explicit,example);}
    }
    boolean isSpeakingSequence(){return speaking||pending!=null;}
    boolean isSpeakingExample(){return exampleSequence&&isSpeakingSequence();}
    void speak(String text,boolean explicit){sequence(Collections.singletonList(new SpeechPlan.Segment(text,"ja")),explicit);}
    void speakWord(Word word,boolean explicit){
        Prefs prefs=new Prefs(context);
        String spoken=word.spokenForm();if(spoken.trim().isEmpty()){if(explicit)android.widget.Toast.makeText(context,L.t("原库缺少可靠日语读音，暂不猜测朗读。"),android.widget.Toast.LENGTH_LONG).show();return;}
        sequence(SpeechPlan.bilingual(spoken,Ui.meaning(word,prefs),word.example,word.exampleMeaning,prefs.wordRepeats(),prefs.exampleRepeats(),prefs.readExample(),prefs.readChinese()),explicit);
    }
    void speakExample(Word word,boolean explicit){
        Prefs prefs=new Prefs(context);
        sequence(SpeechPlan.bilingual(word.example,word.exampleMeaning,"","",prefs.exampleRepeats(),1,false,prefs.readChinese()),explicit,true);
    }
    private void sequence(List<SpeechPlan.Segment> texts,boolean explicit){sequence(texts,explicit,false);}
    private void sequence(List<SpeechPlan.Segment> texts,boolean explicit,boolean example){
        if(closed||texts.isEmpty())return;
        stop();exampleSequence=example;
        if(ready&&tts!=null){
            if(!audio.acquire())return;
            speaking=true;sequenceExplicit=explicit;activePlan=new ArrayList<>(texts);segment=0;
            final long request=++serial;
            int characters=0;for(SpeechPlan.Segment part:texts)characters+=part.text.length();
            long watchdog=Math.min(600000L,15000L+characters*600L+texts.size()*new Prefs(context).speechGap());
            handler.postDelayed(()->{if(!closed&&request==serial&&speaking){playbackFailed(explicit,L.t("朗读超时，已停止本次播放"));}},watchdog);
            playNext();
        }else if(checking&&explicit){pending=new ArrayList<>(texts);pendingExplicit=true;}
        else if(explicit)help();
    }
    private void playNext(){
        while(!closed&&speaking&&segment<activePlan.size()){
            SpeechPlan.Segment part=activePlan.get(segment);
            java.util.Locale language=part.language.equals("zh")?Locale.SIMPLIFIED_CHINESE:Locale.JAPAN;
            int available;
            try{available=tts.setLanguage(language);}catch(RuntimeException failure){available=TextToSpeech.LANG_NOT_SUPPORTED;}
            if(available<TextToSpeech.LANG_AVAILABLE){
                if(part.language.equals("zh")){skipChinese();segment++;continue;}
                boolean explicit=sequenceExplicit;playbackFailed(explicit,L.t("日语朗读失败，已停止本次播放"));return;
            }
            tts.setSpeechRate(new Prefs(context).speechRate());
            activeUtterance="kf-"+serial+"-"+segment;
            if(tts.speak(part.text,TextToSpeech.QUEUE_FLUSH,null,activeUtterance)!=TextToSpeech.ERROR)return;
            if(part.language.equals("zh")){skipChinese();segment++;continue;}
            boolean explicit=sequenceExplicit;playbackFailed(explicit,L.t("日语朗读失败，已停止本次播放"));return;
        }
        speaking=false;activeUtterance="";activePlan=Collections.emptyList();audio.release();
        if(tts!=null)tts.setLanguage(Locale.JAPAN);
    }
    private void skipChinese(){
        chineseUnavailable=true;notifyChanged();
        if(sequenceExplicit&&!warnedChinese){
            warnedChinese=true;android.widget.Toast.makeText(context,L.t("当前语音引擎的中文不可用，已跳过中文译文"),android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void utteranceFinished(String id,boolean error){
        if(closed||id==null||!id.equals(activeUtterance))return;
        if(error&&segment<activePlan.size()&&!activePlan.get(segment).language.equals("zh")){
            boolean explicit=sequenceExplicit;playbackFailed(explicit,L.t("日语朗读失败，已停止本次播放"));return;
        }
        if(error)skipChinese();
        activeUtterance="";segment++;
        if(segment>=activePlan.size()){playNext();return;}
        long request=serial;handler.postDelayed(()->{if(!closed&&speaking&&request==serial)playNext();},new Prefs(context).speechGap());
    }
    private void help(){if(unavailable!=null)unavailable.run();}
    private void notifyChanged(){if(changed!=null)changed.run();}
    void stop(){audio.release();serial++;speaking=false;exampleSequence=false;activeUtterance="";activePlan=Collections.emptyList();segment=0;pending=null;pendingExplicit=false;if(tts!=null)tts.stop();}
    void close(){closed=true;generation++;handler.removeCallbacksAndMessages(null);stop();if(tts!=null)tts.shutdown();audio.close();}
}

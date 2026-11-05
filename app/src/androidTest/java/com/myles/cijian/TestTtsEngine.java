package com.myles.cijian;

import android.media.AudioFormat;
import android.speech.tts.*;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** 仅测试包使用，输出定时静音并记录语言和文字以核对播放编排。 / Test APK only: emits timed silence and records language/text to verify orchestration. */
public final class TestTtsEngine extends TextToSpeechService {
    private volatile boolean stopped;
    private String testMode(){try{java.io.File f=new java.io.File(getFilesDir(),"tts-mode.txt");return f.exists()?new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8).trim():"normal";}catch(Exception ignored){return "normal";}}

    @Override protected String[] onGetLanguage(){return new String[]{"jpn","JPN",""};}
    @Override protected int onIsLanguageAvailable(String language,String country,String variant){
        if(testMode().equals("noChinese")&&(language.equals("zho")||language.equals("chi")||language.equals("zh")))return TextToSpeech.LANG_NOT_SUPPORTED;
        return language.equals("jpn")||language.equals("ja")||language.equals("zho")||language.equals("chi")||language.equals("zh")?TextToSpeech.LANG_COUNTRY_AVAILABLE:TextToSpeech.LANG_NOT_SUPPORTED;
    }
    @Override protected int onLoadLanguage(String language,String country,String variant){return onIsLanguageAvailable(language,country,variant);}
    @Override protected void onStop(){stopped=true;}
    @Override protected void onSynthesizeText(SynthesisRequest request,SynthesisCallback callback){
        stopped=false;
        try{
            JSONObject entry=new JSONObject().put("text",request.getCharSequenceText().toString()).put("language",request.getLanguage()).put("time",System.currentTimeMillis()).put("rate",request.getSpeechRate());
            try(FileOutputStream output=openFileOutput("utterances.jsonl",MODE_APPEND)){output.write((entry.toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        }catch(Exception ignored){}
        String mode=testMode();if(mode.equals("error")){callback.start(16000,AudioFormat.ENCODING_PCM_16BIT,1);callback.error(TextToSpeech.ERROR_SYNTHESIS);callback.done();return;}
        callback.start(16000,AudioFormat.ENCODING_PCM_16BIT,1);
        if(mode.equals("hang")){long until=System.currentTimeMillis()+60000;while(!stopped&&System.currentTimeMillis()<until){try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}return;}
        byte[] silence=new byte[3200];
        for(int i=0;i<8&&!stopped;i++){
            try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            callback.audioAvailable(silence,0,silence.length);
        }
        if(!stopped)callback.done();
    }
}

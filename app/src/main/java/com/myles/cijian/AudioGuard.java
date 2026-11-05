package com.myles.cijian;

import android.content.*;
import android.media.*;
import android.os.*;
import java.util.function.Consumer;

/** 同一时刻只有一个语音拥有者，显式申请焦点，中断后不自动重播。 / One speech owner, explicit focus, and no automatic replay after an interruption. */
final class AudioGuard {
    private static java.lang.ref.WeakReference<AudioGuard> owner=new java.lang.ref.WeakReference<>(null);
    private final Context context;
    private final AudioManager manager;
    private final AudioFocusRequest request;
    private final Consumer<String> interrupted;
    private boolean held,closed;
    private java.util.function.BooleanSupplier expected=()->false;
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(held||expected.getAsBoolean())interrupt(L.t("耳机已断开，点继续后再播放"));}};
    AudioGuard(Context context,Consumer<String> interrupted){
        this.context=context.getApplicationContext();this.interrupted=interrupted;manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes()).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(change->{if(held&&(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))interrupt(L.t("其他声音正在播放，点继续后再播放"));},new Handler(Looper.getMainLooper())).build();
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);if(Build.VERSION.SDK_INT>=33)this.context.registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else this.context.registerReceiver(noisy,filter);
    }
    void setExpected(java.util.function.BooleanSupplier expected){this.expected=expected;}
    static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();}
    boolean acquire(){
        if(closed)return false;AudioGuard other=owner.get();if(other!=null&&other!=this)other.interrupt(L.t("已切换到另一处朗读，点继续后再播放"));
        int result;try{result=manager.requestAudioFocus(request);}catch(RuntimeException unavailable){result=AudioManager.AUDIOFOCUS_REQUEST_FAILED;}held=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(held){owner=new java.lang.ref.WeakReference<>(this);return true;}interrupted.accept(L.t("暂时无法播放声音，点继续后再试"));return false;
    }
    void interrupt(String reason){release();interrupted.accept(reason);}
    void release(){boolean previous=held;held=false;if(owner.get()==this)owner.clear();if(previous)manager.abandonAudioFocusRequest(request);}
    void close(){if(closed)return;closed=true;release();context.unregisterReceiver(noisy);}
}

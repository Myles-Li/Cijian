package com.myles.kotobafloat;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

/** App 内宿主，只管理生命周期与界面转移。 / In-app host manages lifecycle and surface transfer only. */
public final class ReviewActivity extends Activity {
    private ReviewFlow flow;private boolean foreground;private String mode;private boolean pendingOverlay;
    private final Runnable relinquish=()->{if(flow!=null)flow.setVisible(false);finish();};
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(L.context(base));}
    @Override public void onCreate(Bundle state){
        setTheme(Ui.themeResource(this));Ui.applyTheme(this);super.onCreate(state);FloatService.stopForRestore();StudySurface.claim(relinquish);
        mode=getIntent().getStringExtra("mode");if(mode==null)mode="TASK";
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(root);root.requestApplyInsets();setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        LinearLayout bar=Ui.row(this);Ui.pad(bar,12);bar.addView(Ui.button(this,L.t("‹ 返回"),false,v->finish()));TextView title=Ui.bold(this,L.t("认真记忆"),17,Ui.INK);title.setPadding(Ui.dp(this,16),0,0,0);bar.addView(title);root.addView(bar);LinearLayout panel=Ui.column(this);root.addView(panel,new LinearLayout.LayoutParams(-1,0,1));
        flow=new ReviewFlow(this,panel,mode,new ReviewFlow.Host(){
            public void exit(){startActivity(new Intent(ReviewActivity.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openReview",true));finish();}
            public void transfer(){toOverlay();}public boolean prepareAudio(){return foreground;}public boolean visible(){return foreground;}public boolean overlay(){return false;}
        },state!=null||getIntent().getBooleanExtra("resume",false));
    }
    private void toOverlay(){
        if(!Settings.canDrawOverlays(this)){pendingOverlay=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){pendingOverlay=false;Toast.makeText(this,L.t("请在系统设置允许悬浮显示。"),Toast.LENGTH_LONG).show();}return;}
        try{if(flow!=null)flow.setVisible(false);startForegroundService(new Intent(this,FloatService.class).setAction("FOCUS").putExtra("mode",mode).putExtra("resume",true));finish();}catch(RuntimeException e){if(flow!=null)flow.setVisible(true);Toast.makeText(this,L.t("未能开启悬浮窗，已保留复习进度。"),Toast.LENGTH_LONG).show();}
    }
    @Override protected void onResume(){super.onResume();foreground=true;if(flow!=null){flow.setVisible(true);flow.checkSettings();}if(pendingOverlay){pendingOverlay=false;if(Settings.canDrawOverlays(this))toOverlay();}}
    @Override protected void onPause(){foreground=false;if(flow!=null)flow.setVisible(false);super.onPause();}
    @Override protected void onDestroy(){StudySurface.leave(relinquish);if(flow!=null)flow.close();super.onDestroy();}
}

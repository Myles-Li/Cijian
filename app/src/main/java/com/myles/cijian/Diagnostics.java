package com.myles.cijian;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.net.Uri;
import android.widget.*;

final class Diagnostics {
    static void show(Activity a,Speech speech,Runnable voiceHelp){
        Prefs prefs=new Prefs(a);LinearLayout root=Ui.column(a);Ui.pad(root,20);root.addView(Ui.heading(a,L.t("运行状态检查"),20,Ui.INK));Ui.gap(root,12);ScrollView scroll=new ScrollView(a);LinearLayout body=Ui.column(a);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(a,500),(int)(a.getResources().getDisplayMetrics().heightPixels*.6f))));TextView status=Ui.prose(a,L.t("正在读取状态…"),14,Ui.INK);body.addView(status);Ui.gap(body,14);
        Runnable refresh=()->status.setText(report(a,prefs,speech));
        body.addView(Ui.button(a,L.t("重新检测语音"),false,v->{speech.refresh();prefs.put("voice.refresh",String.valueOf(SystemClock.elapsedRealtime()));refresh.run();}));Ui.gap(body,8);
        body.addView(Ui.button(a,L.t("悬浮显示权限"),false,v->open(a,new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.getPackageName())))));Ui.gap(body,8);
        body.addView(Ui.button(a,L.t("通知设置"),false,v->open(a,new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()))));Ui.gap(body,8);
        body.addView(Ui.button(a,L.t("应用信息与后台设置"),false,v->open(a,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName())))));Ui.gap(body,8);
        body.addView(Ui.button(a,L.t("获取日语语音"),false,v->voiceHelp.run()));Ui.gap(body,8);
        body.addView(Ui.button(a,L.t("复制检查结果"),false,v->{android.content.ClipboardManager clip=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);clip.setPrimaryClip(ClipData.newPlainText(L.t("词间运行状态"),report(a,prefs,speech)));Toast.makeText(a,L.t("已复制运行状态，不包含单词和学习记录"),Toast.LENGTH_SHORT).show();}));Ui.gap(body,10);
        body.addView(Ui.prose(a,L.t("这里只展示可检测项目。厂商自启动和省电策略无法直接确认，请按手机系统说明检查。"),12,Ui.MUTED));
        AlertDialog d=new ThemedDialog.Builder(a).setView(root).setPositiveButton(L.t("关闭"),null).create();d.show();Ui.dialogWindow(d,a);Runnable[] tick={null};tick[0]=()->{if(d.isShowing()&&!a.isFinishing()){refresh.run();root.postDelayed(tick[0],1000);}};d.setOnDismissListener(v->root.removeCallbacks(tick[0]));tick[0].run();
    }
    static String report(Context c,Prefs p,Speech speech){
        boolean overlay=Settings.canDrawOverlays(c),notification=c.getSystemService(NotificationManager.class).areNotificationsEnabled();PowerManager power=c.getSystemService(PowerManager.class);
        return L.t("悬浮显示权限：")+(overlay?L.t("已允许"):L.t("未允许"))+L.t("\n通知权限：")+(notification?L.t("已允许"):L.t("未允许"))+L.t("\n\n当前状态：")+FloatService.stateDescription()+L.t("\n\n语音：")+speech.status()+L.t("\n手动翻词朗读：")+(p.speakOnFlip()?L.t("开启"):L.t("关闭"))+L.t("\n自动朗读：")+(p.autoSpeak()?L.t("开启"):L.t("关闭"))+L.t("\n收球继续听：")+(p.ballAudio()?L.t("开启"):L.t("关闭"))+L.t("\n锁屏继续听：")+(p.lockAudio()?L.t("开启"):L.t("关闭"))+L.t("\n\n应用内开机自启动：")+(p.autoStart()?L.t("开启"):L.t("关闭"))+L.t("\n系统电池优化豁免：")+(power.isIgnoringBatteryOptimizations(c.getPackageName())?L.t("已豁免"):L.t("未豁免；后台可能受限"))+L.t("\n厂商自启动权限：需到系统确认\n\n词间 ")+Ui.version(c)+" · Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT;
    }
    private static void open(Activity a,Intent intent){try{a.startActivity(intent);}catch(ActivityNotFoundException e){Toast.makeText(a,L.t("请在系统设置中打开词间的应用信息"),Toast.LENGTH_LONG).show();}}
}

package com.myles.kotobafloat;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** 只在系统启动或覆盖升级后恢复用户已启用的悬浮窗，初始暂停。 / Only restores the user-enabled overlay, initially paused, after a system boot or app replacement. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent==null?null:intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        Prefs prefs=new Prefs(context);
        if(!prefs.autoStart()||FloatService.running)return;
        if(!Settings.canDrawOverlays(context)){
            prefs.put("boot.status","自动启动需要先允许悬浮显示");return;
        }
        try {
            context.startForegroundService(new Intent(context,FloatService.class).putExtra("fromAutoStart",true));
            prefs.put("boot.status","已请求开机恢复悬浮学习；先暂停，点播放开始");
        }catch(RuntimeException restricted){
            prefs.put("boot.status","系统阻止了自动启动，请检查自启动和后台运行权限");
            if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("startup","启动提醒",NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open=PendingIntent.getActivity(context,8,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            manager.notify(8,new Notification.Builder(context,"startup").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("词间未能自动启动").setContentText("点此打开词间，并检查系统的自启动设置")
                .setContentIntent(open).setAutoCancel(true).build());
        }
    }
}

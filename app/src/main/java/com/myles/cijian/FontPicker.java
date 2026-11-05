package com.myles.cijian;

import android.app.*;
import android.widget.*;
import android.view.*;

/** 只在确认时提交字号，预览不写学习记录。 / Commit only on Apply; preview never records learning. */
final class FontPicker {
    static void show(Activity a,Prefs prefs,Runnable changed){show(a,prefs,changed,false);}
    static void showApp(Activity a,Prefs prefs,Runnable changed){show(a,prefs,changed,true);}
    private static void show(Activity a,Prefs prefs,Runnable changed,boolean app){
        LinearLayout root=Ui.column(a);Ui.pad(root,20);root.addView(Ui.heading(a,L.t(app?"App 内学习字号":"悬浮窗文字大小"),20,Ui.INK));Ui.gap(root,12);
        ScrollView scroll=new ScrollView(a);LinearLayout body=Ui.column(a);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(a,500),(int)(a.getResources().getDisplayMetrics().heightPixels*.62f))));
        LinearLayout presets=Ui.row(a);body.addView(presets);
        int[] sizes=app?new int[]{prefs.appWordSize(),prefs.appReadingSize(),prefs.appMeaningSize()}:new int[]{prefs.wordSize(),prefs.readingSize(),prefs.meaningSize()};
        int[] min=app?new int[]{18,10,12}:new int[]{16,10,12},max=app?new int[]{48,28,32}:new int[]{36,22,24};
        String[] names={L.t("词头"),L.t("读音与词性"),L.t("释义")};SeekBar[] bars=new SeekBar[3];TextView[] labels=new TextView[3];
        LinearLayout preview=Ui.column(a);Ui.pad(preview,12);preview.setBackground(Ui.shape(Ui.SURFACE,16,a));TextView word=Ui.bold(a,"日本語",sizes[0],Ui.INK),reading=Ui.text(a,"にほんご  ·  名",sizes[1],Ui.GREEN),meaning=Ui.text(a,L.t("日语；日本的语言"),sizes[2],Ui.INK);preview.addView(word);Ui.gap(preview,6);preview.addView(reading);Ui.gap(preview,6);preview.addView(meaning);
        Runnable refresh=()->{word.setTextSize(sizes[0]);reading.setTextSize(sizes[1]);meaning.setTextSize(sizes[2]);for(int i=0;i<3;i++)if(labels[i]!=null)labels[i].setText(a.getString(R.string.font_size_label,names[i],sizes[i]));};
        int[][] presetsValues=app?new int[][]{{27,13,17},{34,15,21},{42,19,26}}:new int[][]{{20,11,13},{23,12,14},{28,15,18}};String[] presetNames={L.t("紧凑"),L.t("标准"),L.t("大字")};
        for(int j=0;j<3;j++){final int which=j;Ui.weighted(presets,Ui.button(a,presetNames[j],false,v->{for(int i=0;i<3;i++)bars[i].setProgress(presetsValues[which][i]-min[i]);refresh.run();}));}Ui.gap(body,12);
        for(int j=0;j<3;j++){final int i=j;labels[i]=Ui.text(a,names[i]+" · "+sizes[i],14,Ui.INK);body.addView(labels[i]);bars[i]=new SeekBar(a);bars[i].setContentDescription(names[i]+L.t("字号"));bars[i].setMax(max[i]-min[i]);bars[i].setProgress(sizes[i]-min[i]);body.addView(bars[i]);bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){sizes[i]=p+min[i];refresh.run();}});Ui.gap(body,10);}
        body.addView(Ui.text(a,L.t("实时预览 · 不计学习记录"),12,Ui.MUTED));Ui.gap(body,8);body.addView(preview);Ui.gap(body,10);body.addView(Ui.prose(a,L.t(app?"用于 App 内浏览、回忆、识义与词库；与悬浮窗字号分开保存，仍跟随系统字号。取消不会改变设置。":"同时跟随系统字体缩放。长词会换行，空间不足时详情可以滚动。取消不会改变设置。"),12,Ui.MUTED));Ui.tint(root);
        String[] keys=app?new String[]{"appWordSize","appReadingSize","appMeaningSize"}:new String[]{"wordSize","readingSize","meaningSize"};
        AlertDialog d=new ThemedDialog.Builder(a).setView(root).setPositiveButton(L.t("应用"),(v,w)->{prefs.data.edit().putInt(keys[0],sizes[0]).putInt(keys[1],sizes[1]).putInt(keys[2],sizes[2]).apply();changed.run();}).setNegativeButton(L.t("取消"),null).create();d.show();Ui.dialogWindow(d,a);
    }
}

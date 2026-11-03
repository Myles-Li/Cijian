package com.myles.kotobafloat;

import android.app.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

/** 默认主题置顶，其他十二项保持完整四行。 / Default first, then four complete rows of three choices. */
final class ThemePicker {
    static void show(Activity a,Prefs prefs,Runnable custom,Runnable changed){
        LinearLayout content=Ui.column(a);Ui.pad(content,20);content.addView(Ui.heading(a,L.t("主题颜色"),21,Ui.INK));Ui.gap(content,8);content.addView(Ui.prose(a,L.t("选择色块预览风格，文字会自动保持清晰。"),12,Ui.MUTED));Ui.gap(content,16);
        ScrollView scroll=new ScrollView(a);scroll.addView(content);AlertDialog dialog=new ThemedDialog.Builder(a).setView(scroll).setNegativeButton(L.t("取消"),null).create();
        content.addView(tile(a,prefs,"BLACK",true,dialog,custom,changed));Ui.gap(content,12);
        LinearLayout row=null;int placed=0;
        for(String id:Accent.IDS){if(id.equals("BLACK"))continue;if(placed%3==0){row=Ui.row(a);row.setGravity(Gravity.TOP);content.addView(row,new LinearLayout.LayoutParams(-1,-2));if(placed>0){LinearLayout.LayoutParams space=(LinearLayout.LayoutParams)row.getLayoutParams();space.topMargin=Ui.dp(a,8);row.setLayoutParams(space);}}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(Ui.dp(a,3),0,Ui.dp(a,3),0);row.addView(tile(a,prefs,id,false,dialog,custom,changed),lp);placed++;
        }
        dialog.show();Ui.dialogWindow(dialog,a);
    }
    private static LinearLayout tile(Activity a,Prefs prefs,String id,boolean wide,AlertDialog dialog,Runnable custom,Runnable changed){
        int index=Accent.index(id);boolean selected=id.equals(prefs.accent());LinearLayout tile=wide?Ui.row(a):Ui.column(a);tile.setGravity(Gravity.CENTER);tile.setPadding(Ui.dp(a,wide?16:5),Ui.dp(a,12),Ui.dp(a,wide?16:5),Ui.dp(a,12));tile.setMinimumHeight(Ui.dp(a,wide?60:88));tile.setTag("theme."+id);tile.setSelected(selected);
        GradientDrawable background=Ui.shape(selected?Ui.PALE:Ui.BG,12,a);if(selected)background.setStroke(Ui.dp(a,2),Ui.GREEN);tile.setBackground(background);
        View swatch=new View(a);GradientDrawable circle=Ui.shape(Accent.palette(id,prefs.customColor(),Ui.night(a))[0],20,a);circle.setStroke(Ui.dp(a,1),Ui.MUTED);swatch.setBackground(circle);tile.addView(swatch,new LinearLayout.LayoutParams(Ui.dp(a,30),Ui.dp(a,30)));
        String label=L.t(Accent.LABELS[index]);TextView text=Ui.bold(a,(id.equals("CUSTOM")?L.t("自定义"):label)+(selected?" ✓":""),12,Ui.INK);text.setGravity(wide?Gravity.CENTER_VERTICAL:Gravity.CENTER);
        if(wide){text.setPadding(Ui.dp(a,14),0,0,0);Ui.weighted(tile,text);}else{Ui.gap(tile,8);tile.addView(text);}
        tile.setContentDescription(L.t("主题颜色：")+label+(selected?L.t("，已选择"):""));tile.setFocusable(true);tile.setOnClickListener(v->{dialog.dismiss();if(id.equals("CUSTOM"))custom.run();else{prefs.put("accent",id);changed.run();}});return tile;
    }
    private ThemePicker(){}
}

package com.myles.kotobafloat;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

final class PitchGuide {
    static void show(Activity a,Word word){
        LinearLayout body=Ui.column(a);Ui.pad(body,20);body.addView(Ui.heading(a,word==null?L.t("读懂音调标记"):word.word+L.t(" · 音调"),21,Ui.INK));Ui.gap(body,12);
        body.addView(Ui.prose(a,L.t("圈号表示基本音型中下降的位置，按拍计算，不是重音大小。⓪ 表示没有标记的下降；① 表示第 1 拍后下降，② 表示第 2 拍后下降，依此类推。"),14,Ui.INK));Ui.gap(body,12);
        body.addView(Ui.prose(a,L.t("きゃ 等拗音组合算一拍；っ、ん、长音各占一拍。图中附上通用助词位置，便于区别平板型与词尾下降。这里只是基本高低示意，实际语流、活用和方言可能不同；TTS 未必严格重现原库音型。"),12,Ui.MUTED));Ui.gap(body,16);
        java.util.List<String> morae=word==null?Arrays.asList(L.t("第1拍"),L.t("第2拍"),L.t("第3拍")):Pronunciation.placeholder(word.reading)?Collections.emptyList():PitchPattern.morae(word.spokenForm());java.util.List<Integer> accents=word==null?Arrays.asList(0,1,2,3):PitchPattern.accents(word.pitch,morae.size());
        if(word!=null){body.addView(Ui.prose(a,(Pronunciation.origin(word.reading)?L.t("原库词源：")+word.reading+L.t("\n读音采用原库假名词头：")+word.word:L.t("原库读音：")+word.reading)+L.t("\n原库标记：")+(word.pitch.isEmpty()?L.t("未标注"):word.pitch),14,Ui.GREEN));Ui.gap(body,12);}
        if(accents.isEmpty())body.addView(Ui.prose(a,L.t("这条原始读音或音型无法可靠配对，暂不生成图示；保留原库内容，不猜测补齐。"),14,Ui.INK));
        else for(int n:accents){body.addView(Ui.bold(a,(word==null?L.t("三拍示意 · "):L.t("原库列出的音型 · "))+n+L.t(" 型"),14,Ui.GREEN));HorizontalScrollView row=new HorizontalScrollView(a);row.addView(new Plot(a,morae,n),new HorizontalScrollView.LayoutParams(Ui.dp(a,(morae.size()+1)*56+24),Ui.dp(a,112)));body.addView(row);Ui.gap(body,14);}
        body.addView(Ui.prose(a,L.t("词条标记来自内置 egg rolls，多个圈号按原库列为多种音型，不自动挑选唯一答案。基本原理参考东京大学 OJAD 使用说明；本页离线可看，不访问网络。"),11,Ui.MUTED));ScrollView scroll=new ScrollView(a);scroll.addView(body);AlertDialog d=new ThemedDialog.Builder(a).setView(scroll).setPositiveButton(L.t("明白了"),null).create();d.show();Ui.dialogWindow(d,a);
    }
    @android.annotation.SuppressLint("ViewConstructor")
    private static final class Plot extends View {
        final java.util.List<String> morae;final int accent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Plot(Context c,java.util.List<String> values,int n){super(c);morae=values;accent=n;setMinimumWidth(Ui.dp(c,(values.size()+1)*56+24));setMinimumHeight(Ui.dp(c,112));setContentDescription(L.t("音型 ")+n+L.t("，按拍显示高低，最后一格为助词"));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,scale=getResources().getDisplayMetrics().scaledDensity;paint.setColor(Ui.GREEN);paint.setStrokeWidth(2*d);float px=0,py=0;
            for(int i=0;i<=morae.size();i++){boolean high=i==morae.size()?accent==0:PitchPattern.high(i,accent);float x=(28+56*i)*d,y=(high?25:55)*d;if(i>0)c.drawLine(px,py,x,y,paint);c.drawCircle(x,y,4*d,paint);px=x;py=y;}
            paint.setColor(Ui.INK);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.min(14*scale,19*d));for(int i=0;i<=morae.size();i++)c.drawText(i==morae.size()?L.t("助词"):morae.get(i),(28+56*i)*d,91*d,paint);
        }
    }
}

package com.myles.cijian;

import android.app.*;
import android.view.Gravity;
import android.widget.*;

/** 基础假名参考使用文字排版，确保深浅主题均清晰可读。 / Basic kana reference, laid out as text so it remains legible in either theme. */
final class KanaGuide {
    static final String[] BASIC={
        "あ:a い:i う:u え:e お:o","か:ka き:ki く:ku け:ke こ:ko",
        "さ:sa し:shi す:su せ:se そ:so","た:ta ち:chi つ:tsu て:te と:to",
        "な:na に:ni ぬ:nu ね:ne の:no","は:ha ひ:hi ふ:fu へ:he ほ:ho",
        "ま:ma み:mi む:mu め:me も:mo","や:ya - ゆ:yu - よ:yo",
        "ら:ra り:ri る:ru れ:re ろ:ro","わ:wa - - - を:o","ん:n - - - -"};
    static final String[] VOICED={
        "が:ga ぎ:gi ぐ:gu げ:ge ご:go","ざ:za じ:ji ず:zu ぜ:ze ぞ:zo",
        "だ:da ぢ:ji づ:zu で:de ど:do","ば:ba び:bi ぶ:bu べ:be ぼ:bo",
        "ぱ:pa ぴ:pi ぷ:pu ぺ:pe ぽ:po"};
    static final String[] COMBINED={
        "きゃ:kya きゅ:kyu きょ:kyo","しゃ:sha しゅ:shu しょ:sho",
        "ちゃ:cha ちゅ:chu ちょ:cho","にゃ:nya にゅ:nyu にょ:nyo",
        "ひゃ:hya ひゅ:hyu ひょ:hyo","みゃ:mya みゅ:myu みょ:myo",
        "りゃ:rya りゅ:ryu りょ:ryo","ぎゃ:gya ぎゅ:gyu ぎょ:gyo",
        "じゃ:ja じゅ:ju じょ:jo","びゃ:bya びゅ:byu びょ:byo",
        "ぴゃ:pya ぴゅ:pyu ぴょ:pyo"};
    static String katakana(String text){StringBuilder out=new StringBuilder();for(char c:text.toCharArray())out.append(c>='ぁ'&&c<='ゖ'?(char)(c+0x60):c);return out.toString();}
    static void show(Activity activity,Speech speech){
        LinearLayout root=Ui.column(activity);Ui.pad(root,14);
        root.addView(Ui.text(activity,L.t("上：平假名　下：片假名与罗马字\n点击假名可试听，需要可用的日语语音引擎。"),12,Ui.MUTED));Ui.gap(root,8);
        Spinner category=new ChoiceSpinner(activity);category.setContentDescription(L.t("五十音分类"));category.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{L.t("清音 · 从这里开始"),L.t("浊音与半浊音"),L.t("拗音 · 小ゃゅょ组合"),L.t("零基础读音说明")}));root.addView(category,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(activity);LinearLayout chart=Ui.column(activity);scroll.addView(chart);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{
            int index=category.getSelectedItemPosition();chart.removeAllViews();scroll.scrollTo(0,0);
            if(index==3){
                TextView guide=Ui.text(activity,L.t("平假名和片假名表示同一套声音。平假名常见于语法和日语词汇，片假名常见于外来词等。建议先学清音，再学浊音、半浊音和组合。\n\n五个元音 a、i、u、e、o 对应「あいうえお」。罗马字只辅助认读，不等同于汉语拼音，请结合试听模仿。\n\n清音表有 46 个基本假名；空位不是遗漏。「を」作助词时通常读 o，也常用 wo 表示写法。「ん」是拨音，单独占一拍。\n\n右上角加「゛」形成浊音（か→が），加「゜」形成半浊音（は→ぱ）。现代标准语中，ぢ／じ、づ／ず通常分别同音。\n\n小「ゃゅょ」与前一个假名合成一拍：きゃ kya。普通大小的「きや ki-ya」是两拍。\n\n小「っ／ッ」是促音：在下一个辅音前停顿一拍，如 きって kitte。不是把「つ tsu」完整读出来。\n\n长音要延长一拍：おばさん与おばあさん不同。片假名常用「ー」，如 コーヒー。平假名常见「ああ／いい／うう／えい／おう」等写法，具体读法要随词学习。\n\n助词读音：は→wa、へ→e、を→o；其他位置通常按假名本音读。\n\n这是入门查阅表，不含笔顺动画或完整发音课程。试听沿用手机 TTS，单个假名的合成效果可能与真人示范不同。"),14,Ui.INK);
                guide.setLineSpacing(Ui.dp(activity,4),1);chart.addView(guide);return;
            }
            String[] rows=index==0?BASIC:index==1?VOICED:COMBINED;
            if(index==2)chart.addView(Ui.text(activity,L.t("小假名与前一个假名一起读，合计一拍。"),12,Ui.MUTED));
            for(String row:rows){
                LinearLayout line=Ui.row(activity);
                for(String token:row.split(" ")){
                    LinearLayout cell=Ui.column(activity);cell.setGravity(Gravity.CENTER);cell.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));
                    if(!token.equals("-")){
                        String[] parts=token.split(":");String kana=parts[0];
                        TextView hira=Ui.bold(activity,kana,19,Ui.INK);hira.setGravity(Gravity.CENTER);cell.addView(hira);
                        TextView kata=Ui.text(activity,katakana(kana),16,Ui.GREEN);kata.setGravity(Gravity.CENTER);cell.addView(kata);
                        TextView roman=Ui.text(activity,parts[1],11,Ui.MUTED);roman.setGravity(Gravity.CENTER);cell.addView(roman);
                        cell.setBackground(Ui.shape(Ui.PALE,8,activity));cell.setContentDescription(kana+"、"+katakana(kana)+"、"+parts[1]+L.t("，点击试听"));cell.setOnClickListener(v->speech.speak(kana.equals("を")?"お":kana,true));
                    }
                    LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-2,1);layout.setMargins(Ui.dp(activity,2),Ui.dp(activity,2),Ui.dp(activity,2),Ui.dp(activity,2));line.addView(cell,layout);
                }chart.addView(line);
            }
        };
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){render.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
        AlertDialog dialog=new ThemedDialog.Builder(activity).setTitle(L.t("零基础 · 五十音图")).setView(root).setPositiveButton(L.t("关闭"),(d,w)->speech.stop()).create();
        dialog.setOnDismissListener(d->speech.stop());dialog.setOnShowListener(d->{dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*0.85));render.run();});dialog.show();
    }
}

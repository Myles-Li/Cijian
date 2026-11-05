package com.myles.cijian;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static int BG,INK,MUTED,GREEN,PALE,SURFACE,ON_GREEN;
    static { palette(false); }
    static boolean night(Context c){
        String selected=new Prefs(c).theme();
        return selected.equals("DARK")||selected.equals("AUTO")&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    static int themeResource(Context c){
        Prefs p=new Prefs(c);int index=Accent.nearest(p.accent(),p.customColor());
        int[] light={R.style.AppThemeGreenLight,R.style.AppThemeBlueLight,R.style.AppThemeTealLight,R.style.AppThemePurpleLight,R.style.AppThemeOrangeLight,R.style.AppThemeRoseLight,R.style.AppThemeBlackLight,R.style.AppThemeGrayLight,R.style.AppThemeIndigoLight,R.style.AppThemeAmberLight,R.style.AppThemeRedLight,R.style.AppThemeYellowLight};
        int[] dark={R.style.AppThemeGreenDark,R.style.AppThemeBlueDark,R.style.AppThemeTealDark,R.style.AppThemePurpleDark,R.style.AppThemeOrangeDark,R.style.AppThemeRoseDark,R.style.AppThemeBlackDark,R.style.AppThemeGrayDark,R.style.AppThemeIndigoDark,R.style.AppThemeAmberDark,R.style.AppThemeRedDark,R.style.AppThemeYellowDark};
        return night(c)?dark[index]:light[index];
    }
    static void applyTheme(Context c){L.init(c);Prefs p=new Prefs(c);palette(night(c),p.accent(),p.customColor());}
    private static void palette(boolean dark){palette(dark,"BLACK","#32715F");}
    private static void palette(boolean dark,String accent,String custom){
        int[] p=Accent.palette(accent,custom,dark);GREEN=p[0];BG=p[1];SURFACE=p[2];PALE=p[3];
        boolean neutral=accent.equals("BLACK");INK=Color.parseColor(dark?(neutral?"#E7E7E7":"#E6EFE9"):(neutral?"#202124":"#20302A"));MUTED=Color.parseColor(dark?(neutral?"#AEAEAE":"#A0B3A7"):(neutral?"#6E6E6E":"#707E73"));ON_GREEN=Color.parseColor(dark?(neutral?"#161616":"#15202B"):"#FFFFFF");
    }
    static void tint(View v){
        android.content.res.ColorStateList color=android.content.res.ColorStateList.valueOf(GREEN);
        if(v instanceof android.widget.CompoundButton)((android.widget.CompoundButton)v).setButtonTintList(color);
        if(v instanceof android.widget.Switch){((android.widget.Switch)v).setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{GREEN,MUTED}));}
        if(v instanceof android.widget.ProgressBar){((android.widget.ProgressBar)v).setProgressTintList(color);if(v instanceof android.widget.SeekBar)((android.widget.SeekBar)v).setThumbTintList(color);}
        if(v instanceof android.widget.EditText){
            android.widget.EditText input=(android.widget.EditText)v;input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHighlightColor(PALE);
            if(input.getBackground() instanceof GradientDrawable)input.setBackgroundTintList(null);else input.setBackgroundTintList(color);
        }
        if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)tint(g.getChildAt(i));}
    }
    static void dialogWindow(android.app.AlertDialog dialog,Context c){
        android.view.Window window=dialog.getWindow();if(window==null)return;
        window.setBackgroundDrawable(shape(SURFACE,20,c));
        window.setLayout(Math.min(dp(c,480),c.getResources().getDisplayMetrics().widthPixels-dp(c,32)),android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){return L.t("未知版本");}}
    static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static GradientDrawable shape(int color,float radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String value,float size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    static TextView prose(Context c,String value,float size,int color){TextView t=text(c,value,size,color);t.setLineSpacing(dp(c,4),1.05f);return t;}
    static TextView heading(Context c,String value,float size,int color){TextView t=text(c,value,size,color);t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));t.setLineSpacing(dp(c,2),1);return t;}
    static TextView bold(Context c,String value,float size,int color){TextView t=text(c,value,size,color);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static TextView button(Context c,String value,boolean primary,View.OnClickListener click){
        TextView t=bold(c,value,14,primary?ON_GREEN:GREEN);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));
        t.setBackground(shape(primary?GREEN:PALE,14,c));t.setMinHeight(dp(c,48));t.setOnClickListener(click);t.setFocusable(true);return t;
    }
    // 例句操作使用内容宽度，保持44dp触控高度。 / Content-width example action retains a 44dp touch target.
    static TextView exampleAction(Context c,java.util.function.BooleanSupplier speaking,View.OnClickListener click){
        TextView t=button(c,"",false,click);t.setTextSize(12);t.setMinHeight(dp(c,44));t.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));t.setBackground(shape(PALE,10,c));t.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));exampleActionState(t,speaking.getAsBoolean());
        // 仅已挂载按钮轮询状态；不触发语音回调或重建卡片。 / Poll only attached actions, without speech callbacks or card rebuilds.
        Runnable[] pulse={null};pulse[0]=()->{if(t.isAttachedToWindow()){exampleActionState(t,speaking.getAsBoolean());t.postDelayed(pulse[0],250);}};
        t.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){t.post(pulse[0]);}public void onViewDetachedFromWindow(View v){t.removeCallbacks(pulse[0]);}});return t;
    }
    static void exampleActionState(TextView view,boolean speaking){if(view==null)return;view.setText((speaking?"■  ":"▷  ")+L.t(speaking?"停止朗读":"朗读例句"));view.setContentDescription(L.t(speaking?"停止朗读":"朗读例句"));}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static void gap(LinearLayout l,int dp){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,Ui.dp(l.getContext(),dp)));}
    static void weighted(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));}
    // 输入框随文字增高，最小高度保留触摸区域。 / Grow inputs with their text while retaining a minimum touch area.
    static android.widget.LinearLayout.LayoutParams inputSize(android.widget.EditText input,int minimumDp){input.setMinHeight(dp(input.getContext(),minimumDp));return new android.widget.LinearLayout.LayoutParams(-1,-2);}
    static void pad(View v,int n){int d=dp(v.getContext(),n);v.setPadding(d,d,d,d);}
    static String reading(Word w){return Pronunciation.origin(w.reading)?L.t("词源：")+w.reading:w.reading;}
    static String meaning(Word w,Prefs p){return p.traditional()&&!w.traditional.isEmpty()?w.traditional:w.meaning;}
    static String grammar(Word w){return w.pos.isEmpty()?L.t("词性未标注"):w.pos;}
}

package com.myles.kotobafloat;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.LinearLayout;

/** 顶部锁图标避免依赖系统emoji字体。 / Draw the header lock without depending on emoji fonts. */
@android.annotation.SuppressLint("ViewConstructor")
final class PositionLock extends View {
    private final boolean locked;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    PositionLock(Context context,boolean locked,OnClickListener click){
        super(context);this.locked=locked;setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context,36),Ui.dp(context,44)));setFocusable(true);setContentDescription(L.t(locked?"解锁悬浮窗位置":"锁定悬浮窗位置"));setBackground(Ui.shape(locked?Ui.PALE:Ui.SURFACE,9,context));setOnClickListener(click);
    }
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(locked);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;canvas.save();canvas.translate((getWidth()-24*density)/2,(getHeight()-24*density)/2);canvas.scale(density,density);paint.setColor(Ui.GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawRoundRect(new RectF(5,10,19,21),2,2,paint);canvas.drawArc(new RectF(8,3,16,15),locked?180:205,locked?180:150,false,paint);canvas.drawLine(12,14,12,17,paint);canvas.restore();
    }
}

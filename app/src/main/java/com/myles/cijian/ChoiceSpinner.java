package com.myles.cijian;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** 保留已有选择回调，使用尺寸受限且跟随主题的选项弹窗。 / Existing spinner callbacks with a bounded, theme-aware choice dialog. */
@android.annotation.SuppressLint("ViewConstructor")
final class ChoiceSpinner extends Spinner {
    private java.util.function.IntConsumer onChoice;
    // 允许自定义项再次被选择，不依赖选中索引变化。 / Allow selecting a custom item again without an index change.
    void setOnChoice(java.util.function.IntConsumer callback){onChoice=callback;}
    ChoiceSpinner(Context c){super(c,Spinner.MODE_DIALOG);setBackground(Ui.shape(Ui.SURFACE,12,c));setPadding(Ui.dp(c,12),0,Ui.dp(c,32),0);setMinimumHeight(Ui.dp(c,48));}
    @Override public void setAdapter(SpinnerAdapter source){
        if(source==null){super.setAdapter(null);return;}
        super.setAdapter(new BaseAdapter(){
            public int getCount(){return source.getCount();}public Object getItem(int p){return source.getItem(p);}public long getItemId(int p){return source.getItemId(p);}
            public View getView(int p,View recycled,ViewGroup parent){View view=source.getView(p,recycled,parent);if(view instanceof TextView){TextView text=(TextView)view;text.setTextColor(Ui.INK);text.setTextSize(14);text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(0,0,0,0);text.setMinHeight(Ui.dp(getContext(),48));text.setBackgroundColor(android.graphics.Color.TRANSPARENT);text.setLayoutParams(new ViewGroup.LayoutParams(-1,-2));}return view;}
            public View getDropDownView(int p,View recycled,ViewGroup parent){return getView(p,recycled,parent);}
        });
    }
    @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Ui.MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1.5f));float x=getWidth()-Ui.dp(getContext(),19),y=getHeight()/2f;Path p=new Path();p.moveTo(x-Ui.dp(getContext(),4),y-Ui.dp(getContext(),2));p.lineTo(x,y+Ui.dp(getContext(),2));p.lineTo(x+Ui.dp(getContext(),4),y-Ui.dp(getContext(),2));canvas.drawPath(p,paint);}
    // 直接调用 Spinner.performClick 还会打开原生弹出菜单，因此只发送此对话框的点击事件。 / Calling Spinner.performClick would also open its native popup. Emit the click event for this dialog instead.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean performClick(){
        if(!isEnabled()||getAdapter()==null)return false;
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
        Context c=getContext();LinearLayout root=Ui.column(c);Ui.pad(root,20);CharSequence name=getContentDescription();root.addView(Ui.heading(c,name==null?L.t("请选择"):name.toString(),20,Ui.INK));Ui.gap(root,14);
        ScrollView scroll=new ScrollView(c);LinearLayout choices=Ui.column(c);scroll.addView(choices);root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(c,53)*getAdapter().getCount(),Math.min(Ui.dp(c,420),(int)(getResources().getDisplayMetrics().heightPixels*.55f)))));
        AlertDialog dialog=new ThemedDialog.Builder(c).setView(root).setNegativeButton(L.t("取消"),null).create();
        for(int i=0;i<getAdapter().getCount();i++){
            final int index=i;String value=String.valueOf(getAdapter().getItem(i));boolean selected=i==getSelectedItemPosition();
            TextView option=Ui.button(c,(selected?"✓  ":"    ")+value,false,v->{dialog.dismiss();if(onChoice!=null)onChoice.accept(index);else setSelection(index);});option.setGravity(Gravity.CENTER_VERTICAL);option.setBackground(Ui.shape(selected?Ui.PALE:Ui.SURFACE,10,c));option.setContentDescription(value+(selected?L.t("，已选择"):""));choices.addView(option,new LinearLayout.LayoutParams(-1,-2));if(selected)option.post(()->scroll.scrollTo(0,option.getTop()));Ui.gap(choices,5);
        }
        dialog.show();Ui.dialogWindow(dialog,c);return true;
    }
}

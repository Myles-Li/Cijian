package com.myles.cijian;

import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;

/** 在学习页面接管明确横滑，纵滑、点击与取消均保留。 / Intercept clear horizontal study-page swipes; preserve scrolling, taps and cancellation. */
@android.annotation.SuppressLint("ViewConstructor")
final class SwipeCardLayout extends LinearLayout {
    interface Listener {void touching(boolean touching);void flip(int direction);}
    private final Listener listener;
    private final float slop,minimum;
    private float downX,downY;
    private boolean blocked,swiping,touching,multiple;
    SwipeCardLayout(Context context,Listener listener){
        super(context);this.listener=listener;setOrientation(VERTICAL);setClickable(true);slop=ViewConfiguration.get(context).getScaledTouchSlop();minimum=Ui.dp(context,48);
    }
    private void release(){if(touching){touching=false;listener.touching(false);}}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();blocked=false;swiping=false;multiple=false;touching=true;listener.touching(true);}
        if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN||event.getActionMasked()==MotionEvent.ACTION_POINTER_UP){blocked=true;multiple=true;}
        try{return super.dispatchTouchEvent(event);}finally{if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)release();}
    }
    // 无可点击子控件时MOVE直接到onTouchEvent；两条路径共用方向判定。 / Moves on plain text reach onTouchEvent directly; both paths share direction tracking.
    private void track(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!blocked){
            float dx=Math.abs(event.getRawX()-downX),dy=Math.abs(event.getRawY()-downY);
            if(dy>slop&&dy>=dx){blocked=true;return;}
            if(dx>slop&&dx>dy*1.5f){swiping=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        }
    }
    // 第二指从一开始就取消子控件点击，不只阻止翻词。 / A second finger cancels child clicks as well as navigation.
    @Override public boolean onInterceptTouchEvent(MotionEvent event){track(event);return swiping||multiple;}
    @Override public boolean onTouchEvent(MotionEvent event){
        track(event);
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){blocked=true;return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
            boolean advance=swiping&&!blocked&&event.getPointerCount()==1&&Math.abs(dx)>=minimum&&Math.abs(dx)>Math.abs(dy)*1.5f;
            release();if(advance)listener.flip(dx<0?1:-1);else if(!swiping&&!blocked)performClick();return true;
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
}

package com.myles.kotobafloat;

import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** 整张卡片均可拖动，普通点击继续交给子控件。 / Intercepts drags over the whole card; ordinary taps still reach each child control. */
// 通过代码创建并接收窗口位置回调，不从 XML 加载。 / Constructed programmatically with a window-position callback; never inflated from XML.
@android.annotation.SuppressLint("ViewConstructor")
final class DragLayout extends LinearLayout {
    interface Listener {
        void start();
        void move(float dx,float dy);
        void finish(boolean cancelled);
    }
    private final Listener listener;
    private final float slop;
    private float downX,downY;
    private boolean dragging,scrollHit,positionLocked,touchMoved,scrollGesture,multiTouch;
    void setPositionLocked(boolean locked){positionLocked=locked;}
    DragLayout(Context context,Listener listener) {
        super(context);this.listener=listener;
        slop=Math.min(ViewConfiguration.get(context).getScaledTouchSlop(),Ui.dp(context,4));
        setOrientation(VERTICAL);setClickable(true);
    }
    private void begin(MotionEvent event) {
        downX=event.getRawX();downY=event.getRawY();dragging=false;touchMoved=false;scrollGesture=false;multiTouch=false;
        scrollHit=insideScrollable(this,downX,downY);
    }
    private boolean shouldDrag(MotionEvent event) {
        if(positionLocked||multiTouch)return false;
        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
        if(scrollGesture)return false;
        if(dx*dx+dy*dy<=slop*slop)return false;
        if(scrollHit&&Math.abs(dy)>Math.abs(dx)){scrollGesture=true;return false;}
        return true;
    }
    // 滚动区保留纵向手势，父卡片继续观察横向起手；一旦纵向滚动，不再中途抢走。 / Keep vertical scrolling; observe horizontal starts without stealing an established scroll.
    @Override public void requestDisallowInterceptTouchEvent(boolean disallow){
        if(disallow&&scrollHit&&!positionLocked)return;
        super.requestDisallowInterceptTouchEvent(disallow);
    }
    private void startDrag() {
        if(!dragging){dragging=true;listener.start();getParent().requestDisallowInterceptTouchEvent(true);}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:begin(event);return false;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                multiTouch=true;touchMoved=true;if(dragging){listener.finish(true);dragging=false;}return true;
            case MotionEvent.ACTION_MOVE:
                if(multiTouch)return true;
                if(shouldDrag(event)){startDrag();return true;}return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(dragging){listener.finish(event.getActionMasked()==MotionEvent.ACTION_CANCEL);dragging=false;}return false;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:begin(event);return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                multiTouch=true;touchMoved=true;if(dragging){listener.finish(true);dragging=false;}return true;
            case MotionEvent.ACTION_MOVE:
                if(multiTouch)return true;
                float mx=event.getRawX()-downX,my=event.getRawY()-downY;if(mx*mx+my*my>slop*slop)touchMoved=true;
                if(!dragging&&shouldDrag(event))startDrag();
                if(dragging)listener.move(event.getRawX()-downX,event.getRawY()-downY);
                return true;
            case MotionEvent.ACTION_UP:
                if(dragging){listener.move(event.getRawX()-downX,event.getRawY()-downY);listener.finish(false);dragging=false;}
                else if(!touchMoved)performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if(dragging)listener.finish(true);dragging=false;return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private static boolean insideScrollable(View view,float x,float y) {
        if(view.getVisibility()!=VISIBLE)return false;
        if(view instanceof ScrollView) {
            int[] origin=new int[2];view.getLocationOnScreen(origin);
            return x>=origin[0]&&x<origin[0]+view.getWidth()&&y>=origin[1]&&y<origin[1]+view.getHeight();
        }
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(insideScrollable(group.getChildAt(i),x,y))return true;
        }
        return false;
    }
}

package com.myles.cijian;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

/** 保留原生对话行为，统一外观。 / Keep native dialog behavior with a consistent visual style. */
final class ThemedDialog {
    static final class Builder extends AlertDialog.Builder {
        private final Context context;
        Builder(Context context){super(context);this.context=context;}
        @Override public Builder setTitle(CharSequence text){TextView title=Ui.heading(context,text.toString(),19,Ui.INK);title.setPadding(Ui.dp(context,24),Ui.dp(context,22),Ui.dp(context,24),Ui.dp(context,10));setCustomTitle(title);return this;}
        @Override public Builder setTitle(int id){return setTitle(context.getText(id));}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            android.app.Activity host=activity(context);
            // 在拥有者销毁前释放窗口，保留调用者自己的关闭回调。 / Release windows before their owner is destroyed without replacing caller dismiss listeners.
            android.app.Application.ActivityLifecycleCallbacks lifecycle=host==null?null:new android.app.Application.ActivityLifecycleCallbacks(){
                public void onActivityCreated(android.app.Activity a,android.os.Bundle state){}public void onActivityStarted(android.app.Activity a){}public void onActivityResumed(android.app.Activity a){}public void onActivityPaused(android.app.Activity a){}public void onActivityStopped(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle state){}
                public void onActivityDestroyed(android.app.Activity a){if(a==host&&dialog.isShowing())dialog.dismiss();}
            };
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                public void onViewAttachedToWindow(View v){if(lifecycle!=null)host.getApplication().registerActivityLifecycleCallbacks(lifecycle);v.post(()->style(dialog,context));}
                public void onViewDetachedFromWindow(View v){if(lifecycle!=null)host.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);}
            });
            return dialog;
        }
    }
    private static android.app.Activity activity(Context context){
        while(context instanceof android.content.ContextWrapper){if(context instanceof android.app.Activity)return (android.app.Activity)context;Context next=((android.content.ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return null;
    }
    private static void style(AlertDialog dialog,Context context){
        if(!dialog.isShowing())return;
        Ui.dialogWindow(dialog,context);
        TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null){message.setTextColor(Ui.INK);message.setTextSize(15);message.setLineSpacing(Ui.dp(context,4),1.06f);message.setTextIsSelectable(true);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            android.widget.Button b=dialog.getButton(which);if(b!=null){b.setTextColor(Ui.GREEN);b.setAllCaps(false);b.setMinHeight(Ui.dp(context,48));}
        }
        if(dialog.getListView()!=null){dialog.getListView().setBackgroundColor(Ui.SURFACE);dialog.getListView().setDivider(null);}
        Ui.tint(dialog.getWindow().getDecorView());
    }
}

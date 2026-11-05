package com.myles.cijian;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
/** 就近解释实际排程，不另建一套学习算法。 / Explain the real schedule beside the controls; do not introduce a second study algorithm. */
final class ReviewGuide {
    static final String[][] SECTIONS={
        {"每天从哪里开始？","日常只需点「开始今日任务」。先尝试回忆，点「揭晓答案」，再按真实情况评分。App 内和悬浮窗共用这一轮，退出时已提交的评分保留。"},
        {"浏览、熟悉和正式复习是三回事","浏览只记录你接触过哪些词，不会自动安排到期复习。手动「熟悉」是你自己的筛选标记，也不改变正式复习时间。第一次在正式任务中评分，才算这个词开始正式记忆。"},
        {"今日任务怎样选词？","先取当前范围内已经到期的正式复习词，再用当天剩余的新学额度补新词。新词可以是你尚未浏览过的词。\n\n例如：每日新学10词、每批20词、当前有12词到期，且今天还没正式新学过，第一轮最多12个旧词加8个新词；下一轮最多再补2个新词，并继续优先处理当时到期的词。"},
        {"每批词数不是每日上限","每日新学目标只限制当天第一次正式评分的新词，不限制旧词复习。每批词数控制这一轮先安排多少个不同词，可选预设或自定义1～50词；改变后只影响新安排。固定浏览分组和「跳过熟悉词」不裁切正式任务；等级、词频、学习来源和自选范围仍然生效。"},
        {"三个评分会怎样安排？","没想起：约1分钟后到期。\n模糊：约10分钟后到期。\n记得：按阶段逐步延长，通常为1、3、7、14、30天。\n\n刚忘记后很快答对，会先约10分钟再确认，不直接当作稳定记住。这里是应用现有的简单间隔规则，不是永久掌握保证。"},
        {"为什么本轮又出现同一个词？","评为「没想起」后，若后面还有至少两个题目，通常会在本轮末尾追加一次；小批到期复习和易忘练习不追加。每个词最多追加一次，所以实际题目数可能大于设置的每批词数，最多100题。\n\n本轮再出现和正式到期时间分别计算。评分会立即保存；暂时跳过不评分，也不算完成。退出可下次继续；放弃本轮不会撤销已经保存的评分。"},
        {"其他入口什么时候用？","只复习到期词：不加新词，会更新正式复习时间。\n先复习10个：到期词的小批正式复习，最多10词。\n自由练习：从接触过的词里练习，不改正式时间。\n四选一：练识义，不改正式时间或易忘证据。\n易忘词：最多练5词，更新独立的易忘表现，不改正式时间或新学额度。\n\n不确定怎么选时，继续用「开始今日任务」即可。"},
        {"两个“新词”数字为什么不同？","首页的「今日初见」指首次接触；复习的「今日新学」指首次正式评分，分别计算。今日已练按当天正式评分过的不同词去重。到期词做完后，未到时间的词不必反复刷；可以稍后回来或自由练习。"},
    };
    private static java.lang.ref.WeakReference<AlertDialog> shown=new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Context> owner=new java.lang.ref.WeakReference<>(null);
    static boolean showing(Context c){AlertDialog dialog=shown.get();return owner.get()==c&&dialog!=null&&dialog.isShowing();}
    static void close(Context c){AlertDialog dialog=shown.get();if(owner.get()==c&&dialog!=null)dialog.dismiss();}
    static void show(Context c){
        AlertDialog previous=shown.get();if(previous!=null&&previous.isShowing()){if(owner.get()==c)return;previous.dismiss();}
        LinearLayout panel=Ui.column(c);Ui.pad(panel,20);panel.addView(Ui.prose(c,L.t("先回忆 → 揭晓 → 诚实评分 → 到期再来"),15,Ui.GREEN));Ui.gap(panel,16);
        for(String[] section:SECTIONS){panel.addView(Ui.heading(c,L.t(section[0]),16,Ui.INK));Ui.gap(panel,8);panel.addView(Ui.prose(c,L.t(section[1]),13,Ui.MUTED));Ui.gap(panel,20);}
        ScrollView scroll=new ScrollView(c);scroll.addView(panel);AlertDialog dialog=new ThemedDialog.Builder(c).setTitle(L.t("复习怎么用？")).setView(scroll).setPositiveButton(L.t("明白了"),null).create();
        // 服务的帮助窗口使用已获授权的悬浮类型，并与回忆界面一起关闭。 / Service-owned help uses the authorized overlay type and closes with the recall surface.
        if(!(c instanceof android.app.Activity))dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        shown=new java.lang.ref.WeakReference<>(dialog);owner=new java.lang.ref.WeakReference<>(c);dialog.setOnDismissListener(v->{if(shown.get()==dialog){shown.clear();owner.clear();}});
        try{dialog.show();Ui.dialogWindow(dialog,c);}catch(RuntimeException unavailable){if(shown.get()==dialog){shown.clear();owner.clear();}android.widget.Toast.makeText(c,L.t("说明暂时无法显示，请从 App 的复习页打开。"),android.widget.Toast.LENGTH_LONG).show();}
    }
    private ReviewGuide(){}
}

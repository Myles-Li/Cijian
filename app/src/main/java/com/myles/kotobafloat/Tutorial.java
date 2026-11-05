package com.myles.kotobafloat;

import android.app.*;
import android.widget.*;

/** 提供离线场景教程，让主要页面专注学习。 / Offline, task-oriented help. Keep the main screens focused on studying. */
final class Tutorial {
    static final String[][] TOPICS={
        {"第一次使用 · 三步开始","1. 在「学习内容」选择等级和词频。不确定时可从 N5 或不分组开始。\n\n2. 点「App 内连续浏览」可直接学习，无需悬浮权限。认真记忆也可在 App 内完成每日任务。\n\n也可以点「开启悬浮学习」，按系统提示允许显示在其他应用上层；建议允许通知。\n\n3. 返回后拖动小窗，在其他应用里继续看词。没有语音也可以先学习文字。"},
        {"悬浮窗 · 移动、翻词与暂停","词头、释义和空白处可拖动；详情内容上下滑动查看，左右起手拖动可移动整个窗口。纵向滚动开始后不会中途变成移窗，位置锁定时仍能查看内容。\n\n‹／› 翻词；Ⅱ／▶ 暂停或继续自动轮换；「音」手动朗读；☆ 收藏；「熟」标记熟悉并跳到下一词。近期标记可在 8 秒内撤销。\n\n手动翻词朗读默认开启，暂停轮换时也按此开关播放。它与「自动朗读」独立。\n\n点释义隐藏或揭晓，点详情看词性和例句。点 ● 收球，点小球展开。"},
        {"浏览一遍与认真记忆怎么选","浏览一遍：先对整个范围有印象。目标只统计首次接触，不会限制轮换或强迫分组。\n\n两种节奏都可在 App 内或悬浮窗使用。学习页选节奏，进入相应界面；学习中可转移，评分与位置共用。App 浏览初始暂停，点播放开始自动切词。悬浮正式回忆收球或锁屏会停止语音，不自动揭晓。\n\n认真记忆：完成每日回忆任务，到期复习优先，再学少量新词。先尝试回忆，再揭晓并诚实评分。每批可选预设或自定义1～50词；小批到期复习仍最多 10 词，易忘练习最多 5 词。\n\n四选一是独立识义练习，结果不改变正式到期时间。复习自动读词默认开启，可在复习页关闭；反向回忆在揭晓后才读日语。\n\n「今日遇见 · 全库」含回看旧词；「今日初见」只算首次接触；范围不同，数字可以不同。手动熟悉不是自动判定掌握。"},
        {"易忘词 · 为什么会进出名单","从 1.6 起记录：同一词在两个不同日期评为「没想起」后进入名单。同一天反复忘记只算一次，不会因一次失误就贴标签。旧版没有这些证据，不追溯猜测。复习中断后可继续上轮，或主动放弃本轮重新安排；已评分立即保存。\n\n可在复习页主动「练 5 个易忘词」，每批最多五词，不追加题目，不占每日正式新学额度，也不推迟正式复习时间。\n\n最近一次遗忘之后，连续在三个不同日期评为「记得」才退出名单。同日立刻答对不算稳定回忆；「没想起」或「模糊」会中断这段连续表现。"},
        {"搜索、词频与分组","搜索支持日语、中文、平假名／片假名互查和罗马字，例如 taberu 可找「食べる」。长音可按假名或输入 ko-hi-。罗马字是辅助输入，不是翻译功能。\n\nN5／N4 原词库没有词频分档，使用全部词频；N3～N1 可按原库高、中、低频选择。\n\n常用范围会分别记住浏览位置、组号和随机轮次，切回可继续；学习记录共用。默认不分组。固定词数组反复循环，点「换下一组」手动换组；自选组可跨等级。只看收藏或熟悉词时，没有匹配内容会显示清楚提示，可一键取消筛选。"},
        {"朗读与没有 Google Play 的手机","先在「语音与朗读」查看引擎是否就绪。自动朗读控制首词、自动切词和恢复播放；手动翻词朗读独立控制左右箭头，默认开启。\n\n发音页可分别试读日语和中文。离线能力只按引擎报告说明，请断网试读确认。可设置词、例句的次数，以及中文译文、语速和停顿。自动轮换会等待整段朗读结束。\n\n没有 Google Play：在「获取日语语音」打开作者国内下载页，按三步说明安装兼容引擎、配置 ja-JP 日语声音、返回重新检测。第三方在线声音可能需要联网，离线文字学习不受影响。\n\n不强制修改手机全局默认引擎，也不自动下载大语音包。"},
        {"锁屏、后台与运行状态","默认收球或锁屏后暂停学习；可在发音设置分别开启收球继续听、锁屏继续听，并开启自动朗读。锁屏时窗口隐藏，解锁后恢复。\n\n耳机断开或其他应用取得声音焦点时会停止声音并暂停轮换，避免突然外放；手动继续后才恢复。\n\n「运行状态检查」显示可检测的悬浮权限、通知、引擎和暂停原因。厂商自启动及严格省电策略无法直接确认，需要到系统设置检查。开机自启动还需应用内开关和系统允许。自动出现窗口后默认先暂停，不朗读、不切词，点播放后才开始。"},
        {"备份、换手机与恢复","设置 → 备份与帮助 → 备份学习记录，可分享文件、保存到下载，或自选位置。分享文件生成不代表已经发送成功，请确认副本已存到手机之外。\n\n恢复前会验证文件并展示只读预览，取消不会改记录。确认后合并标记、时间与次数，不重复相加；可选只导入记录或同时恢复设置。带修改时间的收藏／熟悉采用较新状态，旧标记冲突按你选择处理。易忘词采用较新状态，旧备份没有该项时保留本机数据。导入前保存本机快照，备份与帮助可撤回最近导入；导入后新增记录也会撤回。快照不能替代手机之外的备份。未完成复习不随备份迁移，恢复后重新安排。\n\n备份不含系统权限、语音引擎和语音包。卸载会清除本机记录，覆盖升级一般不需要卸载。"},
        {"语言、外观与常见疑问","「语言与外观」可选界面语言、跟随系统或手选浅／深色。默认黑白置顶，其余主题与自定义色号按色块选择，文字会保持清晰。界面语言不会翻译日语词条和中文释义。\n\n悬浮窗页可调宽度、透明度、释义行数和三种文字字号，字号面板实时预览，取消不改变设置。长词会完整换行；详情释义可以滚动。悬浮窗位置可锁定，锁定后仍能点按钮；可点顶部小锁或在悬浮窗设置里解锁。词条读音旁可点开音调说明；无法可靠配对的原库音型不会猜测画图。\n\n隐藏浏览进度后，可点首页「显示学习进度」恢复，也可在学习内容里重新开启。遇到不读、不切词或窗口不见，先打开运行状态检查。\n\nApp 内学习字号可在「语言与外观」或学习页的 Aa 按钮调整；与悬浮窗字号独立保存，继续跟随系统字号。连续浏览可在词卡左右滑动翻词，上下滑动查看内容；正式回忆仍须揭晓与评分。悬浮窗设置可调整详情内容高度，屏幕不足时会自动缩短。"}
    };
    static void show(Activity a,Runnable voiceHelp){
        LinearLayout root=Ui.column(a);Ui.pad(root,20);root.addView(Ui.heading(a,L.t("使用教程"),21,Ui.INK));Ui.gap(root,6);root.addView(Ui.prose(a,L.t("按你现在想做的事查阅，全部离线可看。"),13,Ui.MUTED));Ui.gap(root,12);
        ScrollView scroll=new ScrollView(a);LinearLayout list=Ui.column(a);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(a,470),(int)(a.getResources().getDisplayMetrics().heightPixels*.6f))));
        AlertDialog[] directory={null};for(int i=0;i<TOPICS.length;i++){String[] topic=TOPICS[i];int route=new int[]{1,2,1,1,1,4,3,5,0}[i];list.addView(Ui.button(a,L.t(topic[0])+"  ›",false,v->topic(a,topic,voiceHelp,()->{if(directory[0]!=null)directory[0].dismiss();if(a instanceof MainActivity)((MainActivity)a).goToSettings(route);})));Ui.gap(list,8);}
        list.addView(Ui.button(a,L.t("复习怎么用？"),false,v->ReviewGuide.show(a)));
        AlertDialog d=new ThemedDialog.Builder(a).setView(root).setPositiveButton(L.t("关闭"),null).create();directory[0]=d;d.show();Ui.dialogWindow(d,a);
    }
    private static void topic(Activity a,String[] topic,Runnable voiceHelp,Runnable settings){
        TextView text=Ui.prose(a,L.t(topic[1]),14,Ui.INK);Ui.pad(text,22);ScrollView scroll=new ScrollView(a);scroll.addView(text);AlertDialog.Builder builder=new ThemedDialog.Builder(a).setTitle(L.t(topic[0])).setView(scroll).setPositiveButton(L.t("返回目录"),null).setNegativeButton(L.t("前往相关设置"),(v,w)->settings.run());
        if(topic[0].startsWith("朗读"))builder.setNeutralButton(L.t("获取日语语音"),(d,w)->voiceHelp.run());AlertDialog d=builder.create();d.show();Ui.dialogWindow(d,a);
    }
}

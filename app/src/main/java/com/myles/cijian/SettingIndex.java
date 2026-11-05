package com.myles.cijian;

final class SettingIndex {
    // 依次为分类、可见定位标题及搜索别名，不重复创建设置控件。 / category, visible anchor, search aliases. No duplicate settings controls.
    static final String[][] ITEMS={
        {"0","界面语言","language English Japanese 日本語 中文 语言"},{"0","外观模式","主题 浅色 深色 夜间 跟随系统"},{"0","主题颜色","颜色 色号 黑白 彩虹 自定义"},{"0","App 内学习字号","字体 字号 读音 释义 大字 应用内 浏览 回忆 识义"},
        {"1","当前等级","日语 N1 N2 N3 N4 N5 范围"},{"1","词频范围","高频 中频 低频"},{"1","每天新接触目标","每天 新词 浏览 目标 进度"},{"1","显示学习进度","首页 隐藏 浏览 统计 找回"},{"1","跳过熟悉词","跳过 熟悉 排除"},{"1","先回忆，再看答案","隐藏答案 遮住 释义 揭晓"},{"1","繁体中文释义","繁体 简体 中文"},{"1","学习来源","收藏 熟悉 全部 筛选"},{"1","随机","顺序 排词 轮次"},{"1","分组循环","分组 组数 自选 自定义单词"},
        {"2","自动切词间隔","时间 秒 小数 轮换"},{"2","文字大小","字体 字号 读音 释义 大字"},{"2","窗口宽度","大小 宽度"},{"2","详情内容高度","详情 展开 长度 高度 滚动"},{"2","窗口不透明度","透明 颜色"},{"2","释义","行数 中文 显示"},{"2","锁定悬浮窗位置","锁定 拖动 移动 解锁"},{"2","贴边","吸附 边缘"},
        {"3","开机","自启动 重启 启动"},{"3","运行状态检查","权限 后台 通知 电池 省电 诊断 暂停"},
        {"4","复习时自动朗读","记忆 复习 发音 朗读"},{"4","手动翻词时朗读","左右 上一个 下一个 播音"},{"4","自动轮换时朗读","自动 首词 播放 继续"},{"4","收球后继续听","小球 音频"},{"4","锁屏后继续听","息屏 后台 听词"},{"4","单词朗读次数","重复 词语 次数"},{"4","例句朗读次数","重复 例句 次数"},{"4","单词后连读例句","例句 次数 朗读"},{"4","朗读中文译文","中文 翻译 日中"},{"4","朗读语速","速度 语音 快慢"},{"4","每段朗读后停顿","间隔 停顿"},{"4","试读日语","测试 离线 日语 声音"},{"4","试读中文","测试 离线 中文 声音"},{"4","日语语音引擎","高级 Google Play 引擎 TTS 安装"},
        {"5","备份学习记录","备份 分享 下载 导出"},{"5","恢复学习记录","导入 恢复 设置 合并"},{"5","撤回上次导入","撤回 快照 回退 恢复"},{"5","五十音图","入门 假名 五十音 零基础"},{"5","词性标记说明","语法 词性 自动词 他动词"},{"5","音调标记说明","音调 声调 圈号 高低"},{"6","关于词间","版本 来源 授权 词库"}
    };
    static final String[][] RESET={
        {"theme","accent","customColor","appWordSize","appReadingSize","appMeaningSize"},
        {"level","frequency","studyMode","favoritesOnly","hideFamiliar","shuffle","groupMode","groupSize","conceal","dailyNewTarget","showFirstPass","traditional"},
        {"intervalSeconds","seconds","detailHeight","width","alpha","meaningLines","wordSize","readingSize","meaningSize","snapEdges","positionLocked","panelX","panelY","ballX","ballY"},
        {"autoStart"},
        {"reviewAutoSpeak","autoSpeak","speakOnFlip","ballAudio","lockAudio","readChinese","readExample","wordRepeats","exampleRepeats","speechRate","speechGap","voiceEngine"}
    };
    private SettingIndex(){}
}

# 词间 iOS 0.3 验证工程

独立的原生可行性验证，安卓正式版仍为1.10.5。只含原词库三个样本，没有完整词库、正式复习或安卓记录迁移。iPhone优先，兼容iPad安装；最低系统17。用户提供的iPadOS为27.2，云端系统以实际报告为准。

## 当前候选

- 默认显示普通SwiftUI词卡和上一词／播放暂停／下一词／全部停止，可随系统窗口大小滚动和换行。
- iPad可用系统窗口化App／侧拉显示完整交互；iPhone没有同样的侧拉。App不能自行创建任意跨App窗口。
- 画中画放在折叠的可选实验入口，仍有系统媒体控件。没有把它当作已解决的安卓悬浮窗替代。
- 使用苹果系统语音；默认按语言调用系统选声，支持已安装声音选择、分语言语速和试听。
- 可恢复0.1的系统默认选声与日语0.425／中文0.5语速，保留当前词，保持暂停。不能保证不同系统下载状态下听感与旧版完全相同。
- 朗读完成后再切词，防止长释义被固定计时截断。暂停、退出试听、音频中断、耳机断开和45秒无响应会停止；恢复由用户主动开始。

## 轻量化与离线声音样本

“声音与试听”可选择系统语音或离线样本，默认系统语音。离线样本使用第一组日语jf_alpha和中文女声A（新版中文专用模型zf_001）；用户对日语表示可以、对中文A表示“还行吧”，仅作为本轮候选，不夸大为最终音质验收。

本轮不在手机里运行模型。三个词的日中朗读及两句试听共八个AAC文件，合计149,290字节，约149KB。它们在维护电脑离线生成，手机直接离线播放；样本语速独立保存，默认保持试听时的1.0倍。停止／切词会立即停止旧音频。完整词库发音包尚未加入，后续需另行测量并验证按等级下载。

此前共用Kokoro 1.0模型的中文口音不合适，该运行模型方案已撤回；设备构建虽得到8.7MB压缩包／33.5MB本体，模拟器因运行库链接失败未执行测试。当前候选没有该推理运行库和模型，也没有在线合成接口。系统声音选择、分语言语速、试听和恢复0.1设置仍保留。模型源码与失败证据留在Git历史及验证记录中。

声音及原词库许可见Resources/SAMPLE-VOICE-NOTICES.txt与SAMPLE-VOICE-LICENSE.txt，不改变词间代码许可。应用安装包大小以最终设备产物为准。

## 构建与验证

在Mac用Xcode打开CijianProof.xcodeproj，选择CijianProof和模拟器，或运行：

```sh
python3 make_project.py
python3 ci_probe.py
```

ci_probe.py先构建设备目标，再在iPad和iPhone模拟器运行原生测试。结果、截图、跳过项与未签名设备程序在out/，不使用个人证书、不上传App Store Connect。模拟器通过不等于iPad声音、后台可靠性、真实iPhone或苹果审核通过。[实际验证记录](VALIDATION.md)。

## 安装与真机验收

[Windows个人签名步骤](INSTALL-WINDOWS.md)。用户已用Sideloadly个人免费账号安装0.1并在真实iPad看到词卡、听见声音，对0.2听感不满意；每个后续候选都要单独复验。免费签名需要按其有效期重新部署，下载或LocalSend传输不能代替签名。无需向助手提供Apple账号密码、验证码或私人证书。

0.3请先检查普通词卡、iPad侧拉、快速翻词、暂停和恢复0.1选声。锁屏持续3分钟、蓝牙／耳机、来电、真实iPhone与审核仍需分别验证，不用状态标签代替实际观察。

词条保留原ID及内容，来源egg rolls／5mdld的anki-jlpt-decks，CC BY-NC4.0，见仓库[第三方说明](../THIRD_PARTY_NOTICES.md)。不读取安卓记录，不重录演示视频。

依据：[苹果iPad窗口与侧拉](https://support.apple.com/zh-cn/125309)、[系统画中画](https://developer.apple.com/documentation/avkit/adopting-picture-in-picture-in-a-custom-player)、[后台音频](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)、[系统声音下载](https://support.apple.com/zh-cn/111798)、[个人账号与签名](https://developer.apple.com/help/account/basics/about-your-developer-account/)。

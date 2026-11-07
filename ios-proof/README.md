# 词间 iOS 0.3 验证工程

独立的原生可行性验证，安卓正式版仍为1.10.5。只含原词库三个样本，没有完整词库、正式复习或安卓记录迁移。iPhone优先，兼容iPad安装；最低系统17。用户提供的iPadOS为27.2，云端系统以实际报告为准。

## 当前候选

- 默认显示普通SwiftUI词卡和上一词／播放暂停／下一词／全部停止，可随系统窗口大小滚动和换行。
- iPad可用系统窗口化App／侧拉显示完整交互；iPhone没有同样的侧拉。App不能自行创建任意跨App窗口。
- 画中画放在折叠的可选实验入口，仍有系统媒体控件。没有把它当作已解决的安卓悬浮窗替代。
- 使用苹果系统语音；默认按语言调用系统选声，支持已安装声音选择、分语言语速和试听。
- 可恢复0.1的系统默认选声与日语0.425／中文0.5语速，保留当前词，保持暂停。不能保证不同系统下载状态下听感与旧版完全相同。
- 朗读完成后再切词，防止长释义被固定计时截断。暂停、退出试听、音频中断、耳机断开和45秒无响应会停止；恢复由用户主动开始。

## 轻量化与声音方案状态

Kokoro 1.0 int8的93.4MB候选曾完成电脑合成，中文小样未通过用户听感验收，**已从当前应用候选移除**，ONNX Runtime及下载入口也一并移除。实验代码保留在Git历史，未作为正式iOS版发布。

当前应用不带独立声音模型或第三方推理运行库。用户随后确认第一组jf_alpha日语可接受，中文需重选；新的声音组合及手机合成／预生成离线发音包方式仍在评估，不能把“已听到新小样”写成已经移植并验证。应用压缩大小和解压大小以最终设备构建实测为准。

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

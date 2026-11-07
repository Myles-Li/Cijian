# 词间 iOS 可行性验证

这是独立的0.2验证工程，不是已发布的iOS词间。安卓正式版仍为1.10.5。iPhone优先，目标设备包括iPad；用户提供的iPadOS版本为27.2。工程暂定最低iOS／iPadOS17，云端测试系统以实际报告为准。

当前0.2：设备编译通过；iPad模拟器10项通过，iPhone9项通过、画中画1项明确跳过。新增分语言音色选择、质量显示、试听和独立语速，使用公开API简化画中画快进／快退。0.1已由用户在真实iPad27.2签名安装，确认能看见词卡并听到日中声音；0.2的声音自然度与小窗控件仍待真机复验。模拟器PiP截图仍为黑色，不计为画面显示通过。[完整证据及边界](VALIDATION.md)。

## 验证什么

1. 使用系统画中画显示真实词卡视频帧，查看拖动、缩放、退到其他App后的画面与系统媒体操作。没有跨App任意UIKit控件，也没有伪装成视频通话。
2. 使用系统日语／中文语音，明确选择后台听词；默认暂停。测试中断、耳机断开、全部停止和关闭画中画。未实现静音音频保活，也不保证任意后台定时器一直运行。
3. 编译iPhone／iPad目标，运行模拟器，再落实自己的iPad签名安装。模拟器成功不等于iPad实测，更不等于苹果审核通过。

词条只取原库三个记录，内容和ID未改写。来源egg rolls／5mdld的anki-jlpt-decks，独立遵守仓库的[第三方说明](../THIRD_PARTY_NOTICES.md)和CC BY-NC4.0。不含用户学习记录，不导入安卓数据库，不录制或发布演示视频。

## Mac构建与测试

用Xcode打开`CijianProof.xcodeproj`，选择`CijianProof`方案和模拟器即可。标准Python可重新生成工程：

```sh
python3 make_project.py
python3 ci_probe.py
```

`ci_probe.py`只在macOS运行，先编译未签名设备目标，再在已安装的iPad／iPhone模拟器运行原生测试。它不会上传到App Store Connect，不访问个人开发者证书。结果、截图及跳过项在`out/`，不能把跳过的画中画或缺少声音测试计为成功。

## 装到你的iPad

`UNSIGNED-device-build.zip`只是未签名编译产物，**不能直接安装**。GitHub下载或LocalSend传输都不能代替签名。

- 借到Mac：Xcode登录自己的免费Apple账号，设置签名团队，连接iPad并按系统要求允许开发者模式。免费开发签名通常7天到期，需要重新部署。
- 给其他人方便测试：自己的Apple Developer Program会员、App Store Connect与TestFlight；外部测试首个构建须审核，每个构建最多测试90天。正式App Store发布另行审核。
- 用户已选择Windows免费个人签名路线：[Sideloadly步骤](INSTALL-WINDOWS.md)。工具官网声明支持iOS27系列，用户已使用这条路线安装0.1；0.2可使用相同账号及设置重新签名覆盖，仍需单独验收。

**发布的设备产物没有Apple签名，需要由用户个人签名。0.1安装已取得用户反馈；0.2尚未在其iPad验证。** 不要求上传Apple账号密码、双重验证码或私人证书。

## 系统限制及判定

画中画用于媒体展示，收藏、熟悉、详情、评分等任意触摸按钮无法照搬到小窗中。词卡作为画中画内容的实际体验与审核适用性均需独立验证；若读不清、控制不足或平台拒绝，就不将其作为完整移植的前提。后台听词只在用户主动开始后进行；不实现开机自动显示悬浮窗。

验收至少包括：画中画退出App后可见、长词可读、暂停真实停止、关闭后无残留语音、日中声音切换、锁屏听词、蓝牙拔出、手机来电，以及iPad横竖屏。真实声音和真实设备只能由真机确认。

依据：[苹果画中画API](https://developer.apple.com/documentation/avkit/adopting-picture-in-picture-in-a-custom-player)、[后台音频](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)、[账号与免费签名](https://developer.apple.com/help/account/basics/about-your-developer-account/)、[TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)、[会员费用](https://developer.apple.com/programs/whats-included/)。

## 0.2声音验证与iPad替代窗口

在“声音与试听”中分别选择日语与中文声音，查看实际名称和基础／增强／高级质量；自动优选只考虑系统通过API提供的已安装声音。可以独立试听和调语速，偏好会保留；离开试听页不会擅自开始连续播放。正常基准语速为1.00倍。

更自然的声音通常需先在系统“无障碍／辅助功能 → 阅读与朗读（旧版朗读内容）→ 声音”中下载，回到验证App重新检测。无需开启旁白；并非每个系统声音都一定向第三方App提供。不能保证苹果声音与安卓引擎相同。参考[苹果声音下载说明](https://support.apple.com/zh-cn/111798)。

画中画仍保留系统播放／暂停、关闭与返回控件；点小窗可能显示系统覆盖层。0.2使用公开requiresLinearPlayback简化快进／快退，没有使用隐藏系统控件的私有接口，未宣称去掉LIVE标签。

iPad还可尝试“设置 → 多任务与手势 → 窗口化App”，在词间验证窗口顶部进入侧拉；这提供完整App交互，不是安卓悬浮球。iPhone没有相同的侧拉窗口模式。[苹果iPad窗口与侧拉说明](https://support.apple.com/zh-cn/125309)。

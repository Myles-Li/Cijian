# 词间 · Cijian

安卓日语学习：App 内专注学，也能用悬浮窗在日常使用手机时学。

A lightweight Android app for Japanese study, inside the app or in a floating window. Dictionary meanings and example translations are Chinese; the interface supports 中文 / English / 日本語.

アプリ内でもフローティングウィンドウでも日本語を学べるAndroidアプリです。語義と例文の訳は中国語で、表示言語は中国語・英語・日本語に対応しています。

**1.10.5 · Android 8.0+** · [下载 APK / Download / ダウンロード](https://github.com/Myles-Li/Cijian/releases/latest)

**[▶ 47 秒悬浮学习实录 / Watch the 47-second demo / 47秒の実機デモ](docs/videos/floating-study.mp4)** · [演示说明 / Demo notes / 動画について](docs/demo.md)

点击语言标题，在本页展开全文；点击另一种语言即可切换。
Select a language below to read it on this page. / 言語名をクリックすると、このページ内で全文を読めます。

<details name="readme-language">
<summary><strong>简体中文</strong></summary>

一个轻量的安卓日语学习应用：既能在 App 内专注学习，也能用悬浮窗利用碎片时间反复遇见单词。

**当前版本：1.10.5 / 构建17 · Android 8.0+ · Java 原生界面 · 持续更新中**

在 **GPT-6.1 Sol** 辅助下开发，累计开发投入 **25 小时+，开发跨度约两周（作者估算）**。需求、使用反馈和持续迭代由项目作者维护。[开发记录与时间口径](docs/开发记录.md)

项目首个可核验版本形成于 **2026-10-27**；**1.10.5 更新于 2026-11-07**，持续更新中。

Native Android app for learning Japanese through Chinese meanings. The interface supports 简体中文 / English / 日本語. Interface language changes do not translate the dictionary.

正式发布目前仅提供安卓版本；[iOS 可行性验证](https://github.com/Myles-Li/Cijian/tree/ios/feasibility/ios-proof)在独立实验分支进行，尚未发布完整 iOS 版。

## 下载

**[下载最新 APK](https://github.com/Myles-Li/Cijian/releases/latest)** · [全部版本更新说明](CHANGELOG.md)

**已使用1.10.1：直接用官方1.10.5覆盖安装，不先卸载，保留记录与未完成批次。** 仍使用1.10.0或更早版本的用户需先导出JSON，在Cijian中恢复核对；确认前不要卸载旧版。[升级与迁移步骤](docs/从旧版迁移.md)。历史Release保留。

## 主要功能

| 学习节奏 | App 内 | 悬浮窗 |
|---|---|---|
| 浏览一遍 | 连续浏览、每日初见目标与范围进度 | 自动轮换、手动翻词、小球及可选锁屏继续听 |
| 认真记忆 | 每日任务、到期优先、先回忆再揭晓评分 | 共用正式批次、揭晓状态、评分和中断续接 |

- 内置 egg rolls 的10,641词，保留原日语、中文释义、词性、音型、简繁释义与首条例句。
- 等级与词频筛选、整轮随机、收藏／熟悉、固定组和自选组。
- 主动回忆、自由练习、易忘词和独立四选一；各自写入记录的边界有明确说明。
- 日中单词／例句连读，次数、语速和停顿可调；使用用户安装的Android TTS引擎。
- 本地记录，JSON备份可分享或保存；恢复预览、冲突规则和最近一次导入撤回。
- 浅色／深色／跟随系统、预设主题色及自定义色号、三语界面、教程与五十音说明。

词库、学习和记录离线运行；应用没有INTERNET权限，无账号和广告。外置TTS引擎可能使用在线声音，其网络行为由对应引擎决定。当前没有英语释义学习模块、语法课程或用户词库导入。

## 界面与结构

[演示视频](docs/demo.md)与[最新版界面截图](#screenshots)集中展示在本页；[互动结构图](docs/词间-完整结构图.html)、[结构与统计口径](docs/结构审阅.md)和[复习说明](docs/复习怎样运作.md)可分别打开。结构图需下载后用浏览器离线打开；反馈草稿保存在本地，不会自动发送。

## 版本里程碑

| 版本 | 主要变化 |
|---|---|
| 1.10.5 | 整页横滑翻词、并排按钮留白、简短语音状态与设置往返、说明行距及新版展示截图 |
| 1.10.1 | 完整更换为Cijian安装标识、源码与新备份命名；旧JSON仍可导入，新旧应用独立并存 |
| 1.10.0 | App内独立学习字号、词卡左右滑动、顶部位置锁、紧凑例句操作、详情高度调节 |
| 1.9.5 | 详情拖动与滚动、设置分类与间距、默认色网格、双学习入口、切换与跨日刷新修复 |
| 1.9.0 | Cijian英文显示名、首启三语选择、默认黑白、自定义每批1～50及完整复习说明 |
| 1.8.0 | 三语界面、学习／复习分层、连续撤销、快速操作与通知清理修复 |
| 1.7.5 | 浏览／记忆均支持App与悬浮、共用批次与队列、四选一识义 |
| 1.7 | 按范围记位置、复习断点续接、安静开机恢复、位置锁定、恢复撤回 |
| 1.6 | 手动翻词独立朗读、搜索归一、三类字号、易忘词、主题选项面板和教程 |
| 1.5.5 | 日英学习实验后保留完整中文学习；修复长词与大字窄窗显示 |
| 1.5.0 | 统一统计快照、浏览进度、十词到期小批及备份预览校验 |
| 1.4.0 | 分享备份、两种学习风格、独立复习页、标记撤销和语音衔接 |
| 1.3.0 | 每日初见目标、备份v2、简单回忆安排和自定义主题色 |
| 1.2.0 | 分组、自选组、学习来源、设置分类、五十音图 |
| 1.1.0 | 拖动改善、词频、随机整轮、例句连读、后台听词与主题模式 |
| 1.0.0 | 首版悬浮卡片、词库搜索、基础标记、TTS及JSON记录备份 |

详细变化和早期中英实验说明见 [CHANGELOG.md](CHANGELOG.md)。

## 阅读与构建源码

[源码阅读指南](docs/源码阅读指南.md)从VS Code打开根文件夹讲起；建议先读ReviewPlan.java，再看MainActivity.java、FloatService.java与ReviewFlow.java。运行和调试推荐Android Studio。

构建环境：JDK17、Android SDK35、Build Tools35.0.0、Gradle8.11.1。

```text
Windows:       gradlew.bat :app:assembleDebug
macOS / Linux: sh ./gradlew :app:assembleDebug
Logic checks:  python tools/test_logic.py
```

源码采用普通debug开发签名，不含作者发布签名；自行构建的包与官方APK签名不同，建议使用专用模拟器开发。[验证与测试](docs/验证与测试.md) · [1.10.5最终验证记录](docs/VALIDATION.md)

原词库TSV、来源和转换工具随项目保留，可用 `python tools/prepare_deck.py` 重建仓库词库资产。该操作针对项目文件，不操作手机记录；转换会重建词条表。

## 名称、许可与数据来源

当前统一名称为 **词间 · Cijian**。1.10.1源码包、目录与安装标识均为 `com.myles.cijian`，数据库和新备份名称使用Cijian；旧JSON备份v1/v2仍可导入。1.10.5沿用此标识，可覆盖1.10.1；从1.10.0及更早安装标识迁移才需手动导入记录，详见[名称与迁移标识](docs/命名与兼容.md)。

**应用代码暂不授予开源许可，仅公开展示。** 查看和fork遵循GitHub服务条款；其他代码使用、修改或分发授权请联系作者。见 [代码许可状态](COPYRIGHT.md)。

词库作者为 **egg rolls / 5mdld**，来源 [anki-jlpt-decks](https://github.com/5mdld/anki-jlpt-decks)，独立遵守 **CC BY-NC 4.0**，保留署名、非商业限制及完整许可。Gradle Wrapper独立遵守Apache2.0。[第三方内容与转换说明](THIRD_PARTY_NOTICES.md) · [原词库许可](data/upstream/LICENSE)

源码仓库包含完整应用、构建文件、原生测试、逻辑测试、词库转换数据与公开文档。个人签名、本机缓存、私人需求、反馈照片、历史APK和个人学习记录留在本地。问题或建议可提交Issue；应用仍在持续更新。

</details>

<details name="readme-language">
<summary><strong>English</strong></summary>

A lightweight Android app for learning Japanese through Chinese meanings, either inside the app or in a floating window over other apps.

**Current release: 1.10.5 / build 17 · Android 8.0+ · Native Java UI · Actively maintained**

Developed with assistance from **GPT-6.1 Sol**, with **25+ hours of effort over roughly two weeks (author estimates)**. The project author maintains the requirements, usage feedback and ongoing iteration. The earliest verifiable release is dated **2026-10-27**; **1.10.5 is dated 2026-11-07**. [Development record, Chinese](docs/开发记录.md)

**Important language scope:** menus and help support Simplified Chinese, English and Japanese. Dictionary meanings and example translations remain Chinese. Selecting English or Japanese does not translate the dictionary.

The official release is Android only. An [iOS feasibility prototype](https://github.com/Myles-Li/Cijian/tree/ios/feasibility/ios-proof) is being tested on a separate branch; a full iOS app has not been released.

## Download

**[Download the latest APK](https://github.com/Myles-Li/Cijian/releases/latest)** · [Full version history, in Chinese](CHANGELOG.md)

**Already using 1.10.1? Install the official 1.10.5 APK over it without uninstalling to retain records and unfinished batches.** Users on 1.10.0 or earlier must export JSON and restore it in Cijian before removing the old app. [Upgrade and migration steps, Chinese](docs/从旧版迁移.md). Historical releases remain available.

## Features

| Study rhythm | Inside the app | Floating window |
|---|---|---|
| Browse through | Continuous browsing, a daily first-exposure goal and range progress | Automatic rotation, manual navigation, a small ball and optional screen-off listening |
| Active recall | Daily tasks, due items first, recall before revealing and rating | The same durable batch, revealed state, ratings and interrupted-task continuation |

- 10,641 vocabulary cards from egg rolls, retaining Japanese, Chinese meanings, parts of speech, original pitch marks, simplified/traditional meanings and the first example.
- JLPT level and source frequency filters, shuffled cycles, favourites, manual familiarity, fixed-size groups and custom selections.
- Active recall, free practice, easily forgotten words and separate four-choice recognition practice, with explicit boundaries for which records each mode updates.
- Japanese/Chinese word and example speech, configurable repetitions, rate and pauses, through an Android TTS engine installed by the user.
- Local records and JSON backups that can be shared or saved; restore preview, conflict handling and undo of the most recent import.
- Light, dark and system themes; preset and custom colours; three UI languages; tutorials and a kana chart.

Dictionary access, study and records work locally. The app has no INTERNET permission, account system or ads. An external TTS engine may use online voices under its own network behaviour. The current app does not provide English dictionary meanings, a grammar course or user-dictionary import.

## Screens and structure

The [demo](docs/demo.md) and [current screenshots](#screenshots) are shared on this page. See the [interactive structure map](docs/词间-完整结构图.html), [architecture and statistics](docs/结构审阅.md), and [review guide](docs/复习怎样运作.md). These detailed guides are currently Chinese. Download the HTML map to use it offline; feedback drafts stay local and are not sent automatically.

## Version milestones

| Version | Main changes |
|---|---|
| 1.10.5 | Full-screen browsing swipes, spaced actions, brief voice status and settings return, readable notes and updated screenshots |
| 1.10.1 | New Cijian application ID, source and backup names; legacy JSON imports and independent old/new installations |
| 1.10.0 | Independent in-app study text sizes, swipe navigation, header position lock, compact example action and adjustable detail height |
| 1.9.5 | Detail drag/scroll handling, settings organisation and spacing, default-colour grid, equal study entry points, smoother mode changes and refresh recovery across key changes |
| 1.9.0 | Cijian English display name, first-run language selection, default monochrome theme, custom batch size 1–50 and detailed review help |
| 1.8.0 | Three UI languages, layered study/review navigation, sequential undo, rapid-operation and notification fixes |
| 1.7.5 | Both study rhythms on both surfaces, shared batches and queues, four-choice recognition practice |
| 1.7 | Per-range positions, durable review continuation, quiet boot recovery, position lock and restore undo |
| 1.6 | Independent speech on manual navigation, search normalisation, three text sizes, forgotten-word practice, themed selectors and tutorials |
| 1.5.5 | Japanese/English experiment followed by a complete Chinese-learning edition; long-word and narrow-window large-text fixes |
| 1.5.0 | Consistent statistics snapshots, browsing progress, ten-item due review and validated restore preview |
| 1.4.0 | Shareable backups, two study rhythms, a review tab, mark undo and speech sequencing |
| 1.3.0 | Daily first-exposure goals, backup v2, simple recall scheduling and custom theme colours |
| 1.2.0 | Groups and custom selections, study sources, settings categories and kana help |
| 1.1.0 | Improved dragging, frequency filters, shuffled cycles, example speech, background listening and theme modes |
| 1.0.0 | Initial floating card, dictionary search, basic marks, TTS and JSON record backup |

The [full changelog](CHANGELOG.md) also distinguishes the two historical 1.5.5 editions. These notes describe earlier behaviour; the current app does not include the old English-learning data.

## Read and build the source

The [source-reading guide, in Chinese](docs/源码阅读指南.md) starts with opening the root folder in VS Code. Suggested entry points: ReviewPlan.java, then MainActivity.java, FloatService.java and ReviewFlow.java. Android Studio is recommended for running and debugging.

Build requirements: JDK 17, Android SDK 35, Build Tools 35.0.0 and Gradle 8.11.1. Initial setup downloads build tools and dependencies.

```text
Windows:       gradlew.bat :app:assembleDebug
macOS / Linux: sh ./gradlew :app:assembleDebug
Logic checks:  python tools/test_logic.py
```

The public project uses a normal local debug signature and contains no private release-signing configuration. A locally built APK has a different signature from the official APK; use a dedicated emulator for development. [Build and test scope, Chinese](docs/验证与测试.md) · [Final 1.10.5 validation record, Chinese](docs/VALIDATION.md)

The original pinned TSV, provenance and conversion script are included. `python tools/prepare_deck.py` rebuilds the repository vocabulary asset; it rebuilds its word table and does not operate on phone learning records.

## Name, code rights and data sources

The product name is **词间 · Cijian**. In 1.10.1 the source package, folders and Android application ID use `com.myles.cijian`. New database and backup names use Cijian. Legacy v1/v2 JSON backups can still be imported. Version 1.10.5 keeps this identity and updates 1.10.1 in place. Users on 1.10.0 or earlier need manual export and import. See [naming and migration, Chinese](docs/命名与兼容.md).

**Application code is publicly viewable, with no open-source licence or additional code-reuse permission granted at present.** Viewing and forking on GitHub follow GitHub's terms; contact the author for other code-use, modification or distribution permissions. See [code rights, Chinese](COPYRIGHT.md). This does not prevent personal noncommercial Japanese study with the official APK.

Dictionary author: **egg rolls / 5mdld**, from [anki-jlpt-decks](https://github.com/5mdld/anki-jlpt-decks), independently under **CC BY-NC 4.0**. Retain attribution, observe the noncommercial restriction and keep the original licence. Gradle Wrapper is independently under Apache License 2.0. [Third-party notices and conversion details](THIRD_PARTY_NOTICES.md) · [Original dictionary licence](data/upstream/LICENSE)

The repository contains the complete current app, build files, native tests, logic tests, conversion data and public documentation. Private signing material, machine caches, private feedback, older APKs and personal learning records remain local. Issues and suggestions are welcome; development is ongoing.

</details>

<details name="readme-language">
<summary><strong>日本語</strong></summary>

中国語の語義で日本語を学ぶ、軽量なAndroidアプリです。アプリ内で集中して学習するほか、他のアプリに重ねたフローティングウィンドウですき間時間に単語を繰り返し確認できます。

**現在の版：1.10.5 / ビルド17 · Android 8.0以降 · JavaによるネイティブUI · 継続更新中**

**GPT-6.1 Sol** の支援で開発し、累計 **25時間以上、開発期間は約2週間（作者の概算）** です。要件、利用時のフィードバック、継続的な改善は作者が管理しています。確認できる最初の版は **2026-10-27**、**1.10.5の更新日は2026-11-07** です。継続して更新しています。[開発記録と時間の扱い（中国語）](docs/开发记录.md)

**言語について：** メニューと説明の表示言語は簡体字中国語・英語・日本語から選べます。単語の語義と例文の訳は中国語のままです。表示言語を切り替えても、辞書の内容は翻訳されません。

正式リリースは現在Android版のみです。[iOSの実現可能性を調べる試作](https://github.com/Myles-Li/Cijian/tree/ios/feasibility/ios-proof)は別の実験ブランチで検証中で、完全なiOS版はまだ公開していません。

## ダウンロード

**[最新APKをダウンロード](https://github.com/Myles-Li/Cijian/releases/latest)** · [全バージョンの更新履歴（中国語）](CHANGELOG.md)

**1.10.1をご利用の場合：削除せず公式1.10.5を上書きすると、記録と未完了のバッチを引き継げます。** 1.10.0以前からは、旧版でJSONを保存し、Cijianで復元・確認するまで旧版を削除しないでください。[更新・移行手順（中国語）](docs/从旧版迁移.md)。過去のリリースも残しています。

## 主な機能

| 学習の進め方 | アプリ内 | フローティングウィンドウ |
|---|---|---|
| 一通り見る | 連続閲覧、その日に初めて触れる単語の目標、選択範囲の進捗 | 自動切り替え、手動移動、小さなボールへの折りたたみ、任意の画面消灯中の音声学習 |
| 思い出して覚える | 毎日の課題、復習期限の来た単語を優先、答えを開く前に思い出して自己評価 | 同じ保存済み課題、答えの表示状態、評価、中断位置を引き継ぐ |

- egg rollsの10,641件を内蔵。日本語、中国語の語義、品詞、元のアクセント番号、簡体字／繁体字の語義、最初の例文を保持します。
- JLPTレベルと元データの頻度区分による絞り込み、一巡するランダム順、お気に入り、手動の習熟マーク、固定語数のグループ、自選グループ。
- 能動的な想起、自由練習、忘れやすい単語、独立した四択練習。それぞれがどの記録を更新するかを説明しています。
- 日本語・中国語の単語と例文の読み上げ。回数、速度、間隔を設定でき、利用者がインストールしたAndroid TTSエンジンを使用します。
- ローカルの学習記録と、共有または保存できるJSONバックアップ。復元前の確認、競合の扱い、直前のインポートの取り消し。
- ライト／ダーク／システム連動、既定色と色コード指定、3言語のUI、使い方と五十音の説明。

辞書、学習、記録は端末内で動作します。アプリにINTERNET権限、アカウント機能、広告はありません。外部TTSエンジンのオンライン音声は、そのエンジンの通信方針に従います。現在、英語の語義を使う学習機能、文法講座、利用者の辞書インポートは提供していません。

## 画面と構造

[デモ動画](docs/demo.md)と[最新版の画面](#screenshots)はこのページにまとめています。[対話型構造図](docs/词间-完整结构图.html)、[構造と統計](docs/结构审阅.md)、[復習の説明](docs/复习怎样运作.md)は中国語です。HTMLの構造図はダウンロードしてオフラインで開けます。フィードバックの下書きはローカルに保存され、自動送信されません。

## バージョンごとの主な変更

| 版 | 主な内容 |
|---|---|
| 1.10.5 | 閲覧画面全体の横スワイプ、ボタンの余白、簡潔な音声状態と設定からの復帰、説明の行間、新版の画面写真 |
| 1.10.1 | Cijianの新しいアプリ識別子・ソース・バックアップ名、旧JSONの読み込みと旧新版の共存 |
| 1.10.0 | アプリ内学習文字サイズ、左右スワイプ、上部の位置ロック、簡潔な例文操作、詳細の高さ調整 |
| 1.9.5 | 詳細画面の移動とスクロール、設定分類と余白、既定色の並び、2つの学習入口、モード切り替えと日付などの条件変更時の更新処理を改善 |
| 1.9.0 | 英語表示名Cijian、初回の言語選択、既定の白黒テーマ、1～50語の自由なバッチ設定、詳しい復習説明 |
| 1.8.0 | 3言語のUI、学習／復習の入口整理、連続操作の取り消し、高速操作と通知の修正 |
| 1.7.5 | 2つの学習方法を両画面で利用、課題と順序の共有、四択練習 |
| 1.7 | 範囲ごとの位置、中断した復習の再開、無音での起動復元、位置固定、復元の取り消し |
| 1.6 | 手動移動時の独立した読み上げ、検索の正規化、3種類の文字サイズ、忘れやすい単語、テーマに沿う選択画面と使い方 |
| 1.5.5 | 日英学習の試験版を経て完全な中国語学習版へ。長い単語と狭いウィンドウの大きな文字を修正 |
| 1.5.0 | 統計を同じ時点で取得、閲覧進捗、期限到来語10件の小課題、復元前の検証 |
| 1.4.0 | バックアップ共有、2つの学習方法、復習タブ、マークの取り消し、読み上げの連携 |
| 1.3.0 | 初めて触れる語の1日目標、バックアップv2、簡単な想起予定、自由なテーマ色 |
| 1.2.0 | グループ、自選語、学習対象、設定分類、五十音 |
| 1.1.0 | 移動操作、頻度、一巡するランダム順、例文読み上げ、バックグラウンド音声、テーマモード |
| 1.0.0 | 最初の単語ウィンドウ、辞書検索、基本マーク、TTS、JSON記録バックアップ |

[全更新履歴](CHANGELOG.md)では、過去の1.5.5の2つの版も区別しています。過去の説明は当時の動作を示すもので、現在のアプリに旧英語学習データは含まれません。

## ソースを読む・ビルドする

[ソース読解ガイド（中国語）](docs/源码阅读指南.md)では、VS Codeでルートフォルダーを開くところから説明しています。まずReviewPlan.java、次にMainActivity.java、FloatService.java、ReviewFlow.javaを読む順序を勧めています。実行・デバッグにはAndroid Studioが便利です。

ビルド環境はJDK17、Android SDK35、Build Tools35.0.0、Gradle8.11.1です。初回はツールと依存関係のダウンロードに通信が必要です。

```text
Windows:       gradlew.bat :app:assembleDebug
macOS / Linux: sh ./gradlew :app:assembleDebug
Logic checks:  python tools/test_logic.py
```

公開ソースには配布用の秘密署名設定がなく、通常のローカルdebug署名を使います。自分でビルドしたAPKは公式版と署名が異なるため、開発には専用エミュレーターを使用してください。[ビルドと検証範囲（中国語）](docs/验证与测试.md) · [1.10.5の最終検証記録（中国語）](docs/VALIDATION.md)

固定版の元TSV、出典、変換スクリプトも含みます。`python tools/prepare_deck.py`はリポジトリ内の辞書資産を作り直します。単語テーブルを再構築する処理であり、電話内の学習記録を操作するものではありません。

## 名称・コードの利用条件・データ出典

製品名は **词间 · Cijian** です。1.10.1ではソースパッケージ、フォルダー、Androidのインストール識別子を `com.myles.cijian` に統一しました。データベースと新しいバックアップもCijianの名称を使います。旧v1/v2のJSONは読み込めます。1.10.5は同じ識別子を維持し、1.10.1に上書きできます。1.10.0以前からの記録は手動で移行してください。[名称と移行（中国語）](docs/命名与兼容.md)。

**アプリのコードは公開展示中で、現時点ではオープンソースライセンスや追加のコード再利用許諾を付与していません。** GitHub上の閲覧・forkは同サービスの利用規約に従います。それ以外のコード利用・改変・再配布の許諾は作者へお問い合わせください。[コードの利用条件（中国語）](COPYRIGHT.md)。公式APKを個人の非商業的な日本語学習に使うことを妨げるものではありません。

単語データの作者は **egg rolls / 5mdld**、出典は[anki-jlpt-decks](https://github.com/5mdld/anki-jlpt-decks)で、独立して **CC BY-NC 4.0** に従います。クレジット、非商業条件、元のライセンスを保持してください。Gradle Wrapperには独立してApache License2.0が適用されます。[第三者の内容と変換について](THIRD_PARTY_NOTICES.md) · [元辞書のライセンス](data/upstream/LICENSE)

このリポジトリには現在のアプリ全体、ビルド設定、ネイティブテスト、ロジックテスト、辞書変換データ、公開文書を含みます。個人の署名、端末固有キャッシュ、非公開の要望・写真、旧APK、個人の学習記録はローカルに残します。問題や提案はIssueへお寄せください。アプリは継続更新中です。

</details>

<a name="screenshots"></a>

## 界面截图 / Screenshots / 画面

以下截图全部来自最终 **1.10.5 / 构建17** 安装包，在专用 Android 模拟器中使用演示数据截取。悬浮学习覆盖的是安卓时钟应用。上面的手机实录与这些模拟器截图分开标注。

All screenshots show the final **1.10.5 / build 17** APK on a dedicated Android emulator with demo data. Floating study is shown over the Clock app. The video above is the author's separate phone recording.

すべて最終 **1.10.5 / ビルド17** のAPKを専用Androidエミュレーターで動かした画面です。デモ用データを使用し、フロートは時計アプリに重ねています。上の動画は作者による別の実機録画です。

<img src="docs/screenshots/home.png" alt="学习主页 / Study home / 学習ホーム" width="280"> <img src="docs/screenshots/browse.png" alt="App内浏览 / In-app browsing / アプリ内閲覧" width="280">

<img src="docs/screenshots/floating.png" alt="跨应用悬浮学习 / Floating study over another app / 他のアプリ上の単語ウィンドウ" width="280"> <img src="docs/screenshots/floating-details.png" alt="悬浮详情 / Expanded floating details / 詳細表示" width="280">

<img src="docs/screenshots/review.png" alt="主动回忆 / Active recall / 想起練習" width="280"> <img src="docs/screenshots/palette.png" alt="主题配色 / Theme palette / テーマ色" width="280">

复习图中的 **Cijian TEST ONLY voice** 是模拟器测试引擎，不随正式 APK 提供。 / The review screenshot's **TEST ONLY voice** is an emulator test engine, not included in the official APK. / 復習画面の **TEST ONLY voice** はエミュレーター用で、正式APKには含まれません。

[全部七张截图与来源 / All seven screenshots and provenance / 全7枚と出典](docs/screenshots/README.md)

'use strict';
(() => {
  const messages = {
    zh: { lang: 'zh-CN', pageTitle: '词间 · Cijian — 悬浮学习演示', eyebrow: 'ANDROID · 悬浮学习实录', title: '把日语放进日常。', intro: '47 秒，看看悬浮窗怎样翻词、听词、看例句，以及收起再展开。', phone: '作者手机实录', audio: '保留原音频', length: '47 秒', feature1: '单词、读音、词性和释义，在小窗口里一起看。', feature2: '翻词、朗读、收藏、展开例句，随手操作。', feature3: '拖到顺手的位置，锁定或收成小球。', download: '下载安卓 APK', github: '在 GitHub 阅读项目介绍 ↗', save: '保存视频 · 4.8 MiB', playbackError: '暂时无法播放，请用上面的保存入口下载视频。', scope: '视频录自安卓手机，背景主要是词间主页。跨其他应用显示的效果可在 GitHub 的最新版截图中查看；这不是 iOS 演示。词库释义与例句译文为中文，界面支持中英日三语。', recording: '原视频完整保留，没有剪辑、添加字幕或配乐。仅转换为兼容浏览器的 H.264 格式，原音频保持不变。视频不会自动播放。', footer: '词间 · Cijian — 持续更新中', notes: '录屏说明与来源', videoLabel: '词间安卓悬浮学习实录' },
    en: { lang: 'en', pageTitle: 'Cijian — Floating study demo', eyebrow: 'ANDROID · FLOATING STUDY', title: 'Make room for Japanese.', intro: 'A 47-second look at word navigation, speech, examples, and collapsing and reopening the floating window.', phone: 'Recorded on the author’s phone', audio: 'Original audio', length: '47 seconds', feature1: 'Words, readings, parts of speech and meanings in one small window.', feature2: 'Navigate, listen, save favourites and expand examples.', feature3: 'Move it to a convenient spot, lock its position or collapse it to a ball.', download: 'Download Android APK', github: 'Read about the project on GitHub ↗', save: 'Save video · 4.8 MiB', playbackError: 'Playback is unavailable. Use the save link above to download the video.', scope: 'Recorded on an Android phone, mainly over Cijian’s home screen. Current GitHub screenshots show the window over another app. This is not an iOS demo. Dictionary meanings and example translations are Chinese; the interface supports Chinese, English and Japanese.', recording: 'The full recording is preserved, without cuts, added subtitles or music. Only the video was converted to browser-compatible H.264; the original audio is unchanged. Playback starts only when you press play.', footer: '词间 · Cijian — Actively maintained', notes: 'Recording notes and provenance', videoLabel: 'Cijian floating-study demonstration on Android' },
    ja: { lang: 'ja', pageTitle: '词间 · Cijian — フロート学習デモ', eyebrow: 'ANDROID · フロート学習の実機録画', title: '日常に、日本語を。', intro: '単語の切り替え、読み上げ、例文、折りたたみ・再表示。47秒で操作を紹介します。', phone: '作者のスマートフォンで録画', audio: '元の音声を保持', length: '47秒', feature1: '単語、読み、品詞、語義を小さなウィンドウで確認。', feature2: '単語を切り替え、聞き、お気に入りや例文を操作。', feature3: '使いやすい位置へ移動し、位置をロックしたりボールに折りたたんだりできます。', download: 'Android APKをダウンロード', github: 'GitHubでプロジェクトを読む ↗', save: '動画を保存 · 4.8 MiB', playbackError: '再生できません。上の保存リンクから動画をダウンロードしてください。', scope: 'Androidスマートフォンで、主に词间のホーム画面に重ねて録画しました。他のアプリ上での表示はGitHubの最新版スクリーンショットをご覧ください。iOSのデモではありません。語義と例文の訳は中国語で、UIは中国語・英語・日本語に対応しています。', recording: '元の録画内容を保持し、カット、字幕、音楽の追加はしていません。ブラウザーで再生しやすいH.264に映像だけを変換し、元の音声はそのままです。動画は再生ボタンを押すまで始まりません。', footer: '词间 · Cijian — 継続更新中', notes: '録画について・出典', videoLabel: '词间のAndroidフロート学習デモ' }
  };
  const video = document.getElementById('demo-video');
  function selectLanguage(language) {
    const text = messages[language] || messages.zh;
    document.documentElement.lang = text.lang;
    document.title = text.pageTitle;
    document.querySelectorAll('[data-text]').forEach(element => {
      const value = text[element.dataset.text];
      if (value) element.textContent = value;
    });
    document.querySelectorAll('[data-language]').forEach(button => {
      button.setAttribute('aria-pressed', String(messages[button.dataset.language] === text));
    });
    video.setAttribute('aria-label', text.videoLabel);
  }
  document.querySelectorAll('[data-language]').forEach(button => {
    button.addEventListener('click', () => selectLanguage(button.dataset.language));
  });
  video.addEventListener('error', () => { document.getElementById('playback-error').hidden = false; });
  video.querySelector('source').addEventListener('error', () => { document.getElementById('playback-error').hidden = false; });
  const preferred = (navigator.language || 'zh').toLowerCase();
  selectLanguage(preferred.startsWith('ja') ? 'ja' : preferred.startsWith('zh') ? 'zh' : 'en');
})();

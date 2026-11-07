import SwiftUI
import AVKit
import AVFoundation
import CoreMedia

// 独立可行性验证，尚非完整移植。 / A feasibility probe, not the full iOS port.
struct ProbeWord: Codable, Identifiable {
    let id: String
    let word: String
    let reading: String
    let meaning: String
    let pos: String
    var displayReading: String {
        let latin = reading.range(of: "[A-Za-z]", options: .regularExpression) != nil
        let kana = reading.range(of: "[ぁ-ゖァ-ヺ]", options: .regularExpression) != nil
        return latin && !kana ? "词源：" + reading : reading
    }
}

enum ProbeDeck {
    static func load() throws -> [ProbeWord] {
        guard let url = Bundle.main.url(forResource: "words", withExtension: "json") else {
            throw NSError(domain: "CijianProof", code: 1, userInfo: [NSLocalizedDescriptionKey: "缺少验证词条"])
        }
        let words = try JSONDecoder().decode([ProbeWord].self, from: Data(contentsOf: url))
        guard !words.isEmpty else { throw NSError(domain: "CijianProof", code: 2) }
        return words
    }
    static func moved(_ index: Int, by delta: Int, count: Int) -> Int {
        guard count > 0 else { return 0 }
        return ((index + delta) % count + count) % count
    }
}

struct ProbeVoice: Identifiable, Equatable {
    let id: String
    let name: String
    let language: String
    let quality: Int
    var label: String { name + " · " + (quality >= 3 ? "高级" : quality == 2 ? "增强" : "基础") }
}

enum VoiceSelection {
    static func matching(_ language: String, in voices: [ProbeVoice]) -> [ProbeVoice] {
        let allowed = language == "zh-CN" ? ["zh-CN", "zh-Hans", "zh-Hans-CN"] : [language]
        return voices.filter { allowed.contains($0.language) }.sorted {
            if $0.quality != $1.quality { return $0.quality > $1.quality }
            if $0.name != $1.name { return $0.name < $1.name }
            return $0.id < $1.id
        }
    }
    static func rate(_ value: Double) -> Double { value.isFinite ? min(0.65, max(0.35, value)) : 0.5 }
}

@main
struct CijianProofApp: App {
    @StateObject private var probe = StudyProbe()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ProbeScreen(probe: probe)
                .onChange(of: phase) { probe.sceneChanged($0) }
        }
    }
}

struct ProbeScreen: View {
    @ObservedObject var probe: StudyProbe
    @StateObject private var download = OfflineVoiceDownload()
    @State private var showPiP = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    NavigationLink("声音与试听") { VoiceSettingsScreen(probe: probe, download: download) }
                        .accessibilityIdentifier("voiceSettings")
                    VStack(alignment: .leading, spacing: 14) {
                        Text("第 \(probe.index + 1) / \(probe.words.count) 词")
                            .font(.subheadline).foregroundStyle(.secondary).accessibilityIdentifier("position")
                        Text(probe.currentWord?.word ?? "载入失败").font(.largeTitle.bold())
                            .fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("word")
                        Text(probe.currentWord?.displayReading ?? "").font(.title3)
                            .fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("reading")
                        Text(probe.currentWord?.pos ?? "").font(.caption).foregroundStyle(.secondary)
                        Divider()
                        Text(probe.currentWord?.meaning ?? "").font(.title2)
                            .fixedSize(horizontal: false, vertical: true).accessibilityIdentifier("meaning")
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
                        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { playbackControls }
                        VStack(spacing: 8) { playbackControls }
                    }.buttonStyle(.bordered)
                    Text(probe.playing ? "正在播放" : "已暂停").accessibilityIdentifier("playbackState")
                    Toggle("退出 App 后继续听词", isOn: $probe.backgroundListening)
                        .accessibilityIdentifier("backgroundListening")
                    Button("全部停止") { probe.stopAll() }.buttonStyle(.bordered)
                        .accessibilityIdentifier("stop")
                    if UIDevice.current.userInterfaceIdiom == .pad {
                        Text("iPad 小窗：在系统“多任务与手势”中开启“窗口化 App”，再长按窗口顶部控制按钮选择“进入侧拉”。这里的词卡和按钮都能直接使用。")
                            .font(.footnote).foregroundStyle(.secondary).accessibilityIdentifier("windowGuide")
                    } else {
                        Text("iPhone 可在 App 内学习或后台听词；系统不提供 iPad 的侧拉窗口。")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    DisclosureGroup("可选：画中画实验", isExpanded: $showPiP) {
                        VStack(alignment: .leading, spacing: 12) {
                            FramePreview(probe: probe).aspectRatio(16 / 9, contentMode: .fit)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            Button("开启画中画验证", systemImage: "pip.enter") { probe.startPiP() }
                                .buttonStyle(.bordered).disabled(!probe.pipPossible).accessibilityIdentifier("pip")
                            Text("画中画仍带有系统媒体控件，不提供普通词卡按钮。关闭小窗会停止声音。")
                                .font(.footnote).foregroundStyle(.secondary)
                            Text(probe.pipStatus).accessibilityIdentifier("pipStatus")
                        }.padding(.top, 8)
                    }.accessibilityIdentifier("pipExperiment")
                    Text(probe.voiceStatus).font(.footnote).accessibilityIdentifier("voiceStatus")
                    Text(probe.lastEvent).font(.footnote).foregroundStyle(.secondary).accessibilityIdentifier("lastEvent")
                    Text("验证包 0.3 · 三个原词库样本。默认暂停、默认系统语音；独立离线模型需要主动下载。尚未迁移完整学习记录。")
                        .font(.footnote).foregroundStyle(.secondary)
                }.padding(20).frame(maxWidth: 640).frame(maxWidth: .infinity)
            }.navigationTitle("词间 · iOS 验证").navigationBarTitleDisplayMode(.inline)
        }.tint(.primary)
    }
    @ViewBuilder private var playbackControls: some View {
                        Button("上一个") { probe.move(-1) }.accessibilityIdentifier("previous")
                        Button(probe.playing ? "暂停" : "开始听词") {
                            probe.playing ? probe.pause() : probe.play(requireVoice: true)
                        }.accessibilityIdentifier("play")
                        Button("下一个") { probe.move(1) }.accessibilityIdentifier("next")
    }
}

struct VoiceSettingsScreen: View {
    @ObservedObject var probe: StudyProbe
    @ObservedObject var download: OfflineVoiceDownload
    var body: some View {
        Form {
            Section("语音引擎") {
                Picker("引擎", selection: Binding(get: { probe.engine }, set: { probe.selectEngine($0) })) {
                    Text("苹果系统语音 · 无需额外模型").tag("system")
                    Text("Kokoro · 独立离线模型").tag("kokoro")
                }.accessibilityIdentifier("speechEngine")
                Text("独立模型下载后在本机合成，朗读不联网；当前验证三个词条和两句试听。自然度需实际试听。")
                    .font(.footnote)
                Text(download.status).accessibilityIdentifier("offlineModelStatus")
                if download.downloading {
                    ProgressView()
                    Button("取消下载") { download.cancel() }.accessibilityIdentifier("cancelModelDownload")
                } else if download.installed {
                    Button("删除离线声音模型", role: .destructive) { probe.removeOffline(download) }
                        .accessibilityIdentifier("removeModel")
                } else {
                    Button("下载可选声音模型 · 93.4 MB") { download.download() }
                        .accessibilityIdentifier("downloadModel")
                }
                Text("默认不下载。声音模型可单独删除；系统语音仍可使用。首次下载需要联网。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            voiceSection("日语", language: "ja-JP", selected: probe.japaneseVoiceID, rate: probe.japaneseRate)
            voiceSection("中文", language: "zh-CN", selected: probe.chineseVoiceID, rate: probe.chineseRate)
            Section("对比旧版") {
                Button("恢复 0.1 系统语音设置") { probe.restoreLegacyVoice() }.accessibilityIdentifier("restoreLegacyVoice")
                Text("恢复系统默认选声和 0.1 的日语语速，不改变当前词。系统更新或声音下载变化仍可能影响听感。")
                    .font(.footnote)
            }
            Section("下载更自然的声音") {
                Text("先在系统的“无障碍／辅助功能 → 阅读与朗读（旧版叫朗读内容）→ 声音”中，下载日语和中文的增强或高级声音。无需开启旁白。")
                Text("返回验证包后点“重新检测声音”。这里只能选择系统向 App 提供的声音；下载与个人听感需在设备上确认。")
                Button("重新检测声音") { probe.refreshVoices() }.accessibilityIdentifier("refreshVoices")
                Text(probe.voiceStatus).font(.footnote).accessibilityIdentifier("selectedVoiceStatus")
            }
        }.navigationTitle("声音与试听")
            .onAppear { probe.pause(); probe.refreshVoices() }
            .onDisappear { probe.stopPreview() }
    }
    private func voiceSection(_ title: String, language: String, selected: String, rate: Double) -> some View {
        Section(title) {
            if probe.engine == "system" {
            Picker("声音", selection: Binding(get: { language == "ja-JP" ? probe.japaneseVoiceID : probe.chineseVoiceID },
                set: { probe.selectVoice($0, language: language) })) {
                Text("系统默认声音").tag("")
                ForEach(VoiceSelection.matching(language, in: probe.voices)) { Text($0.label).tag($0.id) }
                if !selected.isEmpty && !probe.voices.contains(where: { $0.id == selected }) {
                    Text("所选声音暂不可用 · 使用自动回退").tag(selected)
                }
            }.accessibilityIdentifier(language == "ja-JP" ? "japaneseVoice" : "chineseVoice")
            } else {
                Text(language == "ja-JP" ? "Kokoro · jf_alpha（日语）" : "Kokoro · zf_xiaobei（普通话）")
            }
            Text(String(format: "语速 · %.2f 倍", rate / 0.5))
            Slider(value: Binding(get: { language == "ja-JP" ? probe.japaneseRate : probe.chineseRate },
                set: { probe.setRate($0, language: language) }), in: 0.35...0.65, step: 0.025)
                .accessibilityIdentifier(language == "ja-JP" ? "japaneseRate" : "chineseRate")
            Button("试听" + title) { probe.preview(language: language) }
                .accessibilityIdentifier(language == "ja-JP" ? "previewJapanese" : "previewChinese")
        }
    }
}

final class PreviewHost: UIView {
    let videoLayer = AVSampleBufferDisplayLayer()
    override init(frame: CGRect) {
        super.init(frame: frame)
        videoLayer.videoGravity = .resizeAspect
        layer.addSublayer(videoLayer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
    override func layoutSubviews() { super.layoutSubviews(); videoLayer.frame = bounds }
}

struct FramePreview: UIViewRepresentable {
    let probe: StudyProbe
    func makeUIView(context: Context) -> PreviewHost {
        let view = PreviewHost()
        probe.attach(view.videoLayer)
        return view
    }
    func updateUIView(_ view: PreviewHost, context: Context) {}
}

// 将真实词条绘制为帧，不捕屏或用静音保活。
// Render actual word frames without screen capture or fake audio.
enum WordFrame {
    static let size = CGSize(width: 1280, height: 720)
    static func make(_ word: ProbeWord) throws -> CMSampleBuffer {
        var pixel: CVPixelBuffer?
        let attributes: [CFString: Any] = [kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true, kCVPixelBufferIOSurfacePropertiesKey: [:]]
        guard CVPixelBufferCreate(kCFAllocatorDefault, Int(size.width), Int(size.height),
                                  kCVPixelFormatType_32BGRA, attributes as CFDictionary, &pixel) == kCVReturnSuccess,
              let pixel else { throw NSError(domain: "CijianFrame", code: 1) }
        CVPixelBufferLockBaseAddress(pixel, [])
        defer { CVPixelBufferUnlockBaseAddress(pixel, []) }
        guard let context = CGContext(data: CVPixelBufferGetBaseAddress(pixel), width: Int(size.width),
            height: Int(size.height), bitsPerComponent: 8, bytesPerRow: CVPixelBufferGetBytesPerRow(pixel),
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        else { throw NSError(domain: "CijianFrame", code: 2) }
        context.setFillColor(UIColor(white: 0.09, alpha: 1).cgColor)
        context.fill(CGRect(origin: .zero, size: size))
        context.translateBy(x: 0, y: size.height); context.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(context); defer { UIGraphicsPopContext() }
        draw(word.word, in: CGRect(x: 64, y: 58, width: 1152, height: 210), font: 94, bold: true)
        draw(word.displayReading + " · " + word.pos, in: CGRect(x: 64, y: 286, width: 1152, height: 100), font: 48)
        draw(word.meaning, in: CGRect(x: 64, y: 414, width: 1152, height: 224), font: 58)
        draw("词间 · 画中画验证", in: CGRect(x: 64, y: 666, width: 1152, height: 42), font: 24)
        var description: CMVideoFormatDescription?
        guard CMVideoFormatDescriptionCreateForImageBuffer(allocator: kCFAllocatorDefault,
            imageBuffer: pixel, formatDescriptionOut: &description) == noErr, let description else {
            throw NSError(domain: "CijianFrame", code: 3)
        }
        var timing = CMSampleTimingInfo(duration: CMTime(value: 1, timescale: 2),
            presentationTimeStamp: CMClockGetTime(CMClockGetHostTimeClock()), decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        guard CMSampleBufferCreateReadyWithImageBuffer(allocator: kCFAllocatorDefault, imageBuffer: pixel,
            formatDescription: description, sampleTiming: &timing, sampleBufferOut: &sample) == noErr,
              let sample else { throw NSError(domain: "CijianFrame", code: 4) }
        if let array = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: true) {
            let dictionary = unsafeBitCast(CFArrayGetValueAtIndex(array, 0), to: CFMutableDictionary.self)
            CFDictionarySetValue(dictionary, Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                                 Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
        }
        return sample
    }
    private static func draw(_ text: String, in rectangle: CGRect, font: CGFloat, bold: Bool = false) {
        let paragraph = NSMutableParagraphStyle(); paragraph.lineBreakMode = .byWordWrapping; paragraph.lineSpacing = 8
        var pointSize = font
        func attributes(_ size: CGFloat) -> [NSAttributedString.Key: Any] {
            [.font: UIFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular),
             .foregroundColor: UIColor.white, .paragraphStyle: paragraph]
        }
        while pointSize > 22 {
            let measured = (text as NSString).boundingRect(with: CGSize(width: rectangle.width, height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attributes(pointSize), context: nil)
            if measured.height <= rectangle.height { break }
            pointSize -= 2
        }
        (text as NSString).draw(with: rectangle, options: [.usesLineFragmentOrigin, .usesFontLeading],
                               attributes: attributes(pointSize), context: nil)
    }
}

final class StudyProbe: NSObject, ObservableObject, AVPictureInPictureControllerDelegate,
    AVPictureInPictureSampleBufferPlaybackDelegate, AVSpeechSynthesizerDelegate, AVAudioPlayerDelegate {
    @Published private(set) var words: [ProbeWord] = []
    @Published private(set) var index = 0
    @Published private(set) var playing = false
    @Published private(set) var pipPossible = false
    @Published private(set) var pipStatus = "画中画尚未就绪"
    @Published private(set) var voiceStatus = "正在检查系统声音"
    @Published private(set) var lastEvent = "尚未播放"
    @Published var backgroundListening = false
    @Published private(set) var voices: [ProbeVoice] = []
    @Published private(set) var japaneseVoiceID: String
    @Published private(set) var chineseVoiceID: String
    @Published private(set) var japaneseRate: Double
    @Published private(set) var chineseRate: Double
    @Published private(set) var engine: String
    var currentWord: ProbeWord? { words.indices.contains(index) ? words[index] : nil }
    private var display: AVSampleBufferDisplayLayer?
    private var pip: AVPictureInPictureController?
    private var observation: NSKeyValueObservation?
    private var frameTimer: Timer?
    private var wordTimer: Timer?
    private var speechWatchdog: Timer?
    private let speech = AVSpeechSynthesizer()
    private var observers: [NSObjectProtocol] = []
    private var speechGeneration = UUID()
    private var activeUtterance: AVSpeechUtterance?
    private var chinesePending: String?
    private var completedSpeechCount = 0
    private let preferences: UserDefaults
    private var previewing = false
    private let offline: OfflineSynthesizer
    private let offlineDirectory: URL
    private var offlineTask: Task<Void, Never>?
    private var player: AVAudioPlayer?
    private var wordDeadline = Date()
    var hasActiveAudio: Bool { speech.isSpeaking || player?.isPlaying == true }
    init(preferences: UserDefaults = .standard, offlineDirectory: URL = OfflineVoiceFiles.directory) {
        self.preferences = preferences
        self.offlineDirectory = offlineDirectory
        offline = OfflineSynthesizer(directory: offlineDirectory)
        japaneseVoiceID = preferences.string(forKey: "probe.voice.ja") ?? ""
        chineseVoiceID = preferences.string(forKey: "probe.voice.zh") ?? ""
        japaneseRate = VoiceSelection.rate(preferences.object(forKey: "probe.rate.ja") as? Double ?? 0.425)
        chineseRate = VoiceSelection.rate(preferences.object(forKey: "probe.rate.zh") as? Double ?? 0.5)
        engine = preferences.string(forKey: "probe.engine") == "kokoro" ? "kokoro" : "system"
        super.init()
        do { words = try ProbeDeck.load() } catch { lastEvent = error.localizedDescription }
        speech.delegate = self
        // 仅配置类别，不激活音频。 / Configure without activating playback or making sound.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        refreshVoices()
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification,
            object: nil, queue: .main) { [weak self] note in
                guard let kind = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                      kind == AVAudioSession.InterruptionType.began.rawValue else { return }
                self?.pause(); self?.record("音频中断，已暂停，需手动继续")
            })
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification,
            object: nil, queue: .main) { [weak self] note in
                guard let reason = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                      reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
                self?.pause(); self?.record("原音频设备断开，已暂停")
            })
        observers.append(NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil, queue: .main) { [weak self] _ in
                guard let self else { return }
                self.pause()
                Task { await self.offline.unload() }
                self.record("系统内存不足，已暂停并释放离线模型")
            })
        record("初始暂停")
    }
    func attach(_ layer: AVSampleBufferDisplayLayer) {
        guard display !== layer, pip?.isPictureInPictureActive != true else { return }
        observation?.invalidate(); frameTimer?.invalidate()
        display = layer
        var timebase: CMTimebase?
        if CMTimebaseCreateWithSourceClock(allocator: kCFAllocatorDefault,
            sourceClock: CMClockGetHostTimeClock(), timebaseOut: &timebase) == noErr, let timebase {
            CMTimebaseSetTime(timebase, time: CMClockGetTime(CMClockGetHostTimeClock()))
            CMTimebaseSetRate(timebase, rate: 1); layer.controlTimebase = timebase
        }
        if AVPictureInPictureController.isPictureInPictureSupported() {
            let source = AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer: layer, playbackDelegate: self)
            let controller = AVPictureInPictureController(contentSource: source)
            controller.delegate = self; controller.canStartPictureInPictureAutomaticallyFromInline = false
            controller.requiresLinearPlayback = true; pip = controller
            observation = controller.observe(\.isPictureInPicturePossible, options: [.initial, .new]) { [weak self] item, _ in
                DispatchQueue.main.async {
                    self?.pipPossible = item.isPictureInPicturePossible
                    if !item.isPictureInPictureActive { self?.pipStatus = item.isPictureInPicturePossible ? "可点击验证画中画" : "当前环境尚不允许画中画" }
                }
            }
        } else { pipStatus = "此运行环境不支持画中画" }
        frameTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.render() }
        render()
    }
    private func render() {
        guard let word = currentWord, let display else { return }
        if display.status == .failed { display.flush() }
        guard display.isReadyForMoreMediaData else { return }
        do { display.enqueue(try WordFrame.make(word)) } catch { record("画面生成失败：" + error.localizedDescription) }
    }
    func move(_ direction: Int) {
        guard !words.isEmpty else { return }
        cancelSpeech(); index = ProbeDeck.moved(index, by: direction, count: words.count)
        render(); if playing { wordDeadline = Date().addingTimeInterval(12); sayCurrent() }; record("切换词条")
    }
    func play(requireVoice: Bool) {
        guard !words.isEmpty else { return }
        if engine == "kokoro" && !OfflineVoiceFiles.present(in: offlineDirectory) {
            voiceStatus = "离线模型尚未下载，请到声音与试听下载或选择系统语音"; record("未下载离线模型"); return
        }
        if engine == "system" && requireVoice && currentVoice("ja-JP") == nil {
            voiceStatus = "没有日语声音，未开始听词"; record("缺少日语声音"); return
        }
        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .default)
            try audio.setActive(true)
        } catch { record("音频会话失败：" + error.localizedDescription); return }
        cancelSpeech(); playing = true; wordDeadline = Date().addingTimeInterval(12); sayCurrent()
        pip?.invalidatePlaybackState(); record("用户开始")
    }
    private func scheduleWord() {
        wordTimer?.invalidate()
        // 等朗读完成再换词，长释义与首次模型加载不会被固定计时截断。
        // Advance only after speech completes, including slow first inference.
        wordTimer = Timer.scheduledTimer(withTimeInterval: max(1, wordDeadline.timeIntervalSinceNow), repeats: false) { [weak self] _ in
            guard let self, self.playing else { return }; self.move(1)
        }
    }
    private func cancelSpeech() {
        speechGeneration = UUID(); activeUtterance = nil; chinesePending = nil; previewing = false
        offlineTask?.cancel(); offlineTask = nil; player?.stop(); player = nil
        wordTimer?.invalidate(); wordTimer = nil; speech.stopSpeaking(at: .immediate)
        speechWatchdog?.invalidate(); speechWatchdog = nil
    }
    private func watchSpeech() {
        speechWatchdog?.invalidate()
        speechWatchdog = Timer.scheduledTimer(withTimeInterval: 45, repeats: false) { [weak self] _ in
            guard let self, self.playing || self.previewing else { return }
            self.pause(); self.record("语音响应超时，已暂停，请重试")
        }
    }
    func refreshVoices() {
        voices = AVSpeechSynthesisVoice.speechVoices().map { ProbeVoice(id: $0.identifier, name: $0.name, language: $0.language, quality: $0.quality.rawValue) }
        func description(_ language: String, selected: String) -> String {
            guard let system = currentVoice(language) else { return "没有可用声音，请在系统下载" }
            let voice = ProbeVoice(id: system.identifier, name: system.name, language: system.language, quality: system.quality.rawValue)
            let fallback = !selected.isEmpty && voice.id != selected ? "（所选声音不可用，已回退系统默认）" : ""
            return voice.label + fallback
        }
        voiceStatus = engine == "kokoro"
            ? (OfflineVoiceFiles.present(in: offlineDirectory) ? "Kokoro 离线 · 日语 jf_alpha／中文 zf_xiaobei" : "Kokoro 离线模型尚未下载")
            : "系统日语：" + description("ja-JP", selected: japaneseVoiceID) + "\n系统中文：" + description("zh-CN", selected: chineseVoiceID)
        record("已重新检测系统声音")
    }
    private func currentVoice(_ language: String) -> AVSpeechSynthesisVoice? {
        let selected = language == "ja-JP" ? japaneseVoiceID : chineseVoiceID
        if let choice = VoiceSelection.matching(language, in: voices).first(where: { $0.id == selected }),
           let voice = AVSpeechSynthesisVoice(identifier: choice.id) { return voice }
        return AVSpeechSynthesisVoice(language: language)
    }
    func selectEngine(_ value: String) {
        pause(); engine = value == "kokoro" ? "kokoro" : "system"
        preferences.set(engine, forKey: "probe.engine")
        if engine == "system" { Task { await offline.unload() } }
        refreshVoices()
    }
    func restoreLegacyVoice() {
        selectEngine("system")
        selectVoice("", language: "ja-JP"); selectVoice("", language: "zh-CN")
        setRate(0.425, language: "ja-JP"); setRate(0.5, language: "zh-CN")
        record("已恢复 0.1 的选声方式与语速，当前词不变")
    }
    func removeOffline(_ download: OfflineVoiceDownload) {
        selectEngine("system")
        Task { @MainActor in
            await offline.unload()
            do { try download.remove(); refreshVoices() }
            catch { record("删除声音模型失败：" + error.localizedDescription) }
        }
    }
    func selectVoice(_ identifier: String, language: String) {
        pause()
        if language == "ja-JP" { japaneseVoiceID = identifier; preferences.set(identifier, forKey: "probe.voice.ja") }
        else { chineseVoiceID = identifier; preferences.set(identifier, forKey: "probe.voice.zh") }
        refreshVoices()
    }
    func setRate(_ value: Double, language: String) {
        pause(); let rate = VoiceSelection.rate(value)
        if language == "ja-JP" { japaneseRate = rate; preferences.set(rate, forKey: "probe.rate.ja") }
        else { chineseRate = rate; preferences.set(rate, forKey: "probe.rate.zh") }
    }
    func preview(language: String) {
        pause(); refreshVoices()
        if engine == "kokoro" && !OfflineVoiceFiles.present(in: offlineDirectory) { record("请先下载离线模型，或选择系统语音"); return }
        do { try AVAudioSession.sharedInstance().setActive(true) }
        catch { record("试听音频会话失败"); return }
        previewing = true
        let phrase = language == "ja-JP" ? "日本語を毎日少しずつ勉強します。" : "每天学一点，按自己的节奏学习。"
        if engine == "kokoro" { speakOffline(phrase, language: language); return }
        guard let voice = currentVoice(language) else { pause(); record("试听所需声音尚未下载"); return }
        let utterance = AVSpeechUtterance(string: phrase); utterance.voice = voice
        utterance.rate = Float(language == "ja-JP" ? japaneseRate : chineseRate)
        activeUtterance = utterance; watchSpeech(); speech.speak(utterance); record("正在试听")
    }
    func stopPreview() { if previewing { pause() } }
    private func sayCurrent() {
        guard let word = currentWord else { pause(); return }
        chinesePending = word.meaning
        if engine == "kokoro" { speakOffline(word.word, language: "ja-JP"); return }
        guard let voice = currentVoice("ja-JP") else { pause(); record("日语声音不可用，已暂停"); return }
        let utterance = AVSpeechUtterance(string: word.word); utterance.voice = voice
        utterance.rate = Float(japaneseRate)
        activeUtterance = utterance; watchSpeech(); speech.speak(utterance)
    }
    private func speakOffline(_ text: String, language: String) {
        let token = speechGeneration
        let speed = Float((language == "ja-JP" ? japaneseRate : chineseRate) / 0.5)
        watchSpeech()
        record("正在本机合成" + (language == "ja-JP" ? "日语" : "中文"))
        offlineTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let data = try await offline.generate(text: text, language: language, speed: speed)
                guard !Task.isCancelled, speechGeneration == token, playing || previewing else { return }
                let audio = try AVAudioPlayer(data: data); audio.delegate = self
                player = audio
                guard audio.play() else { throw VoiceFailure.message("离线音频无法播放") }
                record("正在播放离线合成语音")
            } catch {
                guard !Task.isCancelled, speechGeneration == token else { return }
                pause(); record("离线语音失败：" + error.localizedDescription)
            }
        }
    }
    func audioPlayerDidFinishPlaying(_ audio: AVAudioPlayer, successfully flag: Bool) {
        guard audio === player else { return }
        player = nil
        guard flag else { pause(); record("离线声音播放失败，已暂停"); return }
        speechFinished()
    }
    func audioPlayerDecodeErrorDidOccur(_ audio: AVAudioPlayer, error: Error?) {
        guard audio === player else { return }
        pause(); record("离线声音解码失败，已暂停")
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard utterance === activeUtterance else { return }
        activeUtterance = nil
        speechFinished()
    }
    private func speechFinished() {
        speechWatchdog?.invalidate(); speechWatchdog = nil
        if previewing { pause(); record("试听结束"); return }
        guard playing else { return }
        completedSpeechCount += 1
        if let meaning = chinesePending {
            chinesePending = nil
            if engine == "kokoro" { speakOffline(meaning, language: "zh-CN"); return }
            guard let voice = currentVoice("zh-CN") else { scheduleWord(); record("未找到中文声音，已跳过译文"); return }
            let translated = AVSpeechUtterance(string: meaning); translated.voice = voice; translated.rate = Float(chineseRate)
            activeUtterance = translated; watchSpeech(); speech.speak(translated)
        } else { scheduleWord() }
        record("系统报告朗读完成，仍需人工试听")
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        guard utterance === activeUtterance else { return }
        pause(); record("系统取消朗读，已暂停")
    }
    func pause() {
        playing = false; wordTimer?.invalidate(); wordTimer = nil; cancelSpeech()
        pip?.invalidatePlaybackState()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        record("已暂停")
    }
    func startPiP() {
        guard let pip, pip.isPictureInPicturePossible else { record("画中画不可用"); return }
        play(requireVoice: false)
        guard playing else { return }
        pip.startPictureInPicture() // 仅用户按钮触发。 / Only the user's button starts PiP.
    }
    func stopAll() { pause(); pip?.stopPictureInPicture(); record("全部停止") }
    func sceneChanged(_ phase: ScenePhase) {
        if phase != .active && previewing { pause() }
        if phase == .active { refreshVoices() }
        if phase == .background && !backgroundListening && pip?.isPictureInPictureActive != true { pause() }
        record(phase == .background ? "已进入后台" : "应用状态变化")
    }
    func pictureInPictureController(_ controller: AVPictureInPictureController, setPlaying: Bool) {
        setPlaying ? play(requireVoice: false) : pause()
    }
    func pictureInPictureControllerTimeRangeForPlayback(_ controller: AVPictureInPictureController) -> CMTimeRange {
        CMTimeRange(start: .zero, duration: .positiveInfinity)
    }
    func pictureInPictureControllerIsPlaybackPaused(_ controller: AVPictureInPictureController) -> Bool { !playing }
    func pictureInPictureController(_ controller: AVPictureInPictureController, didTransitionToRenderSize newRenderSize: CMVideoDimensions) {
        record("画中画尺寸 \(newRenderSize.width)×\(newRenderSize.height)")
    }
    func pictureInPictureController(_ controller: AVPictureInPictureController, skipByInterval skipInterval: CMTime,
                                   completion completionHandler: @escaping () -> Void) {
        move(skipInterval.seconds >= 0 ? 1 : -1); completionHandler()
    }
    func pictureInPictureControllerDidStartPictureInPicture(_ controller: AVPictureInPictureController) {
        pipStatus = "画中画已开启"; record("画中画已开启")
    }
    func pictureInPictureControllerDidStopPictureInPicture(_ controller: AVPictureInPictureController) {
        pause(); pipStatus = "画中画已关闭"; record("画中画已关闭，音频已停")
    }
    func pictureInPictureController(_ controller: AVPictureInPictureController, failedToStartPictureInPictureWithError error: Error) {
        pause(); pipStatus = "画中画启动失败：" + error.localizedDescription; record(pipStatus)
    }
    func pictureInPictureController(_ controller: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        completionHandler(true)
    }
    private func record(_ event: String) {
        lastEvent = event
        let state: [String: Any] = ["event": event, "playing": playing, "index": index,
            "wordCount": words.count, "pipPossible": pipPossible, "pipActive": pip?.isPictureInPictureActive ?? false,
            "voice": voiceStatus, "completedSpeechCount": completedSpeechCount,
            "systemVersion": UIDevice.current.systemVersion, "device": UIDevice.current.model,
            "japaneseRate": japaneseRate, "chineseRate": chineseRate, "previewing": previewing,
            "engine": engine, "offlineModelInstalled": OfflineVoiceFiles.present(in: offlineDirectory),
            "time": Date().timeIntervalSince1970]
        if let data = try? JSONSerialization.data(withJSONObject: state, options: [.prettyPrinted, .sortedKeys]),
           let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            try? data.write(to: directory.appendingPathComponent("proof-status.json"), options: .atomic)
        }
    }
    deinit {
        frameTimer?.invalidate(); wordTimer?.invalidate(); speechWatchdog?.invalidate()
        offlineTask?.cancel(); player?.stop()
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
    }
}

import SwiftUI
import AVKit
import AVFoundation
import CoreMedia

// This is a feasibility probe, not the iOS port of the learning app.
struct ProbeWord: Codable, Identifiable {
    let id: String
    let word: String
    let reading: String
    let meaning: String
    let pos: String
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
    static func choose(_ preferred: String, language: String, voices: [ProbeVoice]) -> ProbeVoice? {
        let available = matching(language, in: voices)
        return available.first(where: { !$0.id.isEmpty && $0.id == preferred }) ?? available.first
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
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("这只是悬浮替代与安装验证，没有迁移完整词库或学习记录。")
                        .font(.subheadline).foregroundStyle(.secondary)
                    NavigationLink("声音与试听") { VoiceSettingsScreen(probe: probe) }
                        .accessibilityIdentifier("voiceSettings")
                    FramePreview(probe: probe).aspectRatio(16 / 9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .accessibilityLabel("词条视频画面")
                    Text(probe.currentWord?.word ?? "载入失败").font(.title.bold())
                        .accessibilityIdentifier("word")
                    Text("第 \(probe.index + 1) / \(probe.words.count) 词")
                        .accessibilityIdentifier("position")
                    HStack(spacing: 12) {
                        Button("上一个") { probe.move(-1) }.accessibilityIdentifier("previous")
                        Button(probe.playing ? "暂停" : "开始听词") {
                            probe.playing ? probe.pause() : probe.play(requireVoice: true)
                        }.accessibilityIdentifier("play")
                        Button("下一个") { probe.move(1) }.accessibilityIdentifier("next")
                    }.buttonStyle(.bordered)
                    Button("开启画中画验证", systemImage: "pip.enter") { probe.startPiP() }
                        .buttonStyle(.borderedProminent).disabled(!probe.pipPossible)
                        .accessibilityIdentifier("pip")
                    Toggle("退出 App 后继续听词", isOn: $probe.backgroundListening)
                        .accessibilityIdentifier("backgroundListening")
                    Text("默认不播放，每 12 秒换词。轻点小窗空白可收起系统播放按钮；小窗中的按钮由 iPadOS 提供。")
                        .font(.footnote).foregroundStyle(.secondary)
                    Text(probe.playing ? "正在播放" : "已暂停").accessibilityIdentifier("playbackState")
                    Text(probe.voiceStatus).accessibilityIdentifier("voiceStatus")
                    Text(probe.pipStatus).accessibilityIdentifier("pipStatus")
                    Text(probe.lastEvent).font(.footnote).accessibilityIdentifier("lastEvent")
                    Button("全部停止") { probe.stopAll() }.buttonStyle(.bordered)
                        .accessibilityIdentifier("stop")
                    Text("验证包 0.2 · 声音可在上方选择。暂停、音频中断或拔出耳机会停止；小窗不提供收藏或评分按钮。")
                        .font(.footnote).foregroundStyle(.secondary)
                }.padding(24)
            }.navigationTitle("词间 · iOS 验证")
        }
    }
}

struct VoiceSettingsScreen: View {
    @ObservedObject var probe: StudyProbe
    var body: some View {
        Form {
            voiceSection("日语", language: "ja-JP", selected: probe.japaneseVoiceID, rate: probe.japaneseRate)
            voiceSection("中文", language: "zh-CN", selected: probe.chineseVoiceID, rate: probe.chineseRate)
            Section("下载更自然的声音") {
                Text("先在系统的“无障碍／辅助功能 → 阅读与朗读（旧版叫朗读内容）→ 声音”中，下载日语和中文的增强或高级声音。无需开启旁白。")
                Text("返回验证包后点“重新检测声音”。这里只能选择系统向 App 提供的声音；下载与个人听感需在设备上确认。")
                Button("重新检测声音") { probe.refreshVoices() }.accessibilityIdentifier("refreshVoices")
                Text(probe.voiceStatus).font(.footnote).accessibilityIdentifier("selectedVoiceStatus")
            }
        }.navigationTitle("声音与试听")
            .onAppear { probe.refreshVoices() }
            .onDisappear { probe.stopPreview() }
    }
    private func voiceSection(_ title: String, language: String, selected: String, rate: Double) -> some View {
        Section(title) {
            Picker("声音", selection: Binding(get: { language == "ja-JP" ? probe.japaneseVoiceID : probe.chineseVoiceID },
                set: { probe.selectVoice($0, language: language) })) {
                Text("自动 · 优先已下载的高质量声音").tag("")
                ForEach(VoiceSelection.matching(language, in: probe.voices)) { Text($0.label).tag($0.id) }
                if !selected.isEmpty && !probe.voices.contains(where: { $0.id == selected }) {
                    Text("所选声音暂不可用 · 使用自动回退").tag(selected)
                }
            }.accessibilityIdentifier(language == "ja-JP" ? "japaneseVoice" : "chineseVoice")
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

// Render real word content as video frames. No screenshot/overlay capture or fake audio.
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
        draw(word.reading + " · " + word.pos, in: CGRect(x: 64, y: 286, width: 1152, height: 100), font: 48)
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
    AVPictureInPictureSampleBufferPlaybackDelegate, AVSpeechSynthesizerDelegate {
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
    var currentWord: ProbeWord? { words.indices.contains(index) ? words[index] : nil }
    private var display: AVSampleBufferDisplayLayer?
    private var pip: AVPictureInPictureController?
    private var observation: NSKeyValueObservation?
    private var frameTimer: Timer?
    private var wordTimer: Timer?
    private let speech = AVSpeechSynthesizer()
    private var observers: [NSObjectProtocol] = []
    private var speechGeneration = UUID()
    private var activeUtterance: AVSpeechUtterance?
    private var chinesePending: String?
    private var completedSpeechCount = 0
    private let preferences: UserDefaults
    private var previewing = false
    init(preferences: UserDefaults = .standard) {
        self.preferences = preferences
        japaneseVoiceID = preferences.string(forKey: "probe.voice.ja") ?? ""
        chineseVoiceID = preferences.string(forKey: "probe.voice.zh") ?? ""
        japaneseRate = VoiceSelection.rate(preferences.object(forKey: "probe.rate.ja") as? Double ?? 0.5)
        chineseRate = VoiceSelection.rate(preferences.object(forKey: "probe.rate.zh") as? Double ?? 0.5)
        super.init()
        do { words = try ProbeDeck.load() } catch { lastEvent = error.localizedDescription }
        speech.delegate = self
        // Configure the category without activating playback or making sound.
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
        record("初始暂停")
    }
    func attach(_ layer: AVSampleBufferDisplayLayer) {
        guard display == nil else { return }
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
        render(); if playing { sayCurrent(); scheduleWord() }; record("切换词条")
    }
    func play(requireVoice: Bool) {
        guard !words.isEmpty else { return }
        if requireVoice && currentVoice("ja-JP") == nil {
            voiceStatus = "没有日语声音，未开始听词"; record("缺少日语声音"); return
        }
        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .default)
            try audio.setActive(true)
        } catch { record("音频会话失败：" + error.localizedDescription); return }
        cancelSpeech(); playing = true; sayCurrent(); scheduleWord()
        pip?.invalidatePlaybackState(); record("用户开始")
    }
    private func scheduleWord() {
        wordTimer?.invalidate()
        wordTimer = Timer.scheduledTimer(withTimeInterval: 12, repeats: false) { [weak self] _ in
            guard let self, self.playing else { return }; self.move(1)
        }
    }
    private func cancelSpeech() {
        speechGeneration = UUID(); activeUtterance = nil; chinesePending = nil; previewing = false; speech.stopSpeaking(at: .immediate)
    }
    func refreshVoices() {
        voices = AVSpeechSynthesisVoice.speechVoices().map { ProbeVoice(id: $0.identifier, name: $0.name, language: $0.language, quality: $0.quality.rawValue) }
        func description(_ language: String, selected: String) -> String {
            guard let voice = VoiceSelection.choose(selected, language: language, voices: voices) else { return "没有可用声音，请在系统下载" }
            let fallback = !selected.isEmpty && voice.id != selected ? "（所选声音不可用，已回退）" : ""
            return voice.label + fallback
        }
        voiceStatus = "日语：" + description("ja-JP", selected: japaneseVoiceID) + "\n中文：" + description("zh-CN", selected: chineseVoiceID)
        record("已重新检测系统声音")
    }
    private func currentVoice(_ language: String) -> AVSpeechSynthesisVoice? {
        let selected = language == "ja-JP" ? japaneseVoiceID : chineseVoiceID
        guard let choice = VoiceSelection.choose(selected, language: language, voices: voices) else { return nil }
        return AVSpeechSynthesisVoice(identifier: choice.id)
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
        guard let voice = currentVoice(language) else { record("试听所需声音尚未下载"); return }
        do { try AVAudioSession.sharedInstance().setActive(true) }
        catch { record("试听音频会话失败"); return }
        previewing = true
        let phrase = language == "ja-JP" ? "日本語を毎日少しずつ勉強します。" : "每天学一点，按自己的节奏学习。"
        let utterance = AVSpeechUtterance(string: phrase); utterance.voice = voice
        utterance.rate = Float(language == "ja-JP" ? japaneseRate : chineseRate)
        activeUtterance = utterance; speech.speak(utterance); record("正在试听")
    }
    func stopPreview() { if previewing { pause() } }
    private func sayCurrent() {
        guard let word = currentWord, let voice = currentVoice("ja-JP") else { return }
        let utterance = AVSpeechUtterance(string: word.word); utterance.voice = voice
        utterance.rate = Float(japaneseRate)
        activeUtterance = utterance; chinesePending = word.meaning; speech.speak(utterance)
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard utterance === activeUtterance else { return }
        if previewing { pause(); record("试听结束"); return }
        guard playing else { return }
        completedSpeechCount += 1
        if let meaning = chinesePending, let voice = currentVoice("zh-CN") {
            chinesePending = nil
            let translated = AVSpeechUtterance(string: meaning); translated.voice = voice; translated.rate = Float(chineseRate)
            activeUtterance = translated; speech.speak(translated)
        } else { activeUtterance = nil; chinesePending = nil }
        record("系统报告朗读完成，仍需人工试听")
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
        pip.startPictureInPicture() // Only called from the user's button.
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
            "time": Date().timeIntervalSince1970]
        if let data = try? JSONSerialization.data(withJSONObject: state, options: [.prettyPrinted, .sortedKeys]),
           let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            try? data.write(to: directory.appendingPathComponent("proof-status.json"), options: .atomic)
        }
    }
    deinit {
        frameTimer?.invalidate(); wordTimer?.invalidate()
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
    }
}

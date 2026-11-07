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
                    Text("默认不播放。开始后每 12 秒换词；暂停、音频中断或拔出耳机会停止。画中画只提供系统媒体操作，不能加入收藏、评分等任意按钮。")
                        .font(.footnote).foregroundStyle(.secondary)
                    Text(probe.playing ? "正在播放" : "已暂停").accessibilityIdentifier("playbackState")
                    Text(probe.voiceStatus).accessibilityIdentifier("voiceStatus")
                    Text(probe.pipStatus).accessibilityIdentifier("pipStatus")
                    Text(probe.lastEvent).font(.footnote).accessibilityIdentifier("lastEvent")
                    Button("全部停止") { probe.stopAll() }.buttonStyle(.bordered)
                        .accessibilityIdentifier("stop")
                    Text("画中画可用不等于已通过真机或苹果审核。语音直接使用系统可用声音，不安装 Google 引擎。")
                        .font(.footnote).foregroundStyle(.secondary)
                }.padding(24)
            }.navigationTitle("词间 · iOS 验证")
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
    override init() {
        super.init()
        do { words = try ProbeDeck.load() } catch { lastEvent = error.localizedDescription }
        speech.delegate = self
        // Configure the category without activating playback or making sound.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        voiceStatus = AVSpeechSynthesisVoice(language: "ja-JP") == nil ? "未找到日语声音，请在系统中下载" : "系统日语声音可用，需实际试听确认"
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
            controller.requiresLinearPlayback = false; pip = controller
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
        if requireVoice && AVSpeechSynthesisVoice(language: "ja-JP") == nil {
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
        speechGeneration = UUID(); activeUtterance = nil; chinesePending = nil; speech.stopSpeaking(at: .immediate)
    }
    private func sayCurrent() {
        guard let word = currentWord, let voice = AVSpeechSynthesisVoice(language: "ja-JP") else { return }
        let utterance = AVSpeechUtterance(string: word.word); utterance.voice = voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate * 0.85
        activeUtterance = utterance; chinesePending = word.meaning; speech.speak(utterance)
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard playing, utterance === activeUtterance else { return }
        completedSpeechCount += 1
        if let meaning = chinesePending, let voice = AVSpeechSynthesisVoice(language: "zh-CN") {
            chinesePending = nil
            let translated = AVSpeechUtterance(string: meaning); translated.voice = voice
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

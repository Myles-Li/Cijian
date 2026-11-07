import Foundation
import Combine
import CryptoKit
import OnnxRuntimeBindings

// 可选模型与应用本体分开；只使用固定版本和校验过的文件。
// Keep optional model assets outside the app bundle, pinned and verified.
struct VoiceAsset: Codable {
    let name: String
    let remote: String
    let bytes: Int
    let sha256: String
}

enum OfflineVoiceFiles {
    static let revision = "1939ad2a8e416c0acfeecc08a694d14ef25f2231"
    static let assets = [
        VoiceAsset(name: "model.onnx", remote: "onnx/model_quantized.onnx", bytes: 92361116,
                   sha256: "fbae9257e1e05ffc727e951ef9b9c98418e6d79f1c9b6b13bd59f5c9028a1478"),
        VoiceAsset(name: "jf_alpha.bin", remote: "voices/jf_alpha.bin", bytes: 522240,
                   sha256: "56b479360aad9f367aeb8cef908f9201cf48b4555e488c5f4590c9dfcd978bb6"),
        VoiceAsset(name: "zf_xiaobei.bin", remote: "voices/zf_xiaobei.bin", bytes: 522240,
                   sha256: "5dde6e1c9c4f12c8b327bc29c0cee361a23b52b952c04636858ba637ec66e640")
    ]
    static var directory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Kokoro-1.0-int8", isDirectory: true)
    }
    static var totalBytes: Int { assets.reduce(0) { $0 + $1.bytes } }
    static func present(in directory: URL = OfflineVoiceFiles.directory) -> Bool {
        assets.allSatisfy { asset in
            (try? directory.appendingPathComponent(asset.name).resourceValues(forKeys: [.fileSizeKey]).fileSize) == asset.bytes
        }
    }
    static func verify(_ url: URL, asset: VoiceAsset) throws {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        var hasher = SHA256(); var size = 0
        while let data = try file.read(upToCount: 1024 * 1024), !data.isEmpty {
            hasher.update(data: data); size += data.count
        }
        let digest = hasher.finalize().map { String(format: "%02x", $0) }.joined()
        guard size == asset.bytes && digest == asset.sha256 else {
            throw VoiceFailure.message("声音文件校验失败，请重新下载")
        }
    }
}

enum VoiceFailure: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let value) = self { return value }; return nil }
}

final class OfflineVoiceDownload: ObservableObject {
    @Published private(set) var installed: Bool
    @Published private(set) var downloading = false
    @Published private(set) var status: String
    private let directory: URL
    private var operation: Task<Void, Never>?
    private var generation = UUID()
    init(directory: URL = OfflineVoiceFiles.directory) {
        self.directory = directory
        let ready = OfflineVoiceFiles.present(in: directory)
        installed = ready
        status = ready ? "离线模型已下载 · 93.4 MB" : "尚未下载 · 额外占用 93.4 MB"
    }
    func download() {
        guard !downloading else { return }
        downloading = true; let token = UUID(); generation = token
        let destinationRoot = directory
        operation = Task { @MainActor [weak self] in
            do {
                var directory = destinationRoot
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var values = URLResourceValues(); values.isExcludedFromBackup = true
                try directory.setResourceValues(values)
                for (index, asset) in OfflineVoiceFiles.assets.enumerated() {
                    try Task.checkCancellation()
                    self?.status = "正在下载 \(index + 1)/3 · 共 93.4 MB"
                    let url = URL(string: "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/" + OfflineVoiceFiles.revision + "/" + asset.remote)!
                    let (temporary, response) = try await URLSession.shared.download(for: URLRequest(url: url, timeoutInterval: 180))
                    defer { try? FileManager.default.removeItem(at: temporary) }
                    guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw VoiceFailure.message("下载未成功，请稍后重试") }
                    try Task.checkCancellation()
                    // 后台校验避免主界面在大文件哈希时失去响应。
                    // Hash large downloads off the UI thread.
                    try await Task.detached { try OfflineVoiceFiles.verify(temporary, asset: asset) }.value
                    try Task.checkCancellation()
                    let destination = directory.appendingPathComponent(asset.name)
                    if FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.removeItem(at: destination) }
                    try FileManager.default.moveItem(at: temporary, to: destination)
                }
                guard self?.generation == token else { return }
                self?.installed = true; self?.status = "离线模型已下载 · 93.4 MB，可断网使用"
            } catch {
                guard self?.generation == token else { return }
                self?.installed = OfflineVoiceFiles.present(in: destinationRoot)
                self?.status = Task.isCancelled ? "下载已取消，可重新下载" : "下载失败：" + error.localizedDescription
            }
            guard self?.generation == token else { return }
            self?.downloading = false; self?.operation = nil
        }
    }
    func cancel() {
        operation?.cancel(); operation = nil; generation = UUID(); downloading = false
        installed = OfflineVoiceFiles.present(in: directory); status = "下载已取消，可重新下载"
    }
    func remove() throws {
        cancel()
        // 只删除本功能的三个固定文件。 / Remove only this feature's fixed assets.
        for asset in OfflineVoiceFiles.assets {
            let url = directory.appendingPathComponent(asset.name)
            if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        }
        installed = false; status = "声音模型已删除 · 已释放存储空间"
    }
}

struct OfflinePhrase: Codable {
    let text: String
    let language: String
    let phonemes: String
    let tokens: [Int64]
}

// 固定词库提前生成发音索引，手机不携带整套分词词典。
// Fixed-deck phoneme indices avoid shipping large text frontends on the phone.
actor OfflineSynthesizer {
    private var environment: ORTEnv?
    private var session: ORTSession?
    private var verified = false
    private let directory: URL
    init(directory: URL = OfflineVoiceFiles.directory) { self.directory = directory }
    func unload() { session = nil; environment = nil; verified = false }
    static func phrases() throws -> [OfflinePhrase] {
        guard let url = Bundle.main.url(forResource: "offline-phrases", withExtension: "json") else {
            throw VoiceFailure.message("缺少离线发音索引")
        }
        return try JSONDecoder().decode([OfflinePhrase].self, from: Data(contentsOf: url))
    }
    func generate(text: String, language: String, speed: Float) throws -> Data {
        try Task.checkCancellation()
        guard let phrase = try Self.phrases().first(where: { $0.text == text && $0.language == language }),
              !phrase.tokens.isEmpty, phrase.tokens.count <= 510 else {
            throw VoiceFailure.message("这段文字尚未建立离线发音索引")
        }
        if !verified {
            for asset in OfflineVoiceFiles.assets { try OfflineVoiceFiles.verify(directory.appendingPathComponent(asset.name), asset: asset) }
            verified = true
        }
        if session == nil {
            let env = try ORTEnv(loggingLevel: .warning)
            let options = try ORTSessionOptions()
            try options.setIntraOpNumThreads(2)
            try options.setGraphOptimizationLevel(.all)
            session = try ORTSession(env: env, modelPath: directory.appendingPathComponent("model.onnx").path, sessionOptions: options)
            environment = env
        }
        try Task.checkCancellation()
        let tokens: [Int64] = [0] + phrase.tokens + [0]
        let voice = language == "ja-JP" ? "jf_alpha.bin" : "zf_xiaobei.bin"
        let styles = try Data(contentsOf: directory.appendingPathComponent(voice))
        let offset = phrase.tokens.count * 256 * MemoryLayout<Float>.size
        guard offset + 1024 <= styles.count else { throw VoiceFailure.message("声音索引超出模型范围") }
        let style = NSMutableData(data: styles.subdata(in: offset..<(offset + 1024)))
        let input = NSMutableData(data: tokens.withUnsafeBufferPointer { Data(buffer: $0) })
        let speeds: [Float] = [min(1.3, max(0.7, speed))]
        let rate = NSMutableData(data: speeds.withUnsafeBufferPointer { Data(buffer: $0) })
        let output = try session!.run(withInputs: [
            "input_ids": try ORTValue(tensorData: input, elementType: .int64, shape: [1, NSNumber(value: tokens.count)]),
            "style": try ORTValue(tensorData: style, elementType: .float, shape: [1, 256]),
            "speed": try ORTValue(tensorData: rate, elementType: .float, shape: [1])
        ], outputNames: ["waveform"], runOptions: nil)
        try Task.checkCancellation()
        guard let waveform = output["waveform"] else { throw VoiceFailure.message("模型没有生成声音") }
        let data = try waveform.tensorData() as Data
        return try Self.wave(data)
    }
    static func wave(_ floats: Data) throws -> Data {
        guard floats.count > 240 * 4 && floats.count % 4 == 0 && floats.count <= 24000 * 60 * 4 else {
            throw VoiceFailure.message("离线声音长度异常")
        }
        var samples = [Int16](); samples.reserveCapacity(floats.count / 4)
        var nonzero = false
        for offset in stride(from: 0, to: floats.count, by: 4) {
            let sample = floats.withUnsafeBytes { $0.loadUnaligned(fromByteOffset: offset, as: Float.self) }
            guard sample.isFinite else { throw VoiceFailure.message("离线声音数据异常") }
            if abs(sample) > 0.0001 { nonzero = true }
            samples.append(Int16(max(-1, min(1, sample)) * 32767))
        }
        guard nonzero else { throw VoiceFailure.message("模型仅生成了静音") }
        let pcm = samples.withUnsafeBufferPointer { Data(buffer: $0) }
        var wave = Data("RIFF".utf8)
        func integer<T: FixedWidthInteger>(_ value: T) { var v = value.littleEndian; withUnsafeBytes(of: &v) { wave.append(contentsOf: $0) } }
        integer(UInt32(36 + pcm.count)); wave.append(Data("WAVEfmt ".utf8)); integer(UInt32(16))
        integer(UInt16(1)); integer(UInt16(1)); integer(UInt32(24000)); integer(UInt32(48000))
        integer(UInt16(2)); integer(UInt16(16)); wave.append(Data("data".utf8)); integer(UInt32(pcm.count)); wave.append(pcm)
        return wave
    }
}

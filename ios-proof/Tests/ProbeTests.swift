import XCTest
import AVFoundation
@testable import CijianProof

final class ProbeTests: XCTestCase {
    @MainActor
    func testDefaultStaysLightAndLegacySettingsAreRecoverable() throws {
        let suite = "CijianProofTests." + UUID().uuidString
        let preferences = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { preferences.removePersistentDomain(forName: suite) }
        let probe = StudyProbe(preferences: preferences)
        XCTAssertEqual(probe.engine, "system")
        XCTAssertFalse(probe.playing)
        XCTAssertNil(Bundle.main.url(forResource: "model", withExtension: "onnx"))
        XCTAssertEqual(OfflineVoiceFiles.totalBytes, 93405596)
        probe.move(1); probe.selectEngine("kokoro")
        XCTAssertFalse(probe.playing)
        probe.restoreLegacyVoice()
        XCTAssertEqual(probe.index, 1)
        XCTAssertEqual(probe.engine, "system")
        XCTAssertEqual(probe.japaneseVoiceID, "")
        XCTAssertEqual(probe.chineseVoiceID, "")
        XCTAssertEqual(probe.japaneseRate, 0.425, accuracy: 0.001)
        XCTAssertEqual(probe.chineseRate, 0.5, accuracy: 0.001)
    }
    func testPhonemeIndexCoversAllSampleWordsAndPreviewPhrases() throws {
        let phrases = try OfflineSynthesizer.phrases()
        XCTAssertEqual(phrases.count, 8)
        for word in try ProbeDeck.load() {
            XCTAssertTrue(phrases.contains { $0.text == word.word && $0.language == "ja-JP" })
            XCTAssertTrue(phrases.contains { $0.text == word.meaning && $0.language == "zh-CN" })
        }
        for phrase in phrases {
            XCTAssertFalse(phrase.phonemes.isEmpty)
            XCTAssertFalse(phrase.tokens.isEmpty)
            XCTAssertLessThanOrEqual(phrase.tokens.count, 510)
        }
    }
    func testOfflineEngineSynthesizesAllEightPhrasesWithoutNetwork() async throws {
        guard let directory = Bundle(for: ProbeTests.self).url(forResource: "TestModels", withExtension: nil) else {
            throw XCTSkip("Optional model absent; run prepare_test_models.py before generating the test project.")
        }
        let synth = OfflineSynthesizer(directory: directory)
        let output = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("offline-samples")
        try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        var timings = [[String: Any]]()
        for (index, phrase) in try OfflineSynthesizer.phrases().enumerated() {
            let start = Date()
            let data = try await synth.generate(text: phrase.text, language: phrase.language, speed: 1)
            XCTAssertEqual(String(data: data.prefix(4), encoding: .ascii), "RIFF")
            XCTAssertGreaterThan(data.count, 480)
            let seconds = Double(data.count - 44) / 48000
            let elapsed = Date().timeIntervalSince(start)
            timings.append(["text": phrase.text, "language": phrase.language, "audio_seconds": seconds,
                            "generation_seconds": elapsed, "real_time_factor": elapsed / seconds])
            try data.write(to: output.appendingPathComponent("\(index)-\(phrase.language).wav"))
        }
        try JSONSerialization.data(withJSONObject: timings, options: [.prettyPrinted, .sortedKeys])
            .write(to: output.appendingPathComponent("timings.json"))
        await synth.unload()
    }
    func testCorruptOptionalModelIsRejected() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("model.onnx")
        defer { try? FileManager.default.removeItem(at: file); try? FileManager.default.removeItem(at: directory) }
        try Data("invalid model".utf8).write(to: file)
        XCTAssertThrowsError(try OfflineVoiceFiles.verify(file, asset: OfflineVoiceFiles.assets[0]))
    }
    @MainActor
    func testCancelledNeuralPreviewAndModelDeletionDoNotRestartAudio() async throws {
        guard let models = Bundle(for: ProbeTests.self).url(forResource: "TestModels", withExtension: nil) else {
            throw XCTSkip("Optional test model absent.")
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let suite = "CijianProofTests." + UUID().uuidString
        let preferences = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer {
            preferences.removePersistentDomain(forName: suite)
            for asset in OfflineVoiceFiles.assets { try? FileManager.default.removeItem(at: directory.appendingPathComponent(asset.name)) }
            try? FileManager.default.removeItem(at: directory)
        }
        let probe = StudyProbe(preferences: preferences, offlineDirectory: directory)
        let download = OfflineVoiceDownload(directory: directory)
        probe.selectEngine("kokoro"); probe.play(requireVoice: true)
        XCTAssertFalse(probe.playing)
        XCTAssertTrue(probe.voiceStatus.contains("尚未下载"))
        for asset in OfflineVoiceFiles.assets {
            try FileManager.default.copyItem(at: models.appendingPathComponent(asset.name), to: directory.appendingPathComponent(asset.name))
        }
        probe.preview(language: "ja-JP")
        try await Task.sleep(nanoseconds: 20_000_000)
        probe.move(1); probe.pause()
        try await Task.sleep(nanoseconds: 2_000_000_000)
        XCTAssertFalse(probe.hasActiveAudio)
        XCTAssertFalse(probe.playing)
        let installed = OfflineVoiceDownload(directory: directory)
        XCTAssertTrue(installed.installed)
        probe.removeOffline(installed)
        for _ in 0..<100 {
            if !installed.installed { break }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTAssertFalse(installed.installed)
        XCTAssertFalse(OfflineVoiceFiles.present(in: directory))
        XCTAssertEqual(probe.engine, "system")
        XCTAssertFalse(probe.hasActiveAudio)
        XCTAssertFalse(probe.playing)
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
        XCTAssertFalse(download.downloading)
    }
    func testVoicePickerRespectsLanguageAndListsQuality() {
        let basic = ProbeVoice(id: "ja-basic", name: "A", language: "ja-JP", quality: 1)
        let enhanced = ProbeVoice(id: "ja-enhanced", name: "B", language: "ja-JP", quality: 2)
        let premium = ProbeVoice(id: "ja-premium", name: "C", language: "ja-JP", quality: 3)
        let mandarin = ProbeVoice(id: "zh", name: "Chinese", language: "zh-CN", quality: 1)
        let cantonese = ProbeVoice(id: "hk", name: "Cantonese", language: "zh-HK", quality: 3)
        let voices = [basic, enhanced, premium, mandarin, cantonese]
        XCTAssertEqual(VoiceSelection.matching("ja-JP", in: voices), [premium, enhanced, basic])
        XCTAssertEqual(VoiceSelection.matching("zh-CN", in: voices), [mandarin])
        XCTAssertTrue(VoiceSelection.matching("zh-CN", in: [cantonese]).isEmpty)
        XCTAssertTrue(VoiceSelection.matching("ja-JP", in: []).isEmpty)
        XCTAssertEqual(VoiceSelection.rate(.nan), 0.5)
        XCTAssertEqual(VoiceSelection.rate(-1), 0.35)
        XCTAssertEqual(VoiceSelection.rate(2), 0.65)
    }
    @MainActor
    func testVoiceAndSeparateRatesPersistWithoutAutoplay() throws {
        let suite = "CijianProofTests." + UUID().uuidString
        let preferences = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { preferences.removePersistentDomain(forName: suite) }
        let probe = StudyProbe(preferences: preferences)
        probe.selectVoice("my-selected-ja", language: "ja-JP")
        probe.selectVoice("my-selected-zh", language: "zh-CN")
        probe.setRate(0.425, language: "ja-JP"); probe.setRate(0.55, language: "zh-CN")
        let restored = StudyProbe(preferences: preferences)
        XCTAssertEqual(restored.japaneseVoiceID, "my-selected-ja")
        XCTAssertEqual(restored.chineseVoiceID, "my-selected-zh")
        XCTAssertEqual(restored.japaneseRate, 0.425, accuracy: 0.001)
        XCTAssertEqual(restored.chineseRate, 0.55, accuracy: 0.001)
        XCTAssertFalse(restored.playing)
    }
    func testDeckHasOriginalIdentifiersAndBothScripts() throws {
        let deck = try ProbeDeck.load()
        XCTAssertEqual(deck.count, 3)
        XCTAssertEqual(Set(deck.map(\.id)).count, 3)
        XCTAssertTrue(deck.contains { $0.word == "コミュニケーション" })
        for word in deck {
            XCTAssertFalse(word.word.isEmpty)
            XCTAssertFalse(word.meaning.isEmpty)
            XCTAssertFalse(word.reading.isEmpty)
        }
    }
    func testRapidAlternatingNavigationReturnsToSameWord() {
        var index = 0
        for _ in 0..<1000 {
            index = ProbeDeck.moved(index, by: 1, count: 3)
            index = ProbeDeck.moved(index, by: -1, count: 3)
        }
        XCTAssertEqual(index, 0)
        XCTAssertEqual(ProbeDeck.moved(0, by: -1, count: 3), 2)
        XCTAssertEqual(ProbeDeck.moved(0, by: 1, count: 0), 0)
    }
    @MainActor
    func testEveryWordProducesDisplayableVideoBuffer() throws {
        for word in try ProbeDeck.load() {
            let sample = try WordFrame.make(word)
            XCTAssertTrue(CMSampleBufferIsValid(sample))
            let description = try XCTUnwrap(CMSampleBufferGetFormatDescription(sample))
            let size = CMVideoFormatDescriptionGetDimensions(description)
            XCTAssertEqual(size.width, 1280); XCTAssertEqual(size.height, 720)
        }
    }
    @MainActor
    func testInitialAndRepeatedStopStaySilent() {
        let probe = StudyProbe()
        XCTAssertFalse(probe.playing)
        for _ in 0..<10 { probe.move(1); probe.stopAll() }
        XCTAssertFalse(probe.playing)
        XCTAssertEqual(probe.index, 1)
    }
}

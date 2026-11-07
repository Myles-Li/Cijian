import XCTest
import AVFoundation
@testable import CijianProof

final class ProbeTests: XCTestCase {
    func testBestVoiceRespectsLanguageQualityAndExplicitChoice() {
        let basic = ProbeVoice(id: "ja-basic", name: "A", language: "ja-JP", quality: 1)
        let enhanced = ProbeVoice(id: "ja-enhanced", name: "B", language: "ja-JP", quality: 2)
        let premium = ProbeVoice(id: "ja-premium", name: "C", language: "ja-JP", quality: 3)
        let mandarin = ProbeVoice(id: "zh", name: "Chinese", language: "zh-CN", quality: 1)
        let cantonese = ProbeVoice(id: "hk", name: "Cantonese", language: "zh-HK", quality: 3)
        let voices = [basic, enhanced, premium, mandarin, cantonese]
        XCTAssertEqual(VoiceSelection.choose("", language: "ja-JP", voices: voices), premium)
        XCTAssertEqual(VoiceSelection.choose("ja-basic", language: "ja-JP", voices: voices), basic)
        XCTAssertEqual(VoiceSelection.choose("removed", language: "ja-JP", voices: voices), premium)
        XCTAssertEqual(VoiceSelection.choose("hk", language: "zh-CN", voices: voices), mandarin)
        XCTAssertNil(VoiceSelection.choose("", language: "zh-CN", voices: [cantonese]))
        XCTAssertNil(VoiceSelection.choose("", language: "ja-JP", voices: []))
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

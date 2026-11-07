import XCTest
import AVFoundation
@testable import CijianProof

final class ProbeTests: XCTestCase {
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

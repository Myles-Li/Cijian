import XCTest

final class ProbeUITests: XCTestCase {
    private var app: XCUIApplication!
    private func reveal(_ element: XCUIElement) {
        for _ in 0..<6 {
            if element.isHittable { return }
            app.swipeUp()
        }
    }
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.staticTexts["position"].waitForExistence(timeout: 15))
    }
    func testVoiceSettingsAndPreviewDoNotStartContinuousPlayback() {
        app.buttons["voiceSettings"].tap()
        XCTAssertTrue(app.buttons["previewJapanese"].waitForExistence(timeout: 10))
        reveal(app.buttons["previewJapanese"])
        app.buttons["previewJapanese"].tap()
        reveal(app.buttons["previewChinese"])
        app.buttons["previewChinese"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
        app.buttons["voiceSettings"].tap()
        reveal(app.buttons["refreshVoices"])
        XCTAssertTrue(app.buttons["refreshVoices"].waitForExistence(timeout: 5))
        app.buttons["refreshVoices"].tap()
        attach("voice-selection-and-quality", XCUIScreen.main.screenshot())
    }
    func testInitialPauseAndRapidNavigation() {
        XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
        let first = app.staticTexts["word"].label
        for _ in 0..<6 { app.buttons["next"].tap() }
        XCTAssertEqual(app.staticTexts["word"].label, first)
        for _ in 0..<6 { app.buttons["previous"].tap() }
        XCTAssertEqual(app.staticTexts["word"].label, first)
        XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
        attach("iOS-probe-in-app", XCUIScreen.main.screenshot())
    }
    func testPiPStartsOnlyFromButtonAndCanStop() throws {
        reveal(app.buttons["pipExperiment"])
        app.buttons["pipExperiment"].tap()
        let pipButton = app.buttons["pip"]
        XCTAssertTrue(pipButton.waitForExistence(timeout: 10))
        reveal(pipButton)
        let possible = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: pipButton)
        guard XCTWaiter.wait(for: [possible], timeout: 18) == .completed else {
            attach("PiP-unavailable-on-runner", XCUIScreen.main.screenshot())
            throw XCTSkip("This simulator did not enable PiP; real-device validation remains pending.")
        }
        pipButton.tap()
        let active = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label == %@", "画中画已开启"), object: app.staticTexts["pipStatus"])
        XCTAssertEqual(XCTWaiter.wait(for: [active], timeout: 15), .completed)
        XCUIDevice.shared.press(.home)
        sleep(3)
        attach("PiP-over-home-screen", XCUIScreen.main.screenshot())
        app.activate()
        app.swipeDown()
        app.buttons["stop"].tap()
        XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
    }
    func testNativeCardAndOptionalModelAreDefault() {
        XCTAssertTrue(app.staticTexts["meaning"].exists)
        XCTAssertFalse(app.buttons["pip"].exists)
        attach("native-word-card", XCUIScreen.main.screenshot())
        app.buttons["voiceSettings"].tap()
        XCTAssertTrue(app.buttons["downloadModel"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["offlineModelStatus"].label.contains("尚未下载"))
        XCTAssertFalse(app.buttons["cancelModelDownload"].exists)
        attach("optional-offline-model-not-downloaded", XCUIScreen.main.screenshot())
    }
    func testBackgroundListeningRequiresOptInAndCanStop() {
        app.buttons["play"].tap()
        if app.staticTexts["playbackState"].label == "正在播放" {
            XCUIDevice.shared.press(.home); sleep(2); app.activate()
            XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
            let toggle = app.switches["backgroundListening"]
            if !toggle.isHittable { app.swipeUp() }
            toggle.tap(); app.buttons["play"].tap()
            XCUIDevice.shared.press(.home); sleep(4); app.activate()
            XCTAssertEqual(app.staticTexts["playbackState"].label, "正在播放")
            app.buttons["stop"].tap()
            XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
        } else {
            XCTAssertTrue(app.staticTexts["voiceStatus"].label.contains("没有日语声音"))
        }
    }
    private func attach(_ name: String, _ screen: XCUIScreenshot) {
        let attachment = XCTAttachment(screenshot: screen); attachment.name = name
        attachment.lifetime = .keepAlways; add(attachment)
    }
}

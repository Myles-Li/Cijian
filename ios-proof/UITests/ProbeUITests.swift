import XCTest

final class ProbeUITests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.staticTexts["position"].waitForExistence(timeout: 15))
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
        let pipButton = app.buttons["pip"]
        XCTAssertTrue(pipButton.waitForExistence(timeout: 10))
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
        app.buttons["stop"].tap()
        XCTAssertEqual(app.staticTexts["playbackState"].label, "已暂停")
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

//
//  Tammy_UITests.swift
//  Tammy UITests
//
//  Created by Michael Thiele on 24.02.26.
//

import XCTest

final class Tammy_UITests: XCTestCase {

    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.

        // In UI tests it is usually best to stop immediately when a failure occurs.
        continueAfterFailure = false

        // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
    }

    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }

    @MainActor
    func testExample() throws {
        // UI tests must launch the application that they test.
        let app = XCUIApplication(bundleIdentifier: "de.connect2x.tammy.testdata")
        app.launchEnvironment["SCREENSHOTS"] = "YES"
        print("--- SCREENSHOTS ---")
        setupSnapshot(app)
        app.activate()
        let bob = app.buttons/*@START_MENU_TOKEN@*/.containing(.staticText, identifier: "Bob").firstMatch/*[[".element(boundBy: 20)",".containing(.staticText, identifier: \"🥰\").firstMatch",".containing(.staticText, identifier: \"Bob\").firstMatch",".containing(.staticText, identifier: \"17:10\").firstMatch"],[[[-1,3],[-1,2],[-1,1],[-1,0]]],[1]]@END_MENU_TOKEN@*/
        _ = bob.waitForExistence(timeout: 5.0)
        snapshot("RoomList")
        bob.tap()
        let send = app/*@START_MENU_TOKEN@*/.images["send"]/*[[".buttons[\"send\"].images",".buttons.images[\"send\"]",".images[\"send\"]"],[[[-1,2],[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/.firstMatch
        _ = send.waitForExistence(timeout: 5.0)
        snapshot("Room")
    }
}

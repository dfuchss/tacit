import TammyUI

if let value = ProcessInfo.processInfo.environment["SCREENSHOTS"] {
    if (value == "YES") {
        print("Special runner for making screenshots!")
        let showRoom = ShowRoomKt.showRoom // cannot be used from UITests, since different process!
        try ScreenshotMainKt.screenshotMain(args: CommandLine.arguments, showRoom: showRoom)
    } else {
        try MainKt.main(args: CommandLine.arguments)
    }
} else {
    try MainKt.main(args: CommandLine.arguments)
}
 
let showRoom = ShowRoomKt.showRoom

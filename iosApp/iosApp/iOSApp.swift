import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Register all Kotlin↔Swift bridges before any UI renders
        ChimeSdkSetup.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

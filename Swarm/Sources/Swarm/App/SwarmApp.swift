import SwiftUI

@main
enum Entry {
    static func main() {
        SelfTest.runIfRequested()
        SwarmApp.main()
    }
}

struct SwarmApp: App {
    var body: some Scene {
        Window("Swarm", id: "main") {
            Text("Swarm")
                .frame(minWidth: 600, minHeight: 400)
        }
    }
}

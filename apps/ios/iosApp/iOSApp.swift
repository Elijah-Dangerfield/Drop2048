import SwiftUI
import UIKit
import ComposeApp

@main
struct iOSApp: App {
    
    let permissionManager = IOSPermissionManager()
    let reviewLauncher = IOSReviewLauncher()
    let adNetwork = IOSAdNetwork()
    let storeBilling = IOSStoreBilling()
    private let iOSAppComponent: IosAppComponent

    init() {
        self.iOSAppComponent = create(
            permissionManager: permissionManager,
            reviewLauncher: reviewLauncher,
            adNetwork: adNetwork,
            storeBilling: storeBilling
        )
        iOSAppComponent.telemetry.initialize()
        // Construct every @AutoInit singleton up front — resolving the set is
        // what forces construction (AppEventDispatcher's lifecycle attach etc.).
        _ = iOSAppComponent.autoInits
    }
    
    var body: some Scene {
        WindowGroup {
            RootComposeView(appComponent: iOSAppComponent)
            .onOpenURL { url in
                // Forward URLs from custom-scheme links and Universal Links
                // into the Kotlin DeepLinkBridge — App.kt collects from it
                // and calls navController.handleDeepLink.
                iOSAppComponent.deepLinkBridge.emit(url: url.absoluteString)
            }
        }
    }
}

struct RootComposeView: View {
    @Environment(\.scenePhase) private var scenePhase
    let appComponent: IosAppComponent

    var body: some View {
        ComposeView(appComponent: appComponent)
            .ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    let appComponent: IosAppComponent

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(appComponent: appComponent)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}


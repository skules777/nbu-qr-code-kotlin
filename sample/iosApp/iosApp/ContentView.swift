import SwiftUI
import UIKit
import NBUQRSample

/// The whole screen is the shared Compose UI from `sample/shared`.
struct ContentView: View {
    var body: some View {
        // Compose handles safe areas and the keyboard itself (Scaffold insets, imePadding).
        ComposeView().ignoresSafeArea()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

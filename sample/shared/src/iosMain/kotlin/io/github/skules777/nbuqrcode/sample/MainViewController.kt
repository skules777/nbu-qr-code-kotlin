package io.github.skules777.nbuqrcode.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Викликається з SwiftUI (`iosApp/iosApp/ContentView.swift`). */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

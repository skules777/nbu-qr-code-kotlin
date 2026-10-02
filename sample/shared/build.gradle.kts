import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "io.github.skules777.nbuqrcode.sample.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions.jvmTarget = JvmTarget.JVM_11
        androidResources {
            enable = true
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "NBUQRSample"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":nbu-qr-code"))
            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
    }
}

compose.resources {
    packageOfResClass = "io.github.skules777.nbuqrcode.sample.resources"
}

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    // Consumers need Kotlin 2.3+, not the compiler version used here. 2.3 is the floor because
    // kotlin.time.Instant (validUntil, createdAt) is stable only since then.
    coreLibrariesVersion = "2.3.0"

    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_3
        languageVersion = KotlinVersion.KOTLIN_2_3
        // NBUQRDecimal is an expect class actualised by java.math.BigDecimal and NSDecimalNumber.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }

    android {
        namespace = "io.github.skules777.nbuqrcode"
        compileSdk = 37
        minSdk = 21
        compilerOptions.jvmTarget = JvmTarget.JVM_11
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.zxing.javase)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.zxing.core)
        }
    }
}

// Robolectric reaches into JDK internals that JDK 24+ no longer exports by default.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
        sourceLink {
            localDirectory = rootDir
            remoteUrl("https://github.com/skules777/nbu-qr-code-kotlin/tree/main")
        }
    }
}

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.hossain.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // Suppress EMULATOR error to allow dry-run on emulators; remove for real device runs
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    // Benchmarks must run against a non-debuggable build for accurate timing
    testBuildType = "release"

    buildTypes {
        debug {
            // Keep debug for development, but benchmarks use release
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // AndroidX Benchmark
    androidTestImplementation(libs.androidx.benchmark.junit4)

    // Test framework
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    // Libraries under benchmark
    androidTestImplementation(libs.kotlin.textmate.compose)
    androidTestImplementation(libs.shiki.sdk)
    androidTestImplementation(libs.compose.highlight)

    // Compose UI text (AnnotatedString, SpanStyle, Color)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui)

    // For ActivityScenario (compose-highlight WebView benchmark)
    androidTestImplementation(libs.androidx.activity.compose)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

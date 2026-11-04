plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
        }
        // All selected targets run on a JVM. Keep Java/JSON code in one source directory,
        // compiled against Android's APIs on Android and the JDK on desktop.
        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
            dependencies { implementation("com.squareup.okhttp3:okhttp:4.12.0") }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/jvmMain/kotlin")
            dependencies {
                api("org.json:json:20240303")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
            }
        }
    }
}

android {
    namespace = "tv.jumpflix.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

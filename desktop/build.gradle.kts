import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("net.java.dev.jna:jna:5.19.1")
    testImplementation(kotlin("test-junit"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Reuse the original branding, fonts, licenses and guest catalog; no copied asset tree.
sourceSets.main {
    resources {
        srcDir("../app/src/main/assets")
        exclude("auth-config.json")
    }
}

val brandResources = layout.buildDirectory.dir("generated/brandResources")
val prepareBrandResources by tasks.registering(Sync::class) {
    from("../app/src/main/res/font") { into("font") }
    from("../app/src/main/res/drawable-nodpi/mascot.png") { into("drawable") }
    into(brandResources)
}
compose.resources {
    packageOfResClass = "tv.jumpflix.desktop.resources"
    customDirectory("main", prepareBrandResources.map { brandResources.get() })
}

tasks.test { systemProperty("java.awt.headless", "true") }

compose.desktop {
    application {
        mainClass = "tv.jumpflix.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Jumpflix"
            packageVersion = "0.1.0"
            description = "JUMPFLIX parkour cinema — desktop TV preview"
            vendor = "JUMPFLIX"
            copyright = "JUMPFLIX contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("jdk.crypto.ec", "jdk.unsupported", "jdk.accessibility")
            windows { menuGroup = "Jumpflix"; shortcut = true }
            macOS { bundleID = "tv.jumpflix.desktop"; packageVersion = "1.0.0" }
            linux { debMaintainer = "JUMPFLIX contributors" }
        }
    }
}

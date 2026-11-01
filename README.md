# JUMPFLIX for Android and Android TV

A native Kotlin/Jetpack Compose client for the JUMPFLIX parkour film archive.
One APK adapts to phone touch input or Android TV / Google TV remote navigation.

The visual reference is [Nuvio](https://github.com/NuvioMedia/NuvioTV): cinematic
artwork, a large featured film, horizontal browsing rows, dark surfaces, and clear
remote focus. This is a focused JUMPFLIX client with selected upstream reuse.

## First alpha

- Home, discovery rows, title/creator/athlete search, and film/series details.
- Device-local saved titles and resume progress, including separate episode keys.
- Media3 playback for authorized direct/HLS streams.
- Official YouTube and Vimeo embeds on phones, with an external-provider fallback.
- Official YouTube/Vimeo app handoff on TV, where WebView support varies. Playback
  inside another provider's app cannot report progress back to JUMPFLIX.
- TV launcher support and remote-friendly buttons and cards.
- A dated real catalog snapshot for browsing when refresh is unavailable.
- No app credentials, private API tokens, or service-role keys are bundled.

Accounts and cross-device sync, ratings/reviews, maps, casting, notifications,
offline video downloads, and iOS/tvOS builds are outside this first alpha.

## Build

Requires JDK 17 and an Android SDK with platform 35 and build-tools 35.0.0.
Set `JAVA_HOME` and `ANDROID_HOME`, or point `sdk.dir` in `local.properties` at
your SDK. The Gradle wrapper downloads and verifies Gradle 8.11.1.

On this Windows machine, `scripts/build-windows.ps1` can also find the isolated
toolchain created under `%USERPROFILE%\.codex\cache\jumpflix-android-toolchain`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n tv.jumpflix.android/tv.jumpflix.MainActivity
```

On macOS/Linux use `bash ./gradlew` in place of `gradlew.bat`.

## Backend and local development

The website remains in the separate `jumpflix.tv` repository. This client consumes:

- `GET /api/mobile/v1/catalog?offset=0&limit=200`
- `GET /api/mobile/v1/series/{slug}`

The new endpoints must be deployed to the website before production catalog
refresh works. Until then, the default APK displays the bundled, dated snapshot
and identifies it as a saved catalog. Streaming still requires internet access.

For development against a website running on your Windows host:

```powershell
# In the jumpflix.tv checkout:
npm run dev -- --host 127.0.0.1 --port 5173

# In this checkout, for a USB-connected phone:
adb reverse tcp:5173 tcp:5173
.\gradlew.bat :app:assembleDebug -PjumpflixApiBaseUrl=http://127.0.0.1:5173
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

For an Android emulator, use `-PjumpflixApiBaseUrl=http://10.0.2.2:5173`.
Only debug builds permit cleartext to loopback/emulator host addresses.

Refresh the bundled public snapshot with Node.js 20+:

```powershell
node scripts/snapshot-catalog.mjs http://127.0.0.1:5173
```

## Testing phone and TV

Use your phone over ADB for playback, rotation, suspend/resume, and provider apps.
Use an Android TV emulator for focus, Back, search, scrolling, details, saving,
and native player controls. A successful build or emulator run is not evidence
of playback on real TV hardware. Provider embeds, codecs, audio, and performance
still need a TV/Google TV tester before claiming device support.

## License and upstream reuse

Native client source is **GPL-3.0-only**, independently of the website's license.
See [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). When distributing
binaries, provide the corresponding client source and required license notices.
This local repository has not been published and has no upstream remote yet.

Catalog metadata, JUMPFLIX branding, and third-party videos/artwork retain their
respective ownership and permissions; the code license does not grant media rights.

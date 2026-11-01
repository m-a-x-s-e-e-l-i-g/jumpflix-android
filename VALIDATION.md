# Alpha validation — 2026-11-01

## Verified locally

- Windows build with JDK 17, Android SDK 35, AGP 8.9.2 and Gradle 8.11.1.
- Debug APK compiled; five JVM tests pass; Android lint has zero errors.
- Android TV API 34 x86 emulator boots with Windows Hypervisor Platform.
- Two instrumented TV tests pass using D-pad center activation: film detail →
  save → My list, and an accent-insensitive search (`kevin franzen`).
- Manual D-pad navigation opens a film from the hero.
- TV layout and a 432 × 936 phone layout were rendered and visually inspected.
  The phone layout was rendered on the TV emulator using the debug-only layout
  override; this is not physical-phone or phone-OS validation.
- Bundled catalog contains 329 real public JUMPFLIX titles, including hydrated
  DB-backed series episodes. The app explicitly labels this as saved data.
- The local website catalog endpoint returns 200, rejects invalid pagination
  with 400, and returns 404 for unknown series. Website API/SEO checks: 29 passed,
  three skipped; Svelte check: zero errors, five existing warnings; build passed.

## Still requires device testing / deployment

- No physical Android phone was connected during this session.
- No real Android TV / Google TV device was available.
- Direct/HLS playback, official provider embeds, audio, rotation and suspend/resume
  need physical-device tests. No successful playback on physical hardware is claimed.
- TV YouTube/Vimeo playback opens the official provider app. Progress from that
  other app is unavailable to JUMPFLIX and is not fabricated.
- The mobile API changes exist in the separate `jumpflix.tv` checkout and have
  not been deployed. The default APK uses its saved catalog until those routes
  are deployed. A debug build can target the local website using `adb reverse`.
- Account sync and the other deferred features listed in README are not implemented.

## Reproduce

```powershell
.\scripts\build-windows.ps1
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires an Android TV emulator or authorized device on ADB.
Build output: `app/build/outputs/apk/debug/app-debug.apk`. Test reports and
screenshots remain in ignored local build/artifact directories.

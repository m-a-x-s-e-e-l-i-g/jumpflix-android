# Alpha validation — 2026-11-01

## Verified locally

- Windows build with JDK 17, Android SDK 35, AGP 8.9.2 and Gradle 8.11.1.
- Debug APK compiled; five JVM tests pass; Android lint has zero errors.
- Android TV API 34 x86 emulator boots with Windows Hypervisor Platform.
- Two instrumented TV tests pass using D-pad center activation: film detail →
  save → My list, and an accent-insensitive search (`kevin franzen`).
- Manual D-pad navigation opens a film from the hero.
- The debug APK was installed over ADB on a physical A063 running Android 15.
  Cold launch succeeded, the app remained running, and its playback screen was
  subsequently observed in the foreground. No app-process AndroidRuntime errors
  were reported during this check. This does not yet confirm video/audio playback.
- TV layout and a 432 × 936 phone layout were rendered and visually inspected.
  The phone layout was rendered on the TV emulator using the debug-only layout
  override; this is not physical-phone or phone-OS validation.
- Bundled catalog contains 329 real public JUMPFLIX titles, including hydrated
  DB-backed series episodes. The app explicitly labels this as saved data.
- The local website catalog endpoint returns 200, rejects invalid pagination
  with 400, and returns 404 for unknown series. Website API/SEO checks: 29 passed,
  three skipped; Svelte check: zero errors, five existing warnings; build passed.

## Still requires device testing / deployment

- No real Android TV / Google TV device was available.
- Direct/HLS playback, YouTube embeds, audio, rotation and suspend/resume still need
  physical-device tests. The alpha 2 follow-up below records the Vimeo title that
  has since been verified on the connected phone.
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

## Alpha 2 follow-up

- Added the actual popcorn character and website wordmark font to the app and its icon.
- Added existing-account email/password sign-in through the website's Supabase Auth
  service. Its public settings endpoint returned 200 and confirmed email auth is enabled.
- Twelve JVM tests pass, including password-grant request shape, sanitized failures,
  token refresh rotation, local-scope logout, server identity checks, and Vimeo hashes.
- Three Android TV emulator instrumented tests pass. The additional test verifies
  that tokens are encrypted in SharedPreferences, restored using Android Keystore
  after store recreation, and removed on clear. No real account credentials are used.
- Build and Android lint pass (zero errors; advisory warnings remain).
- The update was installed on the physical A063 / Android 15 phone. Its real branding
  and navigation were visually inspected.
- `Holi Shit` played through the official Vimeo embed on that phone: the player
  reported position advancing from 29.378 to 61.253 seconds of 174.458 seconds, and
  a video frame was visually verified. This establishes this title/device combination;
  it does not establish audio, rotation, all Vimeo titles, or TV playback.
- `Balancing Organisms` returned 403 and Vimeo `PrivacyError` in an independent HTTPS
  request. It also failed the public oEmbed lookup. The owner-side cause is unknown.
- The user confirmed successful email/password sign-in on the phone. No password
  was requested in chat or entered through agent tooling.
- Cross-device library/history sync remains unimplemented.

## Alpha 3 Google sign-in

- Added system-browser Google OAuth with PKCE/SHA-256, a strict custom-scheme
  callback, a ten-minute encrypted pending transaction, and single-use code exchange.
- Seventeen JVM tests pass, including the RFC7636 challenge vector, callback spoofing
  and token-fragment rejection, expiry, and the PKCE token endpoint request.
- The existing Supabase project's auth redirect allowlist now contains only the
  additional exact native callback `tv.jumpflix.android://auth/callback`. Read-back
  verified the website URL, Google enabled state, and existing redirects were preserved.
- A public authorization request returned 302 to `accounts.google.com` with OAuth
  state and the existing provider callback. It did not sign in a real user.
- The user still needs to complete the Google account selection/consent on the phone
  to validate the entire live round trip.

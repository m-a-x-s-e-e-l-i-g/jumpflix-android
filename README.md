# JUMPFLIX for Android and Android TV

A native Kotlin/Jetpack Compose client for the JUMPFLIX parkour film archive.
One APK adapts to phone touch input or Android TV / Google TV remote navigation.

The visual reference is [Nuvio](https://github.com/NuvioMedia/NuvioTV): cinematic
artwork, a large featured film, horizontal browsing rows, dark surfaces, and clear
remote focus. This is a focused JUMPFLIX client with selected upstream reuse.

## First alpha

- Home, discovery rows, title/creator/athlete search, and film/series details.
- 2:3 portrait posters throughout home rows, search, and My list on phone and TV.
- Device-local saved titles and resume progress, including separate episode keys.
- Media3 playback for authorized direct/HLS streams.
- Custom JUMPFLIX controls: play/pause, timeline, ±10 seconds, speed, mute, and
  phone fullscreen. Native streams fill the screen; controls hide during playback
  and return on touch or remote input. Media remote buttons also work natively.
- Official YouTube and Vimeo embeds on phones, with an external-provider fallback.
- Official YouTube/Vimeo app handoff on TV, where WebView support varies. Playback
  inside another provider's app cannot report progress back to JUMPFLIX.
- TV launcher support and remote-friendly buttons and cards.
- The original JUMPFLIX popcorn character, mascot launcher icon, and Bebas Neue wordmark.
- Email/password sign-in to the existing JUMPFLIX account service, encrypted session
  persistence with Android Keystore, refresh on restore, and device-local sign-out.
- Google sign-in through the system browser, with a one-time PKCE return to the app.
- Shared account XPOP total and breakdown, with the website's popcorn mark and reward notice.
- Automatic watched rewards at 85% playback, manual movie/episode watched actions,
  and signed-in ratings and public reviews using the existing website account rows.
- A dated real catalog snapshot for browsing when refresh is unavailable.
- No private API tokens, service-role keys, or user sessions are bundled.

Full cross-device resume/list sync, maps, casting, notifications,
offline video downloads, and iOS/tvOS builds are outside this first alpha.

## Accounts

Use the **Account** tab to sign in with the same email and password as jumpflix.tv.
The bundled `auth-config.json` contains only the website's public Supabase URL and
public anonymous/publishable key. It contains no service-role key or user session.
Passwords go directly to that HTTPS Auth service and are never persisted. Session
tokens are encrypted with an Android Keystore AES-GCM key, and backups/transfers
of app data are disabled. Sign-out uses `scope=local` so website sessions stay intact.

Choose **Continue with Google** for the same Google account you use on the website.
The app opens the system browser, keeps the private PKCE verifier encrypted on the
device, and exchanges the returned one-time code directly with Supabase. It never
receives your Google password. Pending sign-ins expire after ten minutes and are
consumed once. The exact Supabase redirect entry is:

```text
tv.jumpflix.android://auth/callback
```

This entry was added to the existing project's auth redirect allowlist on
2026-11-01; the website URL and Google provider configuration were preserved.
For another Supabase deployment, add that exact URI to Auth → URL Configuration.
Completed watches, ratings, reviews, and XPOP use the same account data as the
website. Saved titles and in-progress resume positions remain device-local.

## XPOP

The Account screen displays the current total and its watching, rating, review,
and approved-contribution breakdown. The header links to that total. Scores use
the same weights as the website: 5 per watched item, 15 per rating, 25 per review,
and 10 per approved contribution unit (`xp_units`, rather than suggestion count).
Contributions submitted through the website are included; native suggestion forms
are not part of this build.

Native and phone-provider playback queue a completed watch at 85%, matching the
website threshold and provider/episode keys. The client writes through the existing
account-owned API and unique keys. Replaying an already-watched entry or editing a
rating/review does not add another scoring row. A legacy base movie watch is also
recognized. The popcorn reward notice follows a successful account update.

Pending completed watches survive restarts and remain bound to the account that
watched them. They retry on refresh/foreground restore; another account cannot claim
them. Anonymous viewing is never imported as earned XPOP after sign-in. Failed sync
keeps the previous total or an unavailable state, rather than inventing a zero score.
TV provider apps cannot report completion, so use **Mark watched** on the movie or
episode after viewing. Opening a provider app alone earns no points.

Open **Rate & review** in a title's activity section to choose a 1–10 rating or
explicitly post/edit a public review (up to 2,000 characters). These actions require
sign-in and a loaded account state. They use the existing RLS-protected tables;
the APK contains no service credentials. There are no schema/configuration changes.

When signed in, the Account tab shows **Google connected** for a linked identity.
Otherwise **Connect Google account** uses the authenticated identity-linking flow
and keeps the current JUMPFLIX user ID. Google identities already owned by another
JUMPFLIX account produce a conflict instead of merging or switching users.
Supabase manual identity linking is enabled for this project; another deployment
must enable it in Auth settings before this connection option can work.

## Vimeo availability

The player preserves unlisted-video privacy hashes and catches provider privacy
errors, including errors sent before the Vimeo SDK is ready. It shows a native
explanation and an official-provider fallback. Owner restrictions are not bypassed.
On 2026-11-01, `Balancing Organisms` (223802198) returned HTTP 403 / `PrivacyError`
from Vimeo even outside this app. Other Vimeo titles can remain playable.

## User stats

Choose **Account → Your stats** after signing in. The screen shows the shared
XPOP total, watched film/series/episode entries, saved playback time, archive
progress, average rating and rating distribution, contribution status counts,
paginated rated titles, and the latest 20 reviews. Title links open JUMPFLIX.
Pull down at the top of the stats page to refresh. On TV, press Up at the top.
Screen readers also have a Refresh stats action; there is no refresh button.

Stats read the website's existing account RPCs and owner-scoped tables. Failed
refreshes keep the last loaded account snapshot and display an error; changing
accounts clears it. No backend deployment or schema changes are required.
Saved playback time is the sum of stored account positions, which may omit time
for manually marked watches. In-progress resume positions remain device-local.

## Custom playback controls

The website's old player used Vidstack custom controls with official YouTube and
Vimeo providers underneath. Android now has native JUMPFLIX controls driven by
Media3 for direct/HLS sources and the official provider SDKs for phone embeds.
The provider viewport and its own controls stay visible and unobscured. Supported
speeds depend on the video/provider; an unavailable Vimeo control is a nonfatal
message. TV YouTube/Vimeo still opens the provider app.

The catalog's JUMPFLIX Bunny CDN receives the public website referrer for manifest
and segment requests, as required by its existing configuration. Account tokens
are never attached to video requests.

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

Provider-control bridge checks run without network requests:

```powershell
node --test scripts/test-player-bridge.mjs
```

The normal instrumentation suite skips the real-stream network smoke test. To
run that check explicitly against a chosen emulator, install the debug APK and
test APK, then run:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e class tv.jumpflix.player.NativePlaybackSmokeTest -e nativePlaybackSmoke true tv.jumpflix.android.test/androidx.test.runner.AndroidJUnitRunner
```

It checks decoded HLS video, elapsed time, pause, seek, and mute using a real
catalog title. It requires internet access and does not establish audio quality
or real-device compatibility.

## License and upstream reuse

Native client source is **GPL-3.0-only**, independently of the website's license.
See [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). When distributing
binaries, provide the corresponding client source and required license notices.
This local repository has not been published and has no upstream remote yet.

Catalog metadata, JUMPFLIX branding, and third-party videos/artwork retain their
respective ownership and permissions; the code license does not grant media rights.

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
- The user confirmed that the live Google sign-in round trip works on the phone.

## Alpha 4 portrait posters and signed-in account UI

- Home, search, and My list use 2:3 portrait posters on phone and TV. Card widths
  were adjusted for the taller artwork; featured/detail artwork stays wide.
- Signed-in Google accounts show **Google connected** instead of another login button.
  Accounts without a Google identity show **Connect Google account**, using the
  authenticated Supabase linking endpoint and binding completion to the same user ID.
- Provider badges are populated from server identities (with server app-metadata
  fallback), never from editable user metadata. Restore refreshes those identities.
- Nineteen JVM tests pass, including the authenticated linking request and provider
  parsing/round-trip storage checks. Android build and lint pass.
- Manual linking was enabled in the existing Supabase auth config. Read-back verified
  the website URL, redirect allowlist, and Google enabled state were preserved.
- No live account-linking attempt was performed on the user's already-linked account.
- Six emulator instrumented tests pass: three account states, detail/save/library,
  accent-insensitive search including scrolling through portrait results, and encrypted
  session/pending-link persistence. The keyboard Search action dismisses the keyboard.
- Portrait posters were visually verified on the physical phone. Its signed-in Account
  screen reported **Google connected** and no **Continue with Google** button.
- The phone disconnected before the final keyboard-search adjustment could be
  reinstalled. The portrait/account update was already installed and verified;
  the latest APK additionally includes the verified keyboard-search adjustment.

## Alpha 5 custom player controls

- Added native JUMPFLIX play/pause, timeline seeking, ±10 seconds, speed, mute,
  and phone fullscreen controls. Native Media3 video fills the screen behind
  controls that hide while playing and return on user input. D-pad seeking and
  media remote play/pause/seek keys are supported.
- Phone YouTube/Vimeo controls use the official iframe SDKs. The entire provider
  viewport remains visible; providers still render the video. TV provider handoff
  remains in place. This is not a port of every Vidstack website feature.
- Four offline Node bridge checks execute the actual provider scripts with SDK
  doubles: YouTube supported rates/state/seek bounds, Vimeo mute/seek/nonfatal
  speed errors, origin/source validation of privacy errors, and early lifecycle
  pause before SDK readiness. These do not establish live provider playback.
- Nineteen JVM tests, Android build, and lint pass (zero lint errors).
- Eight regular emulator instrumented checks pass, including the two new custom
  control tests for playback state, D-pad seeking, and phone fullscreen availability.
- The real HLS stream initially returned 403 in Media3. Both catalog HLS manifests
  independently returned 403 without the site referrer and 200 with it. Native
  loading now applies that public referrer only to the catalog's exact CDN host.
- An additional opt-in emulator network check starts `Enter the Breach` with
  Media3, checks decoded video and advancing position, and exercises custom pause,
  forward seek, and mute. It also verifies that controls hide, return on D-pad
  input, and pause through the media remote key. It passes after the CDN request fix.
- The updated APK is built locally. No physical phone was connected for this
  update; new controls, fullscreen/rotation, live provider controls, audio, and
  real TV playback still need physical-device testing. Prior Vimeo playback
  evidence above applies to the previous player UI.

## Alpha 6 XPOP and account activity

- Account and header totals use the existing website's stats RPC and approved
  suggestion XP units. Weights match `jumpflix.tv/src/lib/xp.ts`: 5 watching,
  15 rating, 25 reviewing, and 10 per contribution unit. No new points ledger,
  schema, RLS policy, or production auth configuration was created.
- Watched rewards begin at 85%. Movie/provider and episode keys match the website,
  with duplicate and legacy-base checks. Completed watches are queued by account,
  survive process recreation, and retry on foreground restore or manual refresh.
  Guest history is not credited to a later signed-in account. Saved lists and
  in-progress resume positions are still local.
- Added explicit manual watched actions for movies/episodes, 1–10 ratings, and
  public review creation/editing. Edits upsert the same unique account/media row;
  award notices follow successful account updates, not optimistic local increments.
- Twenty-nine JVM tests pass. The ten new checks cover XP weights, completion
  boundary, website-compatible keys, account-scoped requests, contribution-unit
  pagination, duplicate/legacy watches, review bounds, and sanitized API failures.
- Fourteen emulator checks pass, covering account UI, browsing, custom player, XPOP balance/refresh,
  rating/review controls, guest/error states, phone-width touch controls, and
  account-separated persisted offline completions. Rating/review UI tests use
  callbacks with fixture data; no real user's review or rating was posted.
- Live read-only checks confirmed the current table constraints and owner RLS.
  The stats RPC returned 200 / not-found for a nonexistent fixture UUID, and the
  approved-contribution endpoint returned 200 / an empty anonymous result. This
  confirms endpoint availability, not a live signed-in reward round trip.
- Build and Android lint pass. The updated alpha 6 APK has not been installed on
  a physical phone: only the TV emulator is connected. Live user totals/awards,
  account changes and offline recovery still require signed-in physical-device
  testing; no real Android TV hardware was available.

## User stats follow-up

- Account → Your stats adds watched totals and film/series/episode breakdown,
  saved playback time, archive progress, average rating and 1–10 distribution,
  contribution statuses, paginated rated titles, and the latest 20 reviews.
- Stats use the current signed-in account through the shared encrypted-session
  repository. Account changes and leaving the screen clear its loaded snapshot;
  failed refreshes retain only the same account's previous snapshot with an error.
- Time is labeled as stored playback progress, with an explicit note about manual
  watches and device-local in-progress positions. It is not measured session time.
- Seven new JVM tests pass for account-scoped read requests, paginated ratings and
  contributions, review metadata, empty profiles, failed requests, time formatting,
  and bounded archive progress. The full current JVM suite also passes.
- Four stats UI checks exercise TV charts and rating pagination, phone-width empty
  and refresh states, error handling, Account navigation, Back, account switching,
  and sign-out. Account/XPOP UI regression checks also pass on the TV emulator.
- All three existing stats RPCs returned 200 for a nonexistent fixture UUID in
  read-only live probes; the overview reported not found, the distribution was
  empty counts, and the rated list was empty. No real user's stats were altered.
- Build and Android lint pass. Phone/TV stats layouts use emulator fixture data;
  a live signed-in physical-device stats round trip remains unverified. The phone
  is not currently connected to ADB. Existing map work in the checkout is separate.

## Physical phone follow-up, 2026-11-01

- Installed the current alpha 7 debug APK with `adb install -r` on the connected
  A063. Package version 7 / `0.1.0-alpha7` and successful launch were confirmed.
- The existing account session was preserved and Account showed Google connected.
- Account → Your stats completed its live signed-in refresh on the physical phone.
  The stats XPOP total matched the account header, loading finished, and no stats
  error was displayed. Checks reported booleans without exposing account details.
- This verifies installation, session preservation, and live account stats loading.
  It does not extend the previous playback or map hardware-validation evidence.

## Alpha 8 navigation and stats refresh

- Removed the native map destination, screen/model code, bundled spot data, map
  snapshot script, map-specific tests, and MapLibre dependency. Website map routes
  and data were not modified. The debug APK no longer includes the map SDK/data.
- Replaced the stats refresh button with Material 3 pull-to-refresh. Pulling at
  the top refreshes once; regular scrolling and additional pulls while loading
  do not start duplicate requests. TV Up at the top and a screen-reader refresh
  action provide equivalent access without a visible button.
- Build, lint, all 36 JVM tests, and all 18 emulator instrumentation checks pass,
  including pull gestures, loading guards, ordinary scroll behavior, TV refresh,
  accessibility retry, absent map navigation, and existing native playback checks.
- The phone disconnected after the alpha 7 check. Alpha 8 is built and tested on
  the emulator; installation and physical pull-gesture validation are pending.

## Alpha 9 general UI refinement

- Added a stable compact phone masthead with the original popcorn character and
  wordmark. XPOP loading no longer inserts a separate row above page content.
- Unified warm dark colors, typography, primary/secondary button hierarchy, and
  48dp controls. Restored the JUMPFLIX red family; the primary text/button color
  pair measures 4.92:1, and muted text/background measures 9.51:1.
- Featured/detail backdrops use public wide YouTube artwork when available and
  fall back to the supplied poster. Browse cards remain 2:3 portraits. Details
  pair a portrait poster with the title, metadata, and clear viewing actions.
- Search has quieter horizontally scrollable filters; the account page groups
  identity, connection status, stats, and XPOP. Account data now uses pull-to-
  refresh instead of another manual refresh button. Stats use a full-width
  watched summary and paired supporting metrics on narrow screens.
- Added labeled TV navigation and a landscape phone rail. Reselecting Account
  returns from nested stats to the account root. Native map removal is preserved.
- Build, lint, all 36 JVM tests, and 19 emulator checks pass. Checks include
  account pull refresh without starting authentication, nested account navigation,
  TV focus, search/save/library, ratings/reviews, stats gestures, and HLS playback.
- Phone portrait/landscape and TV layouts were visually checked on the emulator.
  Screenshots and UI fixtures are in the ignored artifacts/cache directories.
  The user deferred phone installation/testing until later; no alpha 9 physical
  device validation is claimed.

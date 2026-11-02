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

## Alpha 10 video spots and songs

- Film details expose approved spot chapters and existing song credits, with a
  direct action that skips long descriptions/cast lists. Separate spot/song
  selectors keep soundtracks reachable. Series chapters use the canonical
  `series:<id>:ep:<episodeId>` key, independent of device-local season keys.
- Spot entries include names, countries, time ranges, and parkour.spot links.
  Songs include title/artist, explicit-lyrics labels, and Spotify links where
  supplied. Manual songs without Spotify remain visible. Shared/absent song
  timestamps are treated as untimed instead of guessing the current song.
- Timestamp actions explicitly override resume position. Native/phone-provider
  playback starts at that cue; TV provider handoffs pass official timestamp URLs.
  Provider apps decide whether to honor them and do not expose JUMPFLIX overlays.
- The custom player follows the current spot range/song start and exposes a
  native credits dialog for seeking or opening credited links. The action remains
  available in landscape; native controls stay visible while its dialog is open.
- Public chapters and public soundtrack rows are fetched independently, without
  user tokens. Ten-minute caches preserve previous lists on partial failure.
  Retry, loading, and empty states are distinct. No database, auth configuration,
  or website changes were made. The native map remains removed.
- Build, lint (zero errors), 40 JVM tests, and all 25 emulator checks pass. New
  coverage includes malformed/unsafe credit data, episode isolation, public-only
  request headers, cached partial failures, cue/link callbacks, unavailable titles,
  TV selector focus/shortcut navigation, player labels/dialog seeking, and native
  HLS startup at a requested cue. No public ratings/reviews were posted.
- An opt-in read-only emulator check fetched the published Peanutbutter credits:
  Carcavelos Beach at 03:28 with Portugal metadata, plus O Mar Apaga by Banda Eddie.
  Live film UI showed 14 spot chapters and 8 songs. Phone and TV credit selectors
  and native player controls were visually checked; screenshots stay in ignored
  `artifacts/`. Emulator display settings were restored after phone previews.
- Phone installation/physical testing remains deferred at the user's request.
  Alpha 10 is built; no physical Android/TV validation is claimed for this change.

## Alpha 11 creator and athlete profiles

- Names under Created by/Featuring open native profiles. Long credit lists show
  six names initially and can expand. Search now switches between Films & series
  and People, with accent-insensitive name search and creator/athlete role labels.
- Profiles combine canonical website name slugs, unique title counts, creator and
  athlete roles, and filmographies filtered by All credits, Created by, or Featuring.
  Initials identify people when no portrait is available. No biography/photo is
  inferred. Profiles are for filmmakers/creators and athletes, as clarified by the
  user; music artist profiles were not added.
- Existing public Instagram handles are read without account tokens and cached
  for ten minutes. Failed refreshes retain saved links; films remain available
  from the saved catalog. Canonical website profiles can also be opened externally.
- Film/profile navigation retains its parent trail, search query/mode, and profile
  filters. Profile and Search headers scroll with their content so short landscape
  layouts retain access to results. No map, website, schema, or auth changes.
- Build, lint (zero errors), 45 JVM tests, and all 30 emulator checks pass. Coverage
  includes canonical name/role merging, distinct title counts, public request
  scoping, cached link failures, filmography filters, social-link callbacks, TV
  focus on first mount, and film/profile/search Back navigation. The opt-in public
  profile check fetched Chris Cobas's existing Instagram handle without a session.
- Kevin Franzén's profile was visually checked on a phone layout, including its
  9 catalog titles and published Instagram link. Resizing to landscape retained
  the profile and access to the filmography. Lester Castro Nilsson's creator/athlete
  profile was checked with TV remote input. Screenshots remain under ignored
  `artifacts/`; the emulator display was restored afterward.
- Physical phone installation/testing remains deferred at the user's request.
  No physical Android TV validation is claimed.

## Alpha 12 remove redundant website actions

- Removed Open on JUMPFLIX from film/series details and creator/athlete profiles.
  These screens already provide their content natively. Profiles without social
  links no longer render an empty links row.
- Useful provider, Spotify, parkour.spot, and Instagram actions remain. No playback,
  account, catalog, or profile data behavior changed. Phone testing stays deferred.
- Build and lint pass, along with all 45 existing JVM tests and seven targeted
  browsing/profile emulator checks. Production UI contains no Open on JUMPFLIX
  actions; no new tests were added for the button removal.

## Alpha 13 remove duplicate YouTube controls

- YouTube IFrame API embeds now set the documented `controls=0` and `fs=0`
  player options. JUMPFLIX provides the playback controls and phone fullscreen
  action, avoiding a second transport bar/fullscreen button inside the video.
- The official YouTube viewport, branding, provider messages, playback SDK, resume
  position, and native watching/credits integration remain. No iframe masking,
  cropping, pointer interception, or stream extraction was added. Vimeo and TV
  provider handoff behavior are unchanged.
- The four provider-script checks pass. The YouTube check captures the actual
  SDK configuration and exercises play/pause, clamped seeks, supported speeds,
  and mute using SDK doubles. Build, lint, and all 45 JVM tests also pass.
- Both targeted Android control checks pass on the emulator: native play/mute/
  remote seeking and phone fullscreen/unknown-duration seeking behavior.
- Physical phone testing remains deferred. These automated checks do not claim
  live YouTube rendering or playback validation on a physical device.

## Alpha 14 visible public reviews

- Film/series details now show public reviews before spots/songs and account
  composition controls. A Reviews action jumps directly to the list with TV
  focus. Viewing reviews does not require sign-in or expanding Rate & review.
- Entries show the public display name/Anonymous, localized date, edited marker,
  and full review body with expansion for long text. Ten-review pages use stable
  updated-time/ID cursors and deduplicate appended results. No user IDs or email
  addresses are requested; email-shaped author names omit the domain.
- The public feed reads the same reviews_with_author view as the website, without
  a session token. Posting/editing one's review reloads the public list. Cancelled
  or outdated requests cannot overwrite the selected title's current feed; failed
  refreshes/load-more requests preserve displayed reviews and offer a retry.
- A read-only production query verified public review visibility and cursor
  pagination for a title with multiple reviews. No reviews were created or edited
  as part of verification. No website, database, or auth changes were made.
- Build/lint and all 49 JVM tests pass. Nine targeted review/browsing emulator
  checks pass, covering guest access, shortcuts, expansion, pagination callbacks,
  TV retry, and the live public review read. The other 25 emulator cases also
  passed in the broader regression run; an ambiguous test selector was corrected
  before the final targeted run.
- The published review on Op zen Hollands was visually checked in guest phone
  and TV layouts, including TV focus on the individual review. Screenshots remain
  in ignored `artifacts/`. Emulator display settings were restored. Physical phone
  installation/testing remains deferred at the user's request.

## Alpha 14 physical phone check

- On 2026-11-01 the reconnected Nothing A063 received versionCode 14 / alpha14
  using an in-place ADB update. The existing signed-in account/XPOP header remained
  available. No app data was cleared and no instrumentation APK ran on the phone.
- The published review for Op zen Hollands loaded on the physical phone, showing
  its Anonymous author, date, and complete text. No review/rating submissions were
  performed during this check.
- Its official YouTube embed played visible video, with the native clock advancing
  to 00:28 of 50:00. Only the JUMPFLIX transport bar was visible. Native mute worked
  (Sound/Unmute state), and native pause returned the control to Play. Audio was
  unmuted again while paused, then playback was closed back to the title page.
- Physical evidence remains in ignored `artifacts/phone-alpha14-*` files. A cold
  title deep link was used: warm title links are not routed by the current
  MainActivity.onNewIntent implementation and remain a separate navigation issue.
  This check does not establish live YouTube behavior on Android TV or all devices.

## Alpha 15 player safe areas, compact navigation, and song search

- Native player controls respect display-cutout, visible system-bar, and mandatory
  gesture insets, including when system bars are hidden. Video remains full bleed.
  Back is an icon in the portrait toolbar and landscape control row; mute and
  fullscreen use accessible icons with state-specific descriptions.
- Film details use a bookmark icon for My list. The primary action says Resume
  for unfinished films; account-watched films take precedence and show Rewatch,
  which explicitly starts at zero despite any existing local checkpoint.
- The phone masthead is smaller and shows the XPOP icon and number without the
  trailing XPOP text. Creator/athlete profiles replace it with a fixed Back/name
  bar, also available to TV remote navigation.
- Search includes song titles and soundtrack artists alongside existing film/person
  fields. A paginated public video_songs/songs index is cached locally; matching
  happens locally without per-keystroke requests. Partial refresh failures preserve
  the previous cache and expose retry. No database, website, or auth changes.
- Build and lint pass (zero errors), along with 54 JVM tests and all 36 emulator
  instrumented tests. New coverage includes watched-action precedence/reset,
  soundtrack matching and cache pagination/failure, production player inset
  handling, and a live song-name search. Existing profile/save/control checks
  cover the updated icons and fixed header. No physical Android TV was available.
- VersionCode 15 / 0.1.0-alpha15 was installed in place on the Nothing A063.
  Sign-in remained available; no app data was cleared and no instrumentation APK
  ran on the phone. The compact header, bookmark, and profile Back/name bar were
  visually checked. Profile Back returned to the originating film.
- On that phone, Op zen Hollands showed Rewatch despite its previous checkpoint
  and opened at 0:00 / 50:01. The portrait Back bounds began at y=151px, below
  the reported 136px top cutout. Landscape Back and fullscreen/mute icons also
  stayed clear of the side cutout. Live video advanced using the native controls;
  it was paused, unmuted while paused, and closed after the check. Normal playback
  updated the local checkpoint; no watched/rating/review mutations were submitted.
- Physical Search for O Mar Apaga returned Peanutbutter. Screenshots remain in
  ignored artifacts/phone-alpha15-* files. This validates the tested title/device
  combination, not every provider, cutout geometry, or physical TV.

## Alpha 16 season and episode layout

- Series put the episode list before reviews and account activity, with an Episodes
  shortcut from the title actions. A horizontally scrolling selector shows one
  season at a time, preserving custom collection names such as Extras. Single-season
  titles show the season name without a redundant selection button.
- Episodes are compact numbered rows with one main play/resume/rewatch action,
  watched status, and a small local-progress bar where duration is known. Watched
  episodes explicitly rewatch from zero. Unavailable episodes cannot start playback.
  Expanding one episode reveals its credit and account-watched actions; other
  episodes stay collapsed. Selection and expansion use saved Compose state.
- Build, all 54 JVM tests, and lint pass with zero lint errors. Nine targeted
  emulator checks pass: seven browsing regressions plus two episode interaction
  checks. They cover remote season selection, selected-season isolation, Resume
  and Rewatch callback positions, unavailable-source handling, expanding/collapsing
  secondary actions, and the Reviews shortcut after the changed list ordering.
- Alpha16 was installed in place on the connected Nothing A063, preserving sign-in.
  CAVEMEN's five Season 1 episodes and three Extras were visually checked. Switching
  seasons replaced the list, updated its count, and kept the selection visible.
  Expanding an Extras episode exposed Spots & songs and Mark watched without adding
  action rows to its siblings. No watched/rating/review submissions were made, and
  no instrumentation APK ran on the phone. Screenshots remain in ignored
  artifacts/phone-alpha16-* files. No physical Android TV validation is claimed.
- No website, database, auth, or playback-provider changes were required.

## Alpha 17 Home discovery — 2026-11-02

- Removed the saved-catalog/retry banner from Home. Saved browsing and the empty
  catalog retry remain; refresh/status are still available in About. A read-only
  production request to /api/mobile/v1/catalog?offset=0&limit=1 returned 404, so this
  client change does not restore live catalog downloads or deploy the website API.
- Featured picks are six random available, free, non-explicit films with artwork
  and playback. Their order is seeded by the local calendar day and canonical IDs,
  remaining stable for that day even if catalog input order changes. Home updates
  the day at local midnight while active and when resuming on a new day.
- Home puts Continue watching (when present), New to the archive, and Explore the
  archive in that order. Explore starts shuffled and has a labeled shuffle action.
  Randomize changes only that row and resets its horizontal position after the new
  items compose, avoiding lazy-list key anchoring retaining a prior poster.
- Build, 56 JVM tests, and lint pass with zero lint errors. The two new unit tests
  cover date stability/change, eligibility, input ordering, and small/empty catalogs.
  Seven browsing regression checks passed; a creator selector was made unambiguous
  for featured films that credit the same person in multiple roles. Two focused
  Home emulator checks additionally pass for phone touch and TV remote layouts,
  covering section order, absent banner, shuffle after sideways scrolling, and
  saved-state restoration. These were targeted runs, not a full emulator suite.
- Alpha17 was installed in place on the Nothing A063, preserving its signed-in
  account. Its daily feature and Continue watching loaded without the banner.
  The phone showed New to the archive directly above Explore, with a readable
  Randomize button. After scrolling Explore sideways, Randomize restored the new
  row's beginning and left both section headings in place. No playback, watched,
  rating, or review mutations were submitted; no instrumentation APK ran on the
  phone. Screenshots remain in ignored artifacts/phone-alpha17-* files.
- No physical Android TV testing or backend deployment was performed.

## Alpha 18 unseen featured picks — 2026-11-02

- Signed-in featured picks exclude the account's completed active movie watches,
  including legacy base keys and any provider-specific key for the same film.
  Exclusions happen after the daily shuffle so the remaining order stays stable.
  Queued completions update known exclusions immediately while sync retries.
- The existing authenticated XPOP repository reads all watched movie pages with
  the current user's bearer token and explicit owner filter. It retains the last
  known set on failure and clears it on account binding changes. History-read
  failures do not discard an already confirmed XPOP summary/reward update.
- Unknown history withholds featured titles, with a quiet loader while fetching.
  Failed initial history reads and exhausted unseen selections omit the hero;
  the rest of Home remains available. Guest picks do not use private history.
- Build/lint pass with zero lint errors, all 59 JVM tests pass, and ten targeted
  emulator checks pass. New coverage includes provider-key normalization, stable
  refill ordering, all-watched exhaustion, paginated owner-scoped reads, denied
  reads, and live UI updates from unknown to known and changed watched sets.
- A read-only REST query using the new filters and an unassigned account ID
  returned 200 with zero rows. This validates the live query shape, not a real
  signed-in user's history or a full RLS audit. No schema/auth/policy changes.
- The phone disconnected from ADB before installation. Alpha18 is built but was
  not installed or validated with the user's real account on the phone. No watched
  records were created/changed for QA, and no physical TV testing was performed.

## Alpha 19 visible watched posters — 2026-11-02

- The shared browsing card displays a checkmark and Watched label over a dark
  poster overlay in Home rows, Search, My list, and creator/athlete filmographies.
  Its accessible watched state updates with the account, without changing the
  card's title, action, or TV focus border. Watched posters hide stale resume bars;
  unfinished titles retain their progress indicator. Paid/unavailable labels stay
  separate at the top of the artwork.
- The existing owner-scoped paginated history read now includes whole-series and
  episode records. Movie provider keys normalize to the same film; only an explicit
  series base watch marks a whole series as watched. One completed episode does not.
  Cards, featured exclusions, and film Rewatch decisions share this normalization.
  There are no per-card requests or schema/auth changes.
- Build, 60 JVM tests, lint with zero errors, and 14 targeted emulator checks pass.
  Coverage includes movie/series identity, old-provider Rewatch, badge appearance
  and removal, accessibility state, resume-bar precedence, touch/remote actions,
  Home status updates, and watched filmography filters. Phone-style and TV-style
  card fixtures were visually inspected with actual public poster artwork and a
  paid label; the TV focus outline remains clear.
- The touch-card check also passed at 1080 × 2400 / density 420, and its portrait
  fixture was visually inspected. Emulator display settings were restored afterward.
- A read-only REST query with an unassigned owner and the broadened filters
  returned 200 with zero rows. This is query-shape validation, not verification
  with a real signed-in account. The phone remains disconnected: alpha19 is built
  but not installed or physically tested. No real watched/rating/review entries
  were changed. Ignored screenshots are under artifacts/watched-cards-*.

## Alpha 19 physical phone check — 2026-11-02

- Installed versionCode 19 / 0.1.0-alpha19 in place on the reconnected Nothing
  A063. The existing sign-in and 11,600 XPOP header remained available. No app
  data was cleared and no instrumentation APK ran on the phone.
- Home loaded the account's watched history: Op zen Hollands and Holi Shit showed
  visible Watched poster badges, while unfinished Bound By Movement retained its
  progress indicator. The daily featured pick was Reborn (Nathan Jones feat.
  Bastille), replacing the earlier unfiltered Capstone Verky BTS pick.
- Searching Op zen Hollands showed its Watched badge. Opening the title offered
  Rewatch despite its old resume checkpoint. Home and Search screenshots were
  visually inspected and remain in ignored artifacts/phone-alpha19-* files.
- Returned to Home without starting playback or changing watched, rating, review,
  or saved-list records. This verifies the observed films and phone, not every
  account/title combination or physical Android TV hardware.

## Alpha 21 shorter Home headings — 2026-11-02

- Home uses New and Explore. Updated the existing test selectors and current
  documentation to match; the Randomize action is unchanged.
- App and instrumentation APK compilation pass. Alpha21 was installed in place
  on the Nothing A063; both shortened headings were confirmed in its UI hierarchy.
  No broader tests were rerun for this label change.

## Alpha 20 Continue Watching — 2026-11-02

- Continue Watching excludes account-watched films across provider keys and
  watched episodes across local season keys. Another unfinished episode keeps
  its series eligible. Unknown account history withholds the row until loaded.
- Debug build, lint and all 62 JVM tests passed. All five Home UI tests passed
  on the Android TV emulator, including watched-history loading and updates in
  both phone and remote layouts.
- Installed versionCode 20 / 0.1.0-alpha20 in place on the Nothing A063 without
  clearing app data. The existing account and XPOP total remained available.
  The inspected Home screenshot shows only unfinished Bound By Movement in
  Continue Watching; watched titles are excluded. Evidence is retained in the
  ignored artifacts/phone-alpha20-home.png screenshot.
- No playback or account-history changes were performed. Physical Android TV
  hardware and every account/title combination were not tested.

## Alpha 22 lighter search and browsing — 2026-11-02

- Film/song search text is normalized once during background catalog/credit
  loading. Queries are normalized once per edit, and people matching reuses the
  existing canonical index keys. Search evaluates only its visible mode. Query
  state remains saved at the navigation root but is read inside Search, preserving
  Back navigation without invalidating the surrounding app UI on each keystroke.
- Home's recent/category/continuing groups are remembered by their actual inputs.
  Initial saved-list/history reads and history reloads run on Dispatchers.IO.
  Save mutations keep their existing ordering. Poster loading already uses Coil's
  layout constraints; no new image policy or reduced-quality artwork was added.
- The new compatibility/performance test compares the old matcher with the new
  matcher over 329 titles, 1,407 people, and 17 incremental queries. All result IDs
  must match, including songs, accents, blank queries, and multiword input. Separate
  checks cover copied/renamed film and music data, ampersands, and non-Latin names.
  Timed results are consumed through a volatile sink; there is no brittle speed
  threshold in the tests. Five-sample median timings in the final host JVM run:

  | Matching workload | Previous matcher | Current matcher |
  | --- | ---: | ---: |
  | Films, all 17 queries | 66.13 ms | 1.62 ms |
  | People, all 17 queries | 34.61 ms | 2.98 ms |

  These measure matching work with prepared data, not overall app latency or
  first-load time. Baseline and final logs remain in the ignored tool cache.
- On the physical Nothing A063, a fixed ADB typing sequence (kevin franzen) was
  recorded with dumpsys gfxinfo in three runs before and after the first pass.
  The 95th-percentile frame buckets were 400/450/450 ms before and 121/93/101 ms
  after, giving medians of 450 and 101 ms. Frame counts and jank percentages varied;
  this supports a reduction in long typing frames, not a general FPS or low-end
  hardware guarantee. Raw captures remain in ignored artifacts/perf-* files.
- The first alpha22 pass was installed in place on that phone, with sign-in and
  local data preserved. The phone disconnected before the final refinements
  (background search preparation and tighter state-read scope) could be installed
  or measured. The final APK therefore still needs reinstallation and phone QA.
- Final app/test builds, lint with zero errors, and all 63 JVM tests pass. All 19
  targeted emulator tests pass, including live credited-song search, query/profile
  Back navigation, saving, watched posters, Continue Watching, and episode actions.
  This run used two virtual CPUs and 1,536 MB configured RAM (about 1,505,428 kB
  visible to Android). It is constrained-emulator validation, not a physical budget
  phone or Android TV test. No account-history mutations or instrumentation APK
  installation on the physical phone were performed.

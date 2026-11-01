# Source provenance and licenses

## NuvioTV

- Project: https://github.com/NuvioMedia/NuvioTV
- Revision: `2d4748cc0ed8743443cd6be4681cb7ac6772fac3` (inspected 2026-11-01).
- License: GNU GPL v3; full text in `LICENSE` and app assets `licenses/GPL-3.0.txt`.
- Copyright: the NuvioTV contributors, retained as upstream attribution.
- Reused file: `app/src/main/java/com/nuvio/tv/ui/components/LightweightCard.kt`.
- Local file: `app/src/main/java/tv/jumpflix/ui/nuvio/LightweightCard.kt`.
- Modification: package namespace and provenance header only.

The TV focus card draws the border and handles remote select independently of
the phone UI. Broader home/detail layouts are new JUMPFLIX implementations inspired
by the requested Nuvio reference. This is not a complete fork of NuvioTV.
Nuvio's add-on system, source discovery, torrent/debrid integrations, profiles,
analytics, update service, and credentials are not included.

## Noto Sans

- Source: https://github.com/NuvioMedia/NuvioMobile/tree/46febb568348aa5e135bff0d4d5c914933aff5a0/iosApp/iosApp/Resources/Fonts
- `NotoSans-Regular.ttf` copied as `app/src/main/res/font/noto_sans.ttf`.
- SIL Open Font License 1.1, retained in `third-party/NotoSans-OFL.txt` and app assets.
- This font has its own OFL license, separate from Nuvio's application source.

## Gradle wrapper

- Source: https://github.com/gradle/gradle/tree/v8.11.1
- Files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`.
- Apache License 2.0; original script headers retained.
- The wrapper verifies the Gradle distribution with its pinned SHA-256.

## JUMPFLIX branding and Bebas Neue

- Original character images are copied unchanged from the project owner's website
  assets: `jumpflix.png`, `sad-jumpflix.webp`, and `searching-jumpflix-logo.webp`.
- The adaptive icon places the original character on the existing brand-red background.
- The wordmark uses the same Bebas Neue family as the website.
- Font source: https://github.com/google/fonts/tree/main/ofl/bebasneue
- SIL Open Font License 1.1 in `third-party/BebasNeue-OFL.txt` and app license assets.
- Branding/artwork remains the project owner's material; the native code license
  does not independently relicense it.

## Other dependencies

AndroidX/Compose/Media3, OkHttp, Coil, Kotlin and JUnit retain their upstream
licenses. Versions are pinned in `app/build.gradle.kts` and `build.gradle.kts`.
JUMPFLIX branding is supplied by the project owner. Catalog/provider artwork is
remote third-party content, not relicensed as application source.

# djungleskwidgets ✧

Photo widgets for an Android home screen. No adverts, no accounts, no tracking. The app
does not even ask for internet access, so it *cannot* show ads or send her photos anywhere.
CI fails the build if that ever changes.

## What it does

- **Seven frames:** soft square, circle, arch, heart, flower, polaroid, postage stamp
- **Nine filters:** original, dreamy, vintage, golden hour, frost, noir, pop, vhs, faded
- **Captions** in seven fonts (including a VCR on-screen display and an arcade high-score
  table), outline and paper colours
- **Camcorder stamp:** "PLAY ▶" in one corner and the time and date the photo was taken
  in the other, read from the photo's camera data, like a frame of an old home video
- **Tilt, film grain and soft shadow** for the finishing touch
- **Opacity** from fully solid down to a ghostly 20%, so the wallpaper can show through
- **Pinch and drag** on the preview to frame each photo; double-tap to reset
- **Several photos per widget:** tap the widget to flip to the next one, or let it
  shuffle by itself every 15 minutes, hourly, every 6 hours or daily
- **Six channels** to flip between for the app's look: arcade, high score, roller rink,
  camcorder, sunday drive and record shop
- **🎲 surprise** button that rolls a random look

### The retro look

The app looks like an evening spent between an arcade and a VCR: pixel lettering
([VT323](https://fonts.google.com/specimen/VT323) for the on-screen display,
[Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) for the arcade lettering),
chunky square buttons that sink into a hard shadow when pressed, cabinet stripes, CRT
scanlines, tape grain and a tracking band that rolls slowly down the screen. The design
preview is a camcorder viewfinder with a live clock. The mood board it was drawn from is in
[`design/retro/`](../design/retro/) at the root of the repository.

Anyone who had picked one of the old pastel vibes lands on the nearest channel: strawberry
milk → roller rink, matcha latte → sunday drive, lavender haze → high score, cloud nine →
camcorder, butter → record shop, midnight → arcade.

Widgets resize freely, and every widget remembers its own design. On Android 12 and later,
long-pressing a widget offers to reconfigure it.

## One-time setup: a signing key

Android only lets an update replace an app signed with the same key, so give CI one
key to sign every build with. Do this before the first install, or the first update will
need an uninstall (which loses her widgets). On any computer with Java:

```sh
keytool -genkeypair -keystore glimmer.jks -alias glimmer -keyalg RSA -keysize 2048 \
  -validity 36500 -dname "CN=Glimmer"     # choose a password when asked
base64 -w0 glimmer.jks > glimmer.jks.txt  # macOS: base64 -i glimmer.jks -o glimmer.jks.txt
```

Then in GitHub: repository **Settings → Secrets and variables → Actions → New repository
secret**, twice:

| Name | Value |
|---|---|
| `GLIMMER_KEYSTORE_BASE64` | the contents of `glimmer.jks.txt` |
| `GLIMMER_KEYSTORE_PASSWORD` | the password you chose |

Keep `glimmer.jks` somewhere safe and out of the repository. Without the secrets, CI still
builds a working APK but warns that it is signed with a throwaway key.

### Why some names still say "glimmer"

The app began life as Glimmer. A few names were kept on purpose, because changing them
would stop phones that already have it from updating in place, or lose what is on them:

- the package ID `app.glimmer.widgets` (and the Kotlin package that matches it): a new ID
  is a different app to Android, and widgets already on the home screen would vanish
- the on-phone storage names (`glimmer_widgets`, `glimmer_settings`, `glimmer-shuffle`),
  which hold her saved designs and the shuffle schedule
- the signing key file, alias and `GLIMMER_*` secrets above, so CI keeps using the same key

## Publishing a release

Push a version tag and CI does the rest:

```sh
git tag v1.1.0
git push origin v1.1.0
```

The tagged build gets version name `1.1.0`, and once it is green a **Djungleskwidgets v1.1.0**
release appears on the repository's **Releases** page with `djungleskwidgets-1.1.0.apk` (and
its SHA-256 checksum) attached and notes drawn from the merged pull requests. Tags must start
with `v` and a digit. A tagged build refuses to run without the signing key above, since a
release signed with a throwaway key could not update her phone. If a release run fails part
way, re-run it from the Actions tab: it refreshes the files on the existing release.

## Installing it on her phone

1. Open the repository's **Releases** page and, under the latest release, download
   `djungleskwidgets-X.Y.Z.apk`. (Untagged test builds live under **Actions** →
   **Djungleskwidgets Android** → a green run → the **djungleskwidgets-apk** artifact, a zip
   holding `djungleskwidgets-1.0.N.apk`.)
2. Get the APK onto the phone (Google Drive, a USB cable, Nearby Share, email to herself).
   Better still, open the Releases page in the phone's own browser and download it there.
3. Tap it. Android will ask to allow installs from that app (Files, Drive, …); allow it once.
4. Open **Djungleskwidgets**, tap **✚ new widget**, design away, then **add to home screen ✧**.

Updates install the same way, straight over the old version, and her widgets stay put.

## Checking a change before handing it over

Every push that touches `djungleskwidgets/` (other than Markdown) builds the APK, then installs a
"preview" build on an Android emulator. A demo activity in `app/src/preview/` makes sample
photos and pins four widgets through the launcher's real "add to home screen" dialog. The
run then taps one of them and walks through the app in a few colour themes. The
**djungleskwidgets-screenshots** artifact holds the screenshots, a contact sheet of every frame and
filter, and the emulator's logcat. The **Screenshots as text** job prints the same pictures
as base64 JPEGs, for tools that can read job logs but cannot download artifacts.

## How it works

| Piece | Where |
|---|---|
| Design data (JSON in SharedPreferences) | `app/src/main/java/app/glimmer/widgets/data/` |
| Bitmap renderer: frames, filters, captions | `…/render/` |
| Widget provider, pinning, shuffle worker | `…/widget/` |
| Jetpack Compose UI and themes | `…/ui/` |
| CI demo driver (preview build only) | `app/src/preview/` |

The widget is a single image: everything is drawn into one bitmap by `WidgetRenderer`,
which also draws every preview inside the app, so what she sees while designing is
exactly what lands on her home screen. Picked photos are copied, downsized, into the
app's private storage, so no storage permission is needed and deleting a photo from
the gallery does not break a widget.

## Building locally

Needs JDK 17 and the Android SDK (Android Studio brings both):

```sh
./gradlew assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

Fonts are from Google Fonts under the SIL Open Font License; see `font-licences/`.

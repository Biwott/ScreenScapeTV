# ScreenScape for Google TV

A full-screen browser shell that opens **https://screenscape.me/** with its own WebView,
built for Android TV / Google TV. Appears on the TV home row with the ScreenScape banner
and icon.

---

## What's in the app

| Feature | Notes |
|---|---|
| Full-screen WebView | No address bar, no browser chrome — the site fills the TV |
| D-pad pointer | An on-screen cursor the remote drives, with acceleration and edge-scrolling |
| Focus mode | Press **MENU** on the remote to switch to native link-to-link D-pad navigation |
| OK button | Synthesises a real tap wherever the pointer is |
| Back button | Goes back through page history, then asks before exiting |
| Desktop user agent | The site serves its full layout instead of the phone layout |
| Autoplay + DRM permissions | Video starts without needing a gesture |
| Offline screen | Branded retry screen if the TV loses connection |
| Installs anywhere | Same APK works on TVs, phones and tablets |

Remote controls:

- **D-pad** — move the pointer (hold to accelerate; push against an edge to scroll)
- **OK / Center** — click
- **Back** — page back, then exit prompt
- **MENU** — toggle pointer mode ⇄ focus mode

---

## Building the APK

### Option A — GitHub Actions (no PC setup needed)

1. Create a new GitHub repository.
2. Upload the contents of this folder to it.
3. Go to the **Actions** tab and run **Build ScreenScape APK** (or just push — it runs automatically).
4. When it finishes, download the **ScreenScape-APK** artifact from the run page.
5. Unzip it. Inside is `app-release.apk`.

This is the easiest route — GitHub installs the Android SDK for you and builds it for free.

### Option B — Android Studio

1. Open Android Studio → **Open** → select this folder.
2. Let it sync (it downloads the SDK and dependencies on first run).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. The APK lands in `app/build/outputs/apk/release/`.

### Option C — command line

Requires JDK 17 and the Android SDK, with `ANDROID_HOME` set.

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

> The release build is signed with the standard debug key so it installs immediately.
> That's fine for sideloading. You'd only need a real keystore to publish on Play.

---

## Installing on your Google TV

You need **Developer options** and **unknown sources** enabled first:

1. On the TV: **Settings → System → About**, then click **Android TV OS build** seven times.
   You'll see "You are now a developer".
2. **Settings → System → Developer options → USB debugging → On**.
3. **Settings → Apps → Security & restrictions → Unknown sources** → allow the app you'll
   sideload from.

Then pick one of these:

### Easiest — Send Files to TV / Downloader

1. Install **Downloader by AFTVnews** (or **Send Files to TV**) on the TV from the Play Store.
2. Put `app-release.apk` somewhere you can reach — Google Drive with a direct link, or your
   phone with Send Files to TV installed on both.
3. Open it on the TV and confirm the install.

### ADB over Wi-Fi (from a computer)

Find the TV's IP under **Settings → Network & Internet**.

```bash
adb connect 192.168.1.50:5555        # your TV's IP
adb install -r app-release.apk
adb disconnect
```

Accept the debugging prompt that appears on the TV screen the first time.

After installing, ScreenScape appears in the **Apps** row on the Google TV home screen.

---

## Customising

| Change | Where |
|---|---|
| The website URL | `HOME_URL` in `app/src/main/java/me/screenscape/tv/MainActivity.java` |
| App name | `app_name` in `app/src/main/res/values/strings.xml` |
| Colours | `app/src/main/res/values/colors.xml` |
| Home-row banner | `app/src/main/res/drawable-*/banner.png` (320×180) |
| Pointer speed | `SPEED_MIN` / `SPEED_MAX` / `ACCEL` in `CursorView.java` |
| Version number | `versionCode` / `versionName` in `app/build.gradle` |

Bump `versionCode` before rebuilding if you want the TV to accept it as an update over an
already-installed copy.

---

## Troubleshooting

**The site loads but nothing responds to the remote.**
You're probably in focus mode. Press MENU to switch back to pointer mode.

**Video won't play / shows an error.**
Some streaming sites use Widevine DRM that a plain WebView can't decrypt. If that's the
case the site itself will need to support TV browsers — no wrapper can work around it.

**"App not installed" on the TV.**
Usually means an older copy with a different signature is present. Uninstall it first, or
bump `versionCode`.

**Layout is too small on screen.**
The app requests the desktop user agent. If the site's desktop layout is cramped on a TV,
switch `setUserAgentString` in `MainActivity.java` to a mobile UA — mobile layouts are often
larger and simpler for a 10-foot view.

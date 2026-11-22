# Local apps for a Google TV

Small sideloaded apps for an Android TV / Google TV. Built for a low-end set (TCL, Android 11,
32-bit ARM, 2 GB RAM) where a full browser with uBlock Origin is too heavy to use.

This is a personal project, shared in case it helps someone in the same situation. It is not
affiliated with HBO Max, Mozilla or uBlock Origin.

## streamshell

A single-purpose app that shows HBO Max's web player in the TV's built-in WebView and
injects [uBlock Origin Lite](https://github.com/uBlockOrigin/uBOL-home)'s page scripts before
each page loads, so ad breaks are removed the same way uBlock Origin removes them in a laptop
browser. DNS blockers such as Pi-hole cannot do this, because the ads come from the same
domains as the video.

- D-pad moves an on-screen pointer, OK clicks, pushing against the top or bottom edge scrolls,
  Back goes back, and the media keys drive the video.
- You sign in to HBO Max on the TV itself, in the app.
- Tested on System WebView 157. The ad rules did not run on WebView 134.

### Build and install

Needs JDK 17, the Android SDK, and ADB debugging switched on in the TV's developer options.

```sh
cd streamshell
./gradlew assembleDebug
adb connect <tv-ip>:5555
adb -s <tv-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

The app then appears as a StreamShell tile under "Your apps".

### Keeping the ad rules current

The rules are the unmodified scriptlet files from a uBlock Origin Lite release; the release
they came from is in `app/src/main/assets/rules/VERSION`. To fetch the latest release, then
rebuild and reinstall:

```sh
tools/update-rules.sh
```

### Checking what the page sees

`tools/page-eval.mjs <tv-ip>:5555 'navigator.userAgent'` evaluates an expression in the app's
current page over ADB (Node 22 or newer).

### Known gaps

- Navigation is not yet locked to HBO Max's domains.
- WebView remote inspection is switched on, which is what `page-eval.mjs` uses. It is
  reachable only over ADB.
- Rules are refreshed by hand, not on launch.
- HBO Max can change its player at any time and break this.

## firefox-shortcut

A home-screen tile that launches a sideloaded Firefox, which Google TV otherwise hides from
the home screen. Build with `firefox-shortcut/build.sh`. Firefox with uBlock Origin proved too
heavy for this TV, which is why streamshell exists.

## Signing keys

No signing keys are in this repo. `firefox-shortcut/build.sh` creates one on first run, and
streamshell uses Android's default debug key unless you put your own (password `android`, alias `androiddebugkey`) at
`streamshell/local.keystore`. An app signed with a different key will not install over the old
one: uninstall it from the TV first (for streamshell that also signs you out of HBO Max).

## Licence and terms

- This repo is licensed under the GNU General Public License v3.0; see [LICENSE](LICENSE).
- `streamshell/app/src/main/assets/rules/` is from uBlock Origin Lite, also GPLv3, with its
  own notices in each file.
- Blocking ads probably breaches HBO Max's terms on an ad-supported plan, the same as using
  uBlock Origin in a laptop browser. Use it at your own risk.

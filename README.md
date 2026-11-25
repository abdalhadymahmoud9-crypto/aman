# Aman

A personal, voluntary adult-content filter for my own devices:

- `android-source/` - Android app (local-VPN DNS filter, whole phone)
- `extension/` - Chrome MV3 extension (desktop Chrome)

Built for personal use. Shared as source in case it helps someone.

## What it does

- Checks website names against a built-in list of ~90,000 adult-content
  domains (source: StevenBlack/hosts, MIT license, see ATTRIBUTION.md)
  plus your own blocked/allowed lists. Allow always wins.
- The Android app answers DNS for blocked names with NXDOMAIN and forwards
  everything else to the public resolver 1.1.1.1. It uses Android's VPN
  slot only to see the phone's own DNS questions; there is no VPN server
  and no traffic leaves the phone except ordinary DNS.
- The Chrome extension blocks the same list with declarativeNetRequest.
  Everything stays in the browser; it makes no network calls of its own.
- No account, no logs, no analytics.

## What it does NOT do

- It is a voluntary aid, not a lock. You can pause or uninstall it any
  time, and it never tries to stop you.
- It cannot block 100% of adult content. No list is complete, and DNS
  filtering can be bypassed (for example DNS-over-HTTPS inside a browser,
  visiting a site by IP address, or another VPN).
- The Chrome extension covers Chrome only, not other browsers or apps.

## Status: experimental

Compile-checked and logic-tested only:

- Android: builds with Gradle 8.5 / AGP 8.1.4 / JDK 17 / compileSdk 34.
  19 DNS unit tests and 14 streak unit tests pass. NOT yet validated on
  a physical device.
- Chrome extension: 15 logic tests pass (mocked chrome APIs). NOT yet
  validated by loading it in a browser.

## Generated assets (not in this repo)

The blocklist and icons are generated locally by small scripts, so this
repo stays text-only:

```
sh tools/fetch-blocklist.sh        # downloads porn-hosts.txt (StevenBlack/hosts)
python3 tools/generate-blocklist.py
python3 tools/generate-icons.py
```

`generate-blocklist.py` creates `domains-*.txt` (inspection copies),
`android-source/app/src/main/assets/blocklist.txt.gz` and
`extension/rules/rules_1.json` .. `rules_4.json`.
`generate-icons.py` creates `extension/icons/*.png` and the Android
`mipmap-*/ic_launcher.png` files.

Without running these, the Android build and the extension are missing
required files. Re-fetching the list on a different day may produce a
slightly different domain set; that is expected (upstream changes).

## Build

Android APK (after generating assets):

```
cd android-source && gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk (debug-signed locally)
```

Chrome extension (after generating assets): open `chrome://extensions`,
enable Developer mode, "Load unpacked", choose `extension/`.

## Tests

```
node extension-logic-tests.js
cd dns-unit-tests    && javac com/aman/filter/*.java && java com.aman.filter.TestDns
cd streak-unit-tests && javac com/aman/filter/*.java && java com.aman.filter.TestStreak
```

## Use at your own risk

No warranty. Blocking lists sometimes contain mistakes; if a site you
need is blocked, add it to your allow list in the app or extension.

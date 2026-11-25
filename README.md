# Aman

A voluntary, personal content filter: an Android app (DNS-level, via the local VPN slot) and a Chrome extension. You turn it on, you can pause it, and you can uninstall it any time. It is not a virus and it does not hide itself.

**Status: experimental. Built with AI assistance.**
- The Android app compiled and its logic passed tests off-device. It has **not been run on a real phone**. Battery managers may stop the service.
- The Chrome extension passed logic tests with a mocked Chrome API. It has **not been loaded and run in a real browser** yet.
- DNS-level filtering has limits: DNS-over-HTTPS, direct IP addresses and cached lookups can bypass it. Keep "Block connections without VPN" OFF.
- No filter can promise to block everything.

## What is in this repo
- `android-source/` Android app source (Gradle 8.5, AGP 8.1.4, JDK 17, compileSdk 34). One Start/Pause button and a streak: calendar days the filter has been running continuously (start day = day 1, pause resets).
- `extension/` Chrome Manifest V3 extension code.
- `dns-unit-tests/`, `streak-unit-tests/`, `extension-logic-tests.js` tests.
- `ARCHIVE-NOTES.txt` design notes.

## Not in this repo (and why)
GitHub's web editor used to publish this cannot take large or binary files, so these are missing:
- The blocklist data: `extension/rules/rules_1..4.json`, `domains-*.txt`, and the Android asset `blocklist.txt.gz`. They are built from [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT, adult-content list, fetched Nov 24 2026). Regenerate them from that list before building. Without them the app and extension will not build or filter.
- The built APK and the icon PNGs. A debug APK exists privately for the owner; it is not published here.

## Licence
No licence has been chosen for this code yet, so by default all rights are reserved. See `NOTICE.md` for third-party material.

## Changes made when publishing (compared with the private build)
- The Chrome manifest's `icons` block was removed (the PNG icons are not published). The extension has no toolbar icon image until you add one.
- The Android launcher icon is now a simple vector drawable (`res/drawable/ic_launcher.xml`) instead of PNG mipmaps. The private APK uses the PNG icon.
- No script to regenerate the blocklist files is included yet, so the blocklist and rules cannot be rebuilt from this repo alone. A generator will be added only after it is tested.

## Build status
Nothing here has been built from this exact repository tree. The private APK was built from the full private tree. Treat this repo as a source project, not a verified clone-and-build.

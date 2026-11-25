Aman (Android app, personal build)
==================================

What it does:
- Filters website names on this phone against a built-in list of 90,304
  adult-content domains (source: StevenBlack/hosts, MIT license, updated
  Nov 24 2026) plus your own blocked/allowed lists. Allow always wins.
- Works for the whole phone (all browsers and apps that use normal DNS),
  not only one browser.
- Blocking is checked against the list stored on the phone. For sites
  that are NOT blocked, the phone asks the public resolver 1.1.1.1
  (Cloudflare) for the address, like ordinary DNS. No account, no logs.
- You can pause it inside the app or uninstall it anytime. It never
  blocks uninstalling.

Install (~3 minutes, needs ~1 MB download):
1. Copy aman-debug.apk to the phone (Telegram saved messages, USB, etc).
2. Open the APK file on the phone. Android will ask to allow installs
   from that source - allow it once, then install.
3. Open Aman. Tap "Start filtering". Android shows its standard VPN
   connection prompt - approve it. (The app uses Android's VPN slot only
   to catch DNS questions on the phone itself; there is no VPN server
   and no internet traffic passes through any other machine.)
4. Optional: Android Settings > Network > VPN > Aman > "Always-on VPN"
   keeps it running after reboots. IMPORTANT: do NOT turn on "Block
   connections without VPN" - Android blocks ALL traffic that does not go
   through the VPN when that switch is on (per the official Android VPN
   docs), and this filter only handles DNS, so that switch would cut the
   phone's internet. Always-on alone is safe.

What it cannot do (honest limits):
- Sites reached by IP address directly, or apps/browsers using their own
  encrypted DNS (DNS-over-HTTPS), or already-cached addresses, can bypass
  it. In Chrome on the phone, keep "Use secure DNS" on the automatic
  setting so Chrome follows the phone's filter.
- No list is complete. This is a strong filter, not a guarantee.
- It cannot survive being uninstalled, and it is not meant to.

The main screen is one Start/Pause button with a "days protected" streak beside it (the streak counts the days the filter VPN has actually been running; it resets when you pause). "Manage your sites" holds your custom lists.

Build note: debug-signed personal build (com.aman.filter, minSdk 26,
targetSdk 34). Source included in aman-source.zip.

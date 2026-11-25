Aman Web Filter (Chrome extension, personal build)
==================================================

What it does:
- Blocks a built-in list of 90,304 adult-content domains in Chrome
  (list source: StevenBlack/hosts, MIT license, updated Nov 24 2026).
- Lets you add your own blocked sites and allow sites back (Allow always wins).
- Pause/resume anytime from the toolbar button. Remove anytime from
  chrome://extensions. It never stops you from removing it.
- Everything runs and stays inside your browser. No account, no network
  calls from the extension, nothing is sent anywhere.

Install (Chrome on your PC, ~1 minute):
1. Unzip this folder somewhere you will keep it (Chrome needs the files
   to stay in place, e.g. Documents/aman-web-filter).
2. Open Chrome and go to: chrome://extensions
3. Turn on "Developer mode" (top-right switch).
4. Click "Load unpacked" and choose the unzipped folder.
5. The shield icon appears in the toolbar. Click it to pause/resume or
   open "Manage your lists" for your custom lists.

Notes:
- Chrome may occasionally show a "developer mode extensions" reminder.
  That is normal for any personal unpacked extension.
- First load enables a 28,000-domain baseline; the extension then tries
  to enable the remaining sets. The popup shows "sets active: x/4" -
  if your Chrome's rule budget is full, it keeps working with fewer sets
  instead of failing.
- This covers Chrome only. Other browsers and other apps on the PC are
  not affected.

License: built-in list from github.com/StevenBlack/hosts (MIT).

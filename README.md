# Memdroid

An Android app for managing RAM and storage on a Pixel phone (built for the Pixel 9 Pro XL, works on Android 12+).

## Features

- **RAM:** live system RAM usage. With Shizuku: running apps by memory, split into user and system apps, kill selected apps or kill all, and a whitelist of apps that are never killed.
- **Storage:** a colour-coded breakdown (apps, app data, app cache, images, videos, audio, other files, system), biggest apps and caches, every app's size with sorting, a folder explorer showing which folders use the most space, and (with Shizuku) clear all app caches.
- **Files:** find files over 50/100/250/500 MB and delete them. Every delete asks for confirmation first.
- **Battery:** level, charging, temperature and health. With Shizuku: battery used by each app since the last full charge, screen time, and force stop.
- **Tools:** usage history charts (RAM, storage, battery over 30 days), large files, duplicate finder (always keeps one copy), and unused apps (30/60/90 days) with uninstall.
- **Auto-kill rules:** stop apps you choose when the screen turns off or RAM runs low (needs Shizuku).
- **App growth:** which apps' storage grew the most over 7, 30 or 60 days.
- **Community:** feature ideas to vote on, bug and feature reports (pre-filled GitHub issues), what's new, and the heroes wall.
- **Smart cleanup:** one card on the Storage tab showing caches, old downloads, old screenshots, unused apps and the recycle bin, each with a review step.
- **Recycle bin (optional, on by default):** deleted files are kept for 30 days in a hidden folder and can be restored.
- **Crash reports:** after a crash, Memdroid offers to open a pre-filled GitHub issue with the error. Nothing is sent without a tap.
- **Alerts (optional, off by default):** a notification when free storage or available RAM drops below a level you choose, or when an app drains the battery in the background.
- **Widget:** RAM and storage on your home screen, with refresh and a Kill all button that opens the app to confirm.
- **Tablets and foldables:** a side navigation rail on wide screens, and two-pane RAM, storage and battery screens on tablets and unfolded phones.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download the `.apk`.
2. Open it and allow installing from your browser when Android asks.
3. New releases install over the old one; your whitelist and settings are kept.

## Setting up Shizuku (needed for killing apps and clearing caches)

Android doesn't let normal apps stop other apps. [Shizuku](https://shizuku.rikka.app/) gives Memdroid the same access as a USB debugging connection, without root.

1. Install **Shizuku** from the Play Store.
2. Turn on Developer options (Settings › About phone › tap Build number 7 times).
3. In Shizuku, choose **Start via Wireless debugging** and follow the pairing steps.
4. Open Memdroid and tap **Grant access**.

Shizuku stops when the phone restarts; open Shizuku and start it again.

## Permissions

| Permission | Why |
|---|---|
| Usage access | Per-app storage sizes and last-used dates |
| All files access | Finding large files anywhere on the phone |
| Query all packages | Listing installed apps |
| Request delete packages | Opening the uninstall dialog |

Memdroid has no internet permission; nothing leaves the phone.

## Building

Every build runs the unit tests in `app/src/test` (battery stats, process list and history parsers) before packaging.

Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release. To build locally: `./gradlew assembleRelease` (needs the Android SDK).

The signing key in `signing/` is checked in on purpose so every CI build can update the installed app. It's fine for a personal sideloaded app; don't reuse it for anything published.

## "Controlled by restricted setting"

Android blocks usage access for apps installed outside the Play Store. Try the toggle once, then open Memdroid's App info, tap ⋮ › **Allow restricted settings**, confirm, and go back to the toggle. Memdroid shows these steps under **Toggle greyed out?** on any permission card.

## Heroes wall

Supporters are listed in `app/src/main/assets/heroes.json` and appear in the app from the next release:

```json
[
  { "name": "Alex", "anonymous": false, "since": "Oct 2026" },
  { "name": "", "anonymous": true, "since": "Nov 2026" }
]
```

Anonymous heroes are shown as "Anonymous hero". Set `DONATE_URL` in `Community.kt` to show the Support button.

## Changelog

See [CHANGELOG.md](CHANGELOG.md). The same list is in the app under Tools › What's new (`Community.changelog`).

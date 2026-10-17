# Memdroid

An Android app for managing RAM and storage on a Pixel phone (built for the Pixel 9 Pro XL, works on Android 12+).

## Features

- **RAM:** live system RAM usage. With Shizuku: running apps by memory, split into user and system apps, kill selected apps or kill all, and a whitelist of apps that are never killed.
- **Storage:** a colour-coded breakdown (apps, app data, app cache, images, videos, audio, other files, system), biggest apps and caches, every app's size with sorting, a folder explorer showing which folders use the most space, and (with Shizuku) clear all app caches.
- **Files:** find files over 50/100/250/500 MB and delete them. Every delete asks for confirmation first.
- **Battery:** level, charging, temperature and health. With Shizuku: battery used by each app since the last full charge, screen time, and force stop.
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

Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release. To build locally: `./gradlew assembleRelease` (needs the Android SDK).

The signing key in `signing/` is checked in on purpose so every CI build can update the installed app. It's fine for a personal sideloaded app; don't reuse it for anything published.

## "Controlled by restricted setting"

Android blocks usage access for apps installed outside the Play Store. Try the toggle once, then open Memdroid's App info, tap ⋮ › **Allow restricted settings**, confirm, and go back to the toggle. Memdroid shows these steps under **Toggle greyed out?** on any permission card.

<p align="center">
  <img src="docs/assets/icon.png" width="96" height="96" alt="olcOS">
</p>

<h1 align="center">olcOS for Android</h1>

<p align="center">
  Source code of the olcOS app for Android 8.0+ (Kotlin, Jetpack Compose).<br>
  olcOS helps you get around mobile internet whitelists through video calls.
</p>

> [!IMPORTANT]
> **Just want to install the app?** This repository holds the source code. Download the ready-made file with the
> button below; step-by-step instructions are in the
> [main olcOS repository](https://github.com/olcOS/olcos/blob/main/README.en.md#android) and on the
> [website](https://olcos.github.io/en/#download).

<p align="center">
  <a href="https://github.com/olcOS/olcos/releases/download/v3.0.3-beta/olcOS-3.0.3-beta-android.apk"><img src="docs/assets/btn-android-en.png" width="159" height="56" alt="Download for Android"></a>&nbsp;
  <a href="https://github.com/olcOS/olcos/blob/main/README.en.md#iphone-and-ipad"><img src="docs/assets/btn-iphone-en.png" width="219" height="56" alt="Install on iPhone and iPad"></a>
</p>

<p align="center">
  <a href="https://olcos.github.io/en/">Website</a> ·
  <a href="https://github.com/olcOS/olcos/blob/main/README.en.md">User guide</a> ·
  <a href="https://github.com/olcOS/olcos/blob/main/README.en.md#troubleshooting">Troubleshooting</a> ·
  <a href="https://github.com/olcOS/olcos-ios">iOS</a> ·
  <a href="README.md">Русский</a>
</p>

## What it is

olcOS sends your phone's traffic through your own server. The data travels inside an ordinary video call (WebRTC) on
one of the calling services the app supports. The list lives in the app and grows with updates. When mobile internet
only works through a whitelist, an ordinary VPN can't reach its server, while a call to a whitelisted service gets
through. Without whitelists the phone can also connect to the server directly over Xray (VLESS + REALITY), so the
speed isn't limited by the call.

You need Android 8.0 or later on a 64-bit processor (arm64-v8a or x86_64). The APK is about 150 MB.

How whitelists work, how to install the app and how to set up a server is explained in the
[main repository](https://github.com/olcOS/olcos/blob/main/README.en.md).

## Features

- A VPN for the whole phone, or a SOCKS5 proxy on `127.0.0.1` for apps that support one.
- Connect with a link, a QR code, a subscription or an invite from another device. Paste the link on the Connections
  tab or open it from another app.
- Several rooms on one server are bonded into one channel. When a room drops, the app joins it again.
- Install your own server over SSH from inside the app: Connections → + → My own server (VPS).
- Server management: status, starting and stopping rooms, logs, new protocols, key rotation and invites for other
  devices.
- The app creates call rooms by itself. If a service needs an account, the app asks you to sign in once.
- Settings for those who know what they change: VP8 video channel frames and packets (30 frames of 64 packets by
  default), DNS, proxy port and login, connection checks and switching to a working connection. A connection link can
  be edited by hand: it holds every parameter, Xray settings included.
- Updates through [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.olcos.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FolcOS%2Folcos%22%2C%22author%22%3A%22olcOS%22%2C%22name%22%3A%22olcOS%22%7D),
  which watches the GitHub releases and offers each new version.
- The app is available in English and Russian.

## Building

You need JDK 17, the Android SDK with the NDK, and Go (the version is set in `olcos-core/mobile/go.mod`). The Go core
and the server side live in [olcOS/olcos-core](https://github.com/olcOS/olcos-core), included as the `olcos-core/`
submodule:

```bash
git clone --recurse-submodules https://github.com/olcOS/olcos-android.git
cd olcos-android
bash scripts/build-core.sh          # the core -> app/libs/olcoscore.aar
bash scripts/sync-core-assets.sh    # srv.sh, the core patch and the agent sources -> assets
gradle assembleDebug
```

How the app is put together, how CI builds the core and the APK, and which link formats are supported is described
in [docs/development.md](docs/development.md).

## Bugs and security

Report bugs and questions in the [main repository's issues](https://github.com/olcOS/olcos/issues/new/choose) and
mention that it's about Android, with the app and system versions. You can copy the log in the app: Settings →
Diagnostics → Copy the log. Remove keys, passwords and full links from it. Report vulnerabilities privately, as
described in [SECURITY.md](https://github.com/olcOS/olcos-ios/blob/main/SECURITY.md).

Use the app within the law of your country and the terms of the services your traffic goes through.

## License

olcOS is released under the [MIT](LICENSE) license. The tunnel is built on
[olcrtc](https://github.com/ghostlane-project/olcrtc) (Apache-2.0); the direct mode uses
[Xray-core](https://github.com/XTLS/Xray-core) (MPL-2.0).

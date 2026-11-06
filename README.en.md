<p align="center">
  <img src="docs/assets/icon.png" width="96" height="96" alt="olcOS">
</p>

<h1 align="center">olcOS for Android</h1>

<p align="center">
  A VPN and SOCKS5 proxy through your own server, carried inside a video call. Android 8.0+, Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/olcOS/olcos/releases/latest"><img src="https://img.shields.io/github/v/release/olcOS/olcos?include_prereleases&label=release&color=3A57D9" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8%2B-3A57D9" alt="Android 8+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-3A57D9" alt="MIT license"></a>
</p>

<p align="center">
  <a href="https://olcos.github.io/en/">Website</a> ·
  <a href="https://github.com/olcOS/olcos/releases/latest">Download</a> ·
  <a href="https://github.com/olcOS/olcos">olcOS</a> ·
  <a href="https://github.com/olcOS/olcos-ios">iOS</a> ·
  <a href="README.md">Русский</a>
</p>

olcOS sends your phone's traffic through a server you own. The data travels inside an ordinary video call (Yandex
Telemost, VK Calls, SaluteJazz, Jitsi, MTS Link) or straight to the server over Xray (VLESS + REALITY). There are no
shared servers and no accounts: the server is yours, and you manage it from your phone.

## Features

- A VPN for the whole phone, or a SOCKS5 proxy on `127.0.0.1` for apps that support one.
- Connect with a link, a QR code, a subscription or an invite from another device. Paste the link on the Connections
  tab or open it from another app.
- Several rooms on one server are bonded into one channel. When a room drops, the app joins it again.
- Install your own server over SSH from inside the app.
- Server management: status, starting and stopping rooms, logs, new protocols, key rotation and invites for other
  devices.
- Telemost, VK Calls and SaluteJazz rooms are created from the phone. MTS Link needs a permanent meeting from your own
  account.
- The app is available in English and Russian.

## Install

1. Download the `.apk` file from the [latest release](https://github.com/olcOS/olcos/releases/latest). Its SHA-256
   checksum is in the release notes.
2. Open the file on your phone. If Android asks, allow installs from this source.
3. Start olcOS, add a connection and tap Connect. In VPN mode the system asks for permission once.

To get new versions automatically, add olcOS to [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.olcos.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FolcOS%2Folcos%22%2C%22author%22%3A%22olcOS%22%2C%22name%22%3A%22olcOS%22%7D). It watches the GitHub releases and offers
the update when one comes out.

## Your own server

You need a Linux VPS (Debian, Ubuntu, Fedora or CentOS) with root access. In the app, open Connections → + → My own
server (VPS), enter the address and an SSH password or key, and olcOS installs everything. Or run one command on the
server:

```bash
curl -fsSL https://raw.githubusercontent.com/olcOS/olcos/main/install.sh | sudo bash
```

At the end it prints a QR code. Scan it in olcOS and that phone becomes the server's owner. Rooms, protocols and
invites are then handled in the app.

## Limitations

- The VPN carries TCP over IPv4. Apps that need UDP or IPv6 may not work through it.
- Speed depends on your network and on the call service. A service can close a room or restrict guests, so it helps
  to keep more than one protocol.
- This is a beta, and behaviour and compatibility still change.
- Use the app within the law of your country and the terms of the services your traffic goes through.

## Bugs and questions

Open an [issue](https://github.com/olcOS/olcos/issues) and mention that it's about Android, with the app and system
versions. Please don't post connection links, keys or your server addresses. Report vulnerabilities privately, as
described in [SECURITY.md](https://github.com/olcOS/olcos-ios/blob/main/SECURITY.md).

## Building

You need JDK 17, the Android SDK with the NDK, and Go (the version is set in the core's `gomobile/go.mod`). The Go core
lives in [olcos-ios](https://github.com/olcOS/olcos-ios) and is bound into `app/libs/olcoscore.aar` by
`scripts/build-core.sh`. How the app is put together, how CI builds the core and the APK, and which link formats are
supported is described in [docs/development.md](docs/development.md).

## License

olcOS is released under the [MIT](LICENSE) license. The tunnel is built on
[olcrtc](https://github.com/ghostlane-project/olcrtc) (Apache-2.0); the direct mode uses
[Xray-core](https://github.com/XTLS/Xray-core) (MPL-2.0).

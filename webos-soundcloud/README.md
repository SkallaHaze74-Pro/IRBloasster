# SoundCloud TV Pro for LG webOS

Private remote-first launcher for LG webOS TV. Version 1.2.0 uses the built-in LG browser for SoundCloud playback and account login, avoiding the black login screen seen inside packaged `file://` web apps.

## Features

- persistent LG browser session instead of storing credentials in the IPK
- large 4×2 TV dashboard for Weiterhören, Bibliothek, Likes, Playlists, Stream, Entdecken, Suche and Anmeldung
- remembers the last opened SoundCloud destination locally
- Magic Remote, arrow keys, OK and numeric shortcuts 1–8
- 1920×1080 OLED-friendly interface
- no SoundCloud password, cookie or access token inside the package
- no additional SmartIR tracking or advertising

## Install from Termux

```bash
cd ~/IRBloasster
git fetch origin +refs/heads/feature/soundcloud-webos-1.0:refs/remotes/origin/feature/soundcloud-webos-1.0
git switch -C feature/soundcloud-webos-1.0 --track origin/feature/soundcloud-webos-1.0
git reset --hard origin/feature/soundcloud-webos-1.0
chmod +x webos-soundcloud/package-termux.sh
chmod +x webos-soundcloud/install-termux.sh
./webos-soundcloud/install-termux.sh smartirtv
```

## First login

1. Launch SoundCloud TV Pro.
2. Choose `Anmelden` once.
3. Complete the login in the LG browser.
4. Leave SoundCloud signed in and close the browser normally.
5. Future launches reuse the LG browser session and can open any dashboard destination directly.

The session can end after explicit logout, clearing LG browser data, reinstall/reset, or SoundCloud-side expiry.

This is a private sideloaded launcher and not an official SoundCloud or LG Content Store app. It does not alter SoundCloud advertisements, subscriptions or access controls.

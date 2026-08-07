# SoundCloud TV Pro for LG webOS

Private remote-first launcher for LG webOS TV. Version 1.4.0 replaces the failed media-player handoff experiment with a Bluetooth Sound Share test using the TV's built-in system apps.

## Features

- persistent LG browser session instead of storing credentials in the IPK
- large TV dashboard for Weiterhören, Bibliothek, Likes, Playlists, Stream, Entdecken, Suche and Anmeldung
- remembers the last opened SoundCloud destination locally
- Magic Remote, arrow keys, OK and numeric shortcuts 1–9
- 1920×1080 OLED-friendly interface
- no SoundCloud password, cookie or access token inside the package
- no additional SmartIR tracking or advertising
- smaller package because the 45-second WAV experiment was removed

## Bluetooth Sound Share test

The previous result on the LG OLED55B19LA was clear:

- the bundled test audio played inside SoundCloud TV Pro
- `com.webos.app.mediadiscovery` did not open a visible player
- audio stopped as soon as the TV switched away from the web app

Version 1.4.0 therefore uses the TV's installed system apps instead:

- `com.webos.app.btspeakerapp` for Bluetooth Sound Share
- `com.webos.app.homeconnect` as the Home Dashboard fallback

Test procedure:

1. Open tile `9 · Bluetooth Sound Share`.
2. Try `Sound Share direkt öffnen`.
3. On the Xiaomi phone, enable Bluetooth and select the LG TV.
4. Start SoundCloud on the phone.
5. Switch the TV to HDMI or Live TV.
6. The experiment succeeds only if the phone's SoundCloud audio continues through the TV speakers.
7. If direct launch fails, open Home Dashboard and select Sound Share in the Mobile section.

No service-menu values, NVRAM, panel settings, firmware or root state are changed.

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

# SoundCloud TV Pro for LG webOS

Private remote-first launcher for LG webOS TV. Version 1.3.0 adds a non-destructive experiment that hands a known-good audio file to the built-in LG media player to test whether playback continues after switching to HDMI or Live TV.

## Features

- persistent LG browser session instead of storing credentials in the IPK
- large TV dashboard for Weiterhören, Bibliothek, Likes, Playlists, Stream, Entdecken, Suche and Anmeldung
- remembers the last opened SoundCloud destination locally
- Magic Remote, arrow keys, OK and numeric shortcuts 1–9
- 1920×1080 OLED-friendly interface
- no SoundCloud password, cookie or access token inside the package
- no additional SmartIR tracking or advertising

## LG background test

The `LG Hintergrundtest` tile contains three safe tests:

1. Play the bundled 45-second PCM WAV inside SoundCloud TV Pro.
2. Hand the same installed WAV to `com.webos.app.mediadiscovery` with `mediaType: MUSIC`.
3. Try an HTTPS Ogg/Vorbis source if the local package path is rejected.

After the LG player starts, press the Input button and switch to HDMI or Live TV. The experiment succeeds only if the music continues after that source switch. The app does not change service-menu values, NVRAM, panel settings or firmware.

The system-player launch payload is an undocumented compatibility path on consumer webOS. A successful launch callback only means that the launch request was accepted; the real result is whether audio continues after the source change.

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

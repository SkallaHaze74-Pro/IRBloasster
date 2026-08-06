# Twitch TV Pro for LG webOS

Private LG webOS launcher plus a hosted official Twitch player optimized for TV remote control and maximum available stream quality.

## Features

- official Twitch embedded player only
- automatically requests `chunked` / Source quality when the broadcaster offers it
- manual quality cycling with Left/Right
- OK toggles Play/Pause
- remembers the last channel in browser local storage
- LG browser shortcuts for Twitch Login, Gefolgt and Home
- 1920×1080 launcher graphics; the LG video pipeline can display UHD streams when Twitch provides them
- no Twitch password, cookie, access token or client secret stored in the IPK
- advertisements, subscriptions and Twitch access controls remain unchanged

## Install from Termux

```bash
cd ~/IRBloasster
git fetch origin +refs/heads/feature/twitch-tv-pro-1.0:refs/remotes/origin/feature/twitch-tv-pro-1.0
git switch -C feature/twitch-tv-pro-1.0 --track origin/feature/twitch-tv-pro-1.0
git reset --hard origin/feature/twitch-tv-pro-1.0
chmod +x webos-twitch-pro/package-termux.sh
chmod +x webos-twitch-pro/install-termux.sh
./webos-twitch-pro/install-termux.sh smartirtv
```

## First use

1. Open `Gefolgt & Login` and sign in once in the LG browser.
2. Return to Twitch TV Pro.
3. Open `Source Player`.
4. Enter a Twitch channel name and start it.
5. The player selects the highest available quality. Left/Right cycles through every quality reported by Twitch.

## Hosting

The Source Player is deployed through GitHub Pages from `web/twitch-tv-pro`. Twitch requires embedded players to run on HTTPS and to declare their parent domain, so the player uses:

```text
https://skallahaze74-pro.github.io/IRBloasster/
```

This is a private sideloaded launcher and is not an official Twitch or LG Content Store app.

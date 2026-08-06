# SoundCloud TV for LG webOS

Private launcher for LG webOS TV. Version 1.1.0 opens SoundCloud in the built-in LG browser instead of keeping the login flow inside the packaged app.

## Why the LG browser is used

- SoundCloud login can open additional authentication pages or popups.
- The packaged web app previously showed a black screen during login on webOS 6.x.
- The built-in LG browser keeps the SoundCloud browser session and cookies, so the user normally remains signed in across launches.
- No SoundCloud password, cookie or token is stored in the IPK package.

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

1. Launch SoundCloud TV.
2. Choose `Einmal anmelden / Konto wechseln` if the library does not open as signed in.
3. Complete the login in the LG browser.
4. Leave SoundCloud signed in and close the browser normally.
5. Future launches open `Meine Bibliothek` in the same browser session.

The session can end if the user signs out, clears LG browser data, reinstalls or resets the TV, or SoundCloud expires the session.

This is a private sideloaded launcher and not an official SoundCloud or LG Content Store app.

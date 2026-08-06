# SoundCloud TV for LG webOS

Private hosted web app for LG webOS TV. The app shows a short local launcher screen and then opens the official SoundCloud web player.

## Install from Termux

```bash
cd ~/IRBloasster
git fetch origin
git switch feature/soundcloud-webos-1.0
git reset --hard origin/feature/soundcloud-webos-1.0
chmod +x webos-soundcloud/package-termux.sh
chmod +x webos-soundcloud/install-termux.sh
./webos-soundcloud/install-termux.sh smartirtv
```

## Notes

- Uses the official SoundCloud website; no SoundCloud credentials are stored in the package.
- First login is completed directly on the TV.
- Magic Remote pointer or a keyboard is recommended for login and search.
- This is a private sideloaded app and not an official SoundCloud or LG Content Store app.

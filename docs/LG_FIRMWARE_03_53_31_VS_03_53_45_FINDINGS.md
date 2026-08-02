# LG OLED55B19LA – Firmware 03.53.31 vs. 03.53.45

## Scope

Offline/read-only analysis of the official firmware packages for the photographed `OLED55B19LA.DEUQJP` (`lm21u-koli`). No firmware was flashed or modified. This document contains no serial numbers, MAC/IP addresses, ESN/VSN/Widevine identifiers, firmware binaries or cryptographic keys.

## Integrity

| Version | ZIP SHA-256 | EPK SHA-256 | EPK size |
|---|---|---|---:|
| 03.53.31 | `f6b04d340f6727e8728a16cdfd3d8db096d59ed18a9bda2d0623a3052eb2e942` | `010b7f5a168c13c2cc656d031b6f358af7f668b3bf4ccf70796872f7f5805be3` | 1,189,910,252 |
| 03.53.45 | `41dbd700479bea137fb0b3aa4bc9f18923ea9f9a9f1275025b259dc89a32f20f` | `ed3dcd87351dea265ef867db287905ebfceca3229ef9a62ab8da147f4f3e7399` | 1,189,910,252 |

Both are signed/encrypted EPK v3 images with OTA ID `HE_DTV_W21U_AFADATAA` and USB update type. The headers report platform/SDK 6.5.2 and firmware 03.53.31.01 versus platform/SDK 6.5.3 and firmware 03.53.45.01.

## Package comparison

Both images contain 201 segment records and the same 15 package names.

Byte-identical packages:

- `partinfo`
- `tzfw`
- `swue`
- `boot`
- `sedata`
- `logo`
- `secureboot`
- `intmicom`

Changed packages:

- `rootfs`
- `kernel`
- `bsppart`
- `fonts`
- `license`
- `otncabi-global`
- `otycabi-global`

Therefore 03.53.45 is a targeted OS/kernel/BSP/app/resource maintenance update, not a replacement of the boot chain, Secure Boot, TrustZone firmware, partition map or software updater.

## Kernel and filesystem

Both versions still use `Linux-4.4.84-899.19.koli.1`, but the kernel images have different build dates and contents.

Rootfs comparison:

- 102,488 entries in 03.53.31
- 102,501 entries in 03.53.45
- 13 added
- 0 removed
- 2,039 content/mode/target changes
- 68,527 metadata-only rebuild changes
- 31,922 byte-identical entries

Many web-app bundles were merely rebuilt; bundle/cache/minifier differences alone are not treated as new functionality.

## Confirmed new component in 03.53.45

03.53.45 adds the hidden trusted app:

```text
com.webos.app.videoads
Title: Container App for video ads App
visible: false
defaultWindowType: screenSaver
requiredPermissions: all
```

It can load regional remote web content from `*.videoads.lgtvcommon.com`.

Exactly three related `lgchannels` settings were added:

```text
isVideoScreenSaverEnabled = false
videoScreenSaverAppList = ["com.webos.app.home"]
videoScreenSaverDisabledByError = false
```

The firmware default is disabled. Runtime activation may still depend on region, server configuration or consent.

## Root/homebrew evidence

`usr/sbin/faultmanager` is byte-identical in both releases:

```text
SHA-256: c72c082f67f7f9a0637429c84a2d84636264c870e91e675f7107a9d61631b30a
Build ID: 3f181319b8d93ab79934c3b98a13180c35d02d19
```

Related manifests and crash scripts are also identical. This proves that 03.53.45 did not patch the `faultmanager` binary itself relative to 03.53.31. Kernel and WebAppMgr/WAM did change, so this comparison alone does not prove that a public exploit remains usable.

RootMyTV is documented as patched on 2021 models from webOS TV 6.3.0 onward; this TV runs webOS TV 6.5.3-47. No software-root path is marked verified by this firmware comparison.

## Hidden official apps worth safe testing

- `com.webos.app.self-diagnosis` – Quick Help / OLED, HDMI, signal, Wi-Fi, Magic Remote, audio/video and sensor diagnostics
- `com.webos.app.gameoptimizer` – Game Optimizer overlay
- `com.webos.app.miracast` – Screen Share
- `com.webos.app.btspeakerapp` – Bluetooth Audio Playback
- `com.webos.app.btsurroundautotuning` – Bluetooth Surround Auto Tuning
- `com.webos.app.onetouchsoundtuning` – One Touch Sound Tuning
- `com.webos.app.channeledit` – Channel Manager
- `com.webos.app.channelsetting` – Channel Tuning
- `com.webos.app.scheduler` – TV Scheduler
- `com.webos.app.recordings` – Recordings
- `com.webos.app.notificationcenter` – Notifications
- `com.webos.app.connectionwizard` – Universal Control Settings
- `com.webos.app.roomconnect` – Room to Room Share
- `com.webos.app.appcasting` – App Casting
- `com.webos.app.camera` – Camera
- `com.webos.app.homeconnect` – Home Dashboard
- `com.webos.app.igallery` – Art Gallery

Factory, installation, remote-service, ACR/ad and store-demo components are intentionally excluded from user shortcuts.

## Read-only capability scanner

The firmware's Second Screen API adapter exposes:

```text
ssap://config/getConfigs
```

Its implementation has `useKeyValidation: false`, allowing an authorised client to request arbitrary config names and forward them to `com.webos.service.config/getConfigs`.

High-value read-only keys include:

- model, SoC, DRAM/eMMC, display type, resolution and output frame rate
- OLED cell/module type
- HDR, Dolby Vision, VRR, ISF, WiSA and OLED orbit support
- headphone, line-out and optical-audio support
- satellite/T2/triple-tuner flags
- Bluetooth and voice-recognition capabilities
- firmware, platform and bootloader versions
- DVR, signal-test, used-time and remote-service capabilities

This is the preferred basis for a SmartIR TV Lab because it reports the features actually active on the specific B1 instead of assuming every schema entry applies.

## Read-only settings exposed to Second Screen

The standard adapter whitelist permits reading selected keys from:

- `network`: device name, Wake-on-LAN/Wi-Fi, BLE advertising
- `picture`: brightness, backlight/OLED level, contrast, colour, energy saving
- `sound`: AV sync, eARC, sound output, digital output, sound mode
- `other`: Simplink/CEC, universal-remote enablement
- `option`: audio guidance, country/ZIP, Live Plus and broadcast/location values
- `general`: Always On, TV-on screen, installation method and SCA3-related values

The video-ad and broader recommendation defaults are present in the firmware but are not part of this standard read whitelist.

## Pro-feature schemas present

The platform contains schema/code families for:

- Game Optimizer and per-HDMI game settings
- Standard/FPS/RPG/RTS genres and Quick Game
- AI Game Sound
- per-HDMI OLED FreeSync and UHD Deep Color
- eARC
- Filmmaker Mode and ISF day/night
- OLED Motion Pro levels
- OLED Care modes
- Bluetooth surround and AV-sync controls

A schema entry proves platform code exists; a read-only config scan is still required to confirm support on this exact model/input.

## External PQ interface

The Second Screen API includes:

```text
ssap://externalpq/getExternalPqData
ssap://externalpq/setExternalPqData
```

This is a real picture-quality/calibration interface. Only read/backup research is considered safe initially. Writing can damage calibration/LUT/PQ data and must not become an unrestricted SmartIR button.

## Flash/downgrade conclusion

- `swue` is byte-identical in both versions.
- The updater contains version comparison and older-USB rejection text.
- EPK v3 packages are encrypted and RSA-signed.
- A modified image cannot be repacked as a trusted normal USB update without LG signing material.
- A blind USB downgrade from installed 03.53.45 to 03.53.31 is not justified.

## Safe implementation plan

1. Add a read-only TV Lab to SmartIR.
2. Scan config capabilities and permitted settings.
3. Add safe shortcuts to hidden official apps, beginning with Quick Help and Game Optimizer.
4. Export an anonymised capability report.
5. Keep all write APIs disabled until there is a per-setting backup and rollback path.
6. Never expose Service Reset, panel/model options, White Balance/LUT/NVRAM, EDID/tool options, OLED protection flags, DRM material or External-PQ writes as normal controls.

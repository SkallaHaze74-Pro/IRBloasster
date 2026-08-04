# LG OLED55B19LA / webOS 6.5.3 / Firmware 03.53.45 – Root research

## Scope and safety

Authorized offline and read-only analysis of the owner's LG OLED55B19LA firmware and runtime configuration. The goal is to identify a new privilege-escalation candidate without blindly modifying panel data, calibration, NVRAM, EDID, DRM material, secure boot, TrustZone, boot partitions or updater state.

No root path is marked verified until it is reproducible with a harmless canary that only touches the Developer Mode area or a disposable temporary directory.

## Firmware integrity and extraction

The complete split archive was reconstructed and verified:

- ZIP SHA-256: `41dbd700479bea137fb0b3aa4bc9f18923ea9f9a9f1275025b259dc89a32f20f`
- EPK SHA-256: `ed3dcd87351dea265ef867db287905ebfceca3229ef9a62ab8da147f4f3e7399`
- OTA ID: `HE_DTV_W21U_AFADATAA`
- platform / SDK: `6.5.3`
- firmware: `03.53.45.01`
- EPK3 encryption: `prodkey`
- update type: `USB`
- package records: `201`

The EPK was decrypted with the known LM21U key used by the public `epk2extract` research tooling. The package map contains `rootfs`, `kernel`, `bsppart`, `swue`, `partinfo`, `boot`, `secureboot`, `tzfw` and other signed packages.

The root filesystem is SquashFS 4.0 with LZO compression and contains 102,501 inodes.

## Known public roots

The model/firmware checker reports all currently public software-root families as patched on 03.53.45:

- RootMy.TV
- WTA
- ASM
- crashd
- DejaVuln
- faultmanager

The `faultmanager` executable is byte-identical between 03.53.31 and 03.53.45, but this does not restore the public exploit: kernel, WAM/WebAppMgr and surrounding policy changed, and the official checker marks the chain patched from 03.52.60.

## Candidate A – root service environment files

A large number of root-run systemd services load optional files from:

```text
/var/systemd/system/env/<service>.env
```

Examples include:

- `appinstalld.env`
- `faultmanager.env`
- `webapp-mgr.env`
- `factorymanager.env`
- `crashd.env`
- `ls-hubd.env`
- `settings-service.env`

`appinstalld.service` also constructs an `LD_PRELOAD` value. If the Developer Mode user can create or replace any relevant environment file and cause the service to restart, the impact would be root code execution.

**Current status:** high impact, reachability unknown. Runtime ownership, mode, ACL and mount properties must be checked before any write test.

## Candidate B – core-boot-done external script

The firmware contains:

```ini
[Service]
Type=oneshot
ExecStart=/bin/sh /var/luna/preferences/exscript.core-boot-done
ExecStartPost=/bin/rm -f /var/luna/preferences/exscript.core-boot-done
RemainAfterExit=yes
```

The companion path unit watches `/tmp/core-boot-done`.

If the unit is active and the Developer Mode identity can create `/var/luna/preferences/exscript.core-boot-done`, it is a direct root shell-script execution primitive.

**Current status:** very high impact, activation and writability unknown. Static firmware contains no second reference that explains who is intended to create the script. Runtime state must be checked without creating the file.

## Candidate C – appinstalld control archive extraction

`appinstalld` runs as root and Developer CLI is allowed to call:

```text
com.webos.appInstallService/dev/install
com.webos.appInstallService/dev/remove
```

Static reverse engineering shows the package parser performs a control-archive stage using external tools:

```text
ar x <ipk> control.tar.gz
tar xzf <control.tar.gz>
```

The TV ships GNU tar 1.17. The later opkg installation stage includes `--no-install-insecure-symlink` and `--no-install-insecure-path`, so the obvious malicious `data.tar` traversal is blocked. The earlier `control.tar.gz` extraction is separate and remains interesting if its working directory is predictable, reused, attacker-writable or raceable.

**Current status:** medium/high potential, not yet an exploit. We need the live temporary-directory path, permissions and cleanup behavior during a normal Developer Mode installation.

## Candidate D – Developer Mode writable directories

The signed Developer Mode startup script performs recursive mode changes on:

```text
/tmp/developer
/media/developer
```

The startup script itself is protected by RSA/SHA-512 verification, and LG included explicit patches to verify it and remove the old SSH private key. Replacing the script is therefore not a current route.

The writable directory tree may still expose symlink/race interactions with privileged services. This requires runtime mount, ownership and symlink-policy inspection.

## SUID inventory

Notable setuid-root binaries in rootfs include:

```text
/bin/busybox.suid
/bin/mount.util-linux
/bin/ping.iputils
/bin/su.shadow
/bin/umount.util-linux
/sbin/mount.ecryptfs_private
/usr/bin/fusermount
/usr/bin/newgrp.shadow
/usr/libexec/dbus-daemon-launch-helper
```

The presence of a SUID binary is not itself a vulnerability. Version, build options, allowed applets and invocation context must be evaluated individually.

## Next gate: read-only runtime probe

Run `tools/lg-root-research/read-only-runtime-probe.sh`. It only executes read/stat/list/status commands through `ares-novacom` and stores the output locally. It does not create files on the TV, restart services or alter settings.

The first decision gate is:

1. Is `/var/systemd/system/env` writable by the Developer Mode identity?
2. Is `/var/luna/preferences` writable, and is `run-exscript-cbd.path` active?
3. Which temporary directory does appinstalld use, and who owns it?
4. Are Developer Mode paths separate mounts, bind mounts or symlinkable locations?

Only after those answers will a harmless canary test be designed. A canary must write exclusively inside `/media/developer` or a disposable `/tmp` directory and must never target a real system file.

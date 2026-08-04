#!/data/data/com.termux/files/usr/bin/bash
set -eu

TARGET="${1:-smartirtv}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT/research-output"
STAMP="$(date '+%Y-%m-%d_%H-%M-%S')"
OUT="$OUT_DIR/LG-root-runtime-readonly-$STAMP.txt"

mkdir -p "$OUT_DIR"

ARES_NOVACOM="$(command -v ares-novacom || true)"
if [ -z "$ARES_NOVACOM" ]; then
  echo "FEHLER: ares-novacom wurde nicht gefunden."
  exit 1
fi

REMOTE_COMMAND='echo SMARTIR_LG_ROOT_READONLY_V1
printf "=== TIME ===\n"
date 2>&1
printf "=== IDENTITY ===\n"
id 2>&1
umask 2>&1
printf "=== SYSTEM ===\n"
uname -a 2>&1
cat /proc/version 2>&1
printf "=== TARGET PATHS ===\n"
for p in \
  /var/systemd \
  /var/systemd/system \
  /var/systemd/system/env \
  /var/luna \
  /var/luna/preferences \
  /var/luna/preferences/exscript.core-boot-done \
  /tmp/core-boot-done \
  /tmp/appinstalld \
  /tmp/developer \
  /media/developer \
  /media/developer/apps \
  /media/cryptofs/tmp \
  /media/cryptofs/apps/usr/palm/services/com.palmdts.devmode.service/start-devmode.sh
do
  echo "--- $p"
  ls -ldn "$p" 2>&1
  stat -c "%A %a %u:%g %U:%G %n" "$p" 2>&1
  if [ -e "$p" ]; then
    if [ -r "$p" ]; then echo "READABLE=yes"; else echo "READABLE=no"; fi
    if [ -w "$p" ]; then echo "WRITABLE=yes"; else echo "WRITABLE=no"; fi
    if [ -x "$p" ]; then echo "EXEC_OR_SEARCH=yes"; else echo "EXEC_OR_SEARCH=no"; fi
  fi
done
printf "=== ENV DIRECTORY ===\n"
ls -lan /var/systemd/system/env 2>&1 | head -120
printf "=== PREFERENCES EXSCRIPTS ===\n"
ls -lan /var/luna/preferences/exscript* 2>&1 | head -80
printf "=== INSTALL TEMP CANDIDATES ===\n"
ls -lan /tmp/appinstalld /media/cryptofs/tmp /tmp 2>&1 | head -180
printf "=== DEVELOPER TREE ===\n"
ls -lan /tmp/developer /media/developer /media/developer/apps 2>&1 | head -180
printf "=== MOUNTS ===\n"
mount 2>&1 | grep -E "(/var|/tmp|/media/developer|/media/cryptofs|overlay|cryptofs)" | head -160
printf "=== SYSTEMD STATE ===\n"
systemctl is-active run-exscript-cbd.path run-exscript-cbd.service appinstalld.service faultmanager.service 2>&1
systemctl status run-exscript-cbd.path run-exscript-cbd.service appinstalld.service faultmanager.service --no-pager 2>&1 | head -240
printf "=== PROCESS IDENTITIES ===\n"
ps 2>&1 | grep -E "appinstalld|faultmanager|WebAppMgr|webapp-mgr|ls-hubd" | head -100
printf "=== SERVICE UNITS ===\n"
sed -n "1,180p" /lib/systemd/system/run-exscript-cbd.path 2>&1
sed -n "1,180p" /lib/systemd/system/run-exscript-cbd.service 2>&1
sed -n "1,180p" /etc/systemd/system/appinstalld.service 2>&1
printf "=== TOOL VERSIONS ===\n"
/bin/tar --version 2>&1 | head -4
/bin/busybox.suid 2>&1 | head -4
printf "=== END ===\n"'

{
  echo "SmartIR LG root research – read-only runtime probe"
  echo "Target: $TARGET"
  echo "Generated: $(date '+%Y-%m-%d %H:%M:%S')"
  echo
  "$ARES_NOVACOM" -d "$TARGET" -r "$REMOTE_COMMAND"
} | tee "$OUT"

echo
echo "Gespeichert: $OUT"

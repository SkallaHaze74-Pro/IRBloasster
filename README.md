# IRBloasster / SmartIR

Lauffähige Minimal‑App (Jetpack Compose) zum Steuern deines LG OLED55B19LA
und Sony STR‑DB870 per integrierten IR‑Blaster (Xiaomi 15T Pro).

* **LG TV** – NEC‑Protokoll 32 bit  
* **Sony Receiver** – SIRC 15 bit  
* Großes Dark‑Theme‑UI, Demo‑Buttons

## Quick Start (Termux)

```bash
pkg install openjdk-17 git gradle
git clone https://github.com/SkallaHaze74-Pro/IRBloasster.git
cd IRBloasster
git checkout smartir
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

# Architektur

```text
app/src/main/java/com/skallahaze/irbloasster/
├── LivingRoomViewModel.kt
├── data/
│   ├── DiagnosticsLog.kt
│   ├── LocalTls.kt
│   ├── SecurePreferences.kt
│   ├── WakeOnLan.kt
│   ├── WebOsClient.kt
│   ├── WebOsDiscovery.kt
│   ├── WebOsPointerClient.kt
│   └── WebOsProtocol.kt
├── ir/
│   ├── ConsumerIrTransmitter.kt
│   ├── DeviceProfiles.kt
│   ├── IrSignal.kt
│   ├── NecEncoder.kt
│   └── SonySircEncoder.kt
├── macro/
│   └── MacroEngine.kt
├── model/
│   └── Models.kt
└── ui/
    ├── Components.kt
    ├── DiagnosticsTab.kt
    ├── HomeTab.kt
    ├── LivingRoomApp.kt
    ├── SonyRemoteTab.kt
    ├── TouchpadTab.kt
    ├── TvRemoteTab.kt
    └── theme/Theme.kt
```

## Datenfluss

1. `LivingRoomViewModel` verbindet UI, Netzwerk, IR, Speicherung und Szenen.
2. `WebOsDiscovery` sucht TVs lokal per SSDP und liest die UPnP-Gerätebeschreibung.
3. `WebOsClient` verwaltet Pairing, Anfragen, Abonnements und Reconnect-fähige Zustände.
4. `WebOsPointerClient` nutzt den vom TV gelieferten zweiten WebSocket.
5. `ConsumerIrTransmitter` serialisiert IR-Sendungen mit einem Mutex.
6. `MacroEngine` führt benannte Schritte nacheinander aus und stellt Fortschritt bereit.
7. `DiagnosticsLog` sammelt lokale, geschwärzte Diagnoseeinträge.

## Sicherheitsmodell

- Der webOS-`client-key` wird über einen Android-Keystore-Schlüssel mit AES-GCM verschlüsselt.
- Lokales `wss://` wird benötigt, weil LG-TVs selbstsignierte Zertifikate verwenden können.
- Beim ersten erfolgreichen WSS-Kontakt wird der SHA-256-Fingerprint gespeichert.
- Ändert sich der Fingerprint später, beendet die App die Verbindung vor dem Pairing.
- Diagnosetexte schwärzen `client-key`-Werte.
- Es werden keine ThinQ-Cloud-Zugangsdaten benötigt.

## SSAP-Anfragen

Format:

```json
{
  "id": "req-42",
  "type": "request",
  "uri": "ssap://audio/volumeUp"
}
```

Abonnements verwenden `"type": "subscribe"`. Pairing verwendet `hello` und anschließend `register` mit einem Manifest aus angeforderten Berechtigungen.

## IR

- LG: NEC, 38 kHz, Byte-Reihenfolge wie geschrieben, Bits je Byte LSB-first.
- Sony: SIRC, 40 kHz, 12/15/20 Bit, LSB-first.
- Wiederholungen werden im Sender ausgeführt, nicht durch unendlich lange Timing-Arrays.

# Security

## Local-only control

The application talks directly to the TV on the local network and does not require a ThinQ account.

## Stored information

- TV IP and MAC address: ordinary private app preferences
- webOS client key: AES-GCM encrypted with a non-exportable Android Keystore key
- TV WSS certificate fingerprint: ordinary private app preferences
- diagnostic log: in-memory only and automatically redacts client keys

## WSS trust model

LG TVs may expose a self-signed local certificate. The app therefore uses trust-on-first-use:

1. Accept the local certificate for the first TLS handshake.
2. Store its SHA-256 fingerprint.
3. Compare all later connections with that fingerprint.
4. Stop if the fingerprint changes.

Resetting app data or using **Client-Key löschen und neu koppeln** removes this local trust state.

## Reporting

Do not post TV client keys, private network captures, APK signing material or ThinQ credentials in public issues.

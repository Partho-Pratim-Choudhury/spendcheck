# SpendCheck — mobile app

Expo (SDK 58) + TypeScript + expo-router. Android-first.

## Setup

```bash
cp .env.example .env   # set EXPO_PUBLIC_API_URL=http://<laptop-LAN-or-Tailscale-IP>:8077
npm install
npx expo start         # scan the QR with Expo Go (same Wi-Fi as the laptop)
npx expo start --tunnel   # fallback if the phone can't reach the laptop's LAN IP
```

After changing `.env`, restart with `npx expo start -c` (EXPO_PUBLIC_* values are inlined at bundle time).

## Notes

- The dev backend is plain HTTP on the LAN, so `expo-build-properties` sets
  `android.usesCleartextTraffic: true` (applies to dev/standalone builds; Expo Go already allows it).
  Remove it once the backend is served over HTTPS.
- `--tunnel` only tunnels the Metro bundler, not the API: the phone must still reach
  `EXPO_PUBLIC_API_URL` directly (same Wi-Fi, or Tailscale on both devices).
- Auth token is stored in expo-secure-store only.

## Checks

```bash
npx tsc --noEmit
npx expo lint
npx expo-doctor
```

# Superscan

An Android app for a small uptime monitor: it shows the monitor's checks and
incident history, and turns its alerts into notifications through
[UnifiedPush](https://unifiedpush.org), so it needs no Google services and
keeps no connection of its own open (your distributor, such as Sunup or ntfy,
does that).

Named after the supercomputer's scan in *Code Lyoko*, the one that finds the
activated tower.

## What it does

- **Pairing:** enter the monitor's address and a one-time pairing code. The
  monitor gives this device its own token, and the app registers with your
  UnifiedPush distributor and hands the monitor its push endpoint. The
  monitor confirms with an "Alerts are on" notification.
- **Status:** every check with its state and last failure, the open incident,
  and past incidents with a timeline. Pull to refresh.
- **Notifications:** full text, one per incident (updates replace it), and a
  tap opens that incident. Alerts and recoveries use separate channels, so
  you can silence one.
- **Web page:** the browser button opens the monitor's own page through a
  short-lived sign-in link.

Pushes are Web Push messages (RFC 8291) encrypted to this device; messages
that are not encrypted for the app are dropped.

## The monitor's API

Any server can be a Superscan monitor by answering these JSON routes over
HTTPS. All but the first two take `Authorization: Bearer <device token>`.

| Route | Does |
| --- | --- |
| `GET /api/info` | `{"name", "vapid"}`: the monitor's name and VAPID public key (base64url, uncompressed P-256) |
| `POST /api/pair` | `{"code", "device"}` → `{"token"}` |
| `POST /api/subscription` | `{"endpoint", "p256dh", "auth"}`: where and how to push to this device |
| `GET /api/status` | `{"name", "now", "checks": [{"id", "name", "group", "status", "note"}], "incidents": [{"id", "title", "started", "ended", "checks", "notes": [{"at", "text"}]}]}`; `status` is `ok`, `warn`, `down` or `unknown`; `group` is `site`, `gated`, `mail` or `monitor` |
| `POST /api/weblink` | `{"url"}`: a short-lived link that signs a browser in to the web page |
| `DELETE /api/device` | unpairs this device |

Push payloads are JSON: `{"title", "body", "incident", "kind"}`, where `kind`
is `down`, `up` or `info` and `incident` is a number or `null`.

## Building

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

Releases are built and signed by GitHub Actions when a `v*` tag is pushed;
install and update them with [Obtainium](https://github.com/ImranR98/Obtainium)
pointed at this repository.

## Credits

The layout follows GrapheneOS's [Info](https://github.com/GrapheneOS/Info)
app; push handling uses the UnifiedPush
[connector library](https://codeberg.org/UnifiedPush/android-connector).

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
  monitor confirms with an "Alerts are on" notification. Superscan asks for
  the notification permission before pairing, so that one is not lost.
- **Status:** a summary of healthy checks and 30-day uptime, with the last
  update in the header. One Checks card groups websites, services behind
  the login, mail, and the monitor. Groups stay folded while all is well
  ("14 of 14 up") and open when something needs attention. Each check shows
  its response time, uptime, and 30 daily history bars. Tap a check for
  details and its incidents. Recent incidents appear when there are any.
  It refreshes every 30 seconds while open, and on pull.
- **Incidents:** ongoing and resolved, each with its affected checks and a
  timeline from the first failure to the recovery.
- **Notifications:** full text, one per incident (updates replace it), and a
  tap opens that incident. Alerts and recoveries use separate channels, so
  you can silence one.
- **Menu:** send a test alert, open the monitor's web page (through a
  short-lived sign-in link), notification settings, re-register alerts,
  unpair.

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
| `GET /api/status` | `{"name", "now", "checks": [...], "incidents": [{"id", "title", "started", "ended", "checks", "notes": [{"at", "text"}]}]}`; each check is `{"id", "name", "group", "status", "note", "what", "ms", "avgMs", "uptime", "days": [{"day", "ok", "fail"}], "lastFail"}`: `status` is `ok`, `warn`, `down` or `unknown`; `group` is `site`, `gated`, `mail` or `monitor`; `what` describes the check; `ms` is the last response time and `avgMs` the 30-day average (or `null`); `uptime` is the 30-day share of passing runs in percent; `days` are the last 30 UTC days, oldest first |
| `POST /api/test` | pushes a test alert to this device |
| `POST /api/weblink` | `{"url"}`: a short-lived link that signs a browser in to the web page |
| `DELETE /api/device` | unpairs this device |

Push payloads are JSON: `{"title", "body", "incident", "kind"}`, where `kind`
is `down`, `up` or `info` and `incident` is a number or `null`.

## Building

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
```

Debug builds (`.debug` application id) may also pair with a monitor on the
emulator's host over plain HTTP (`http://10.0.2.2:<port>`, for example
`wrangler dev`); release builds accept HTTPS only.

Releases are built and signed by GitHub Actions when a `v*` tag is pushed;
install and update them with [Obtainium](https://github.com/ImranR98/Obtainium)
pointed at this repository.

## Credits

Superscan uses [Manrope](https://github.com/sharanda/manrope) by Mikhail
Sharanda and Mirko Velimirovic under the SIL Open Font License 1.1. The
[font license and source](licenses/manrope/README.md) are bundled here.
Colors follow your wallpaper when available, with a teal fallback and
separate light and dark status palettes.

The original layout was based on GrapheneOS's
[Info](https://github.com/GrapheneOS/Info) app. Push handling uses the
UnifiedPush [connector library](https://codeberg.org/UnifiedPush/android-connector).

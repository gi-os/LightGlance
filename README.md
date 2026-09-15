# Glance

Ambient notification dots for the **Light Phone III**. Package `com.gios.lightglance`.

**Current version: v1.0.4.** See [Version history](#version-history).

> **Experimental.** Untested on hardware — every line of this was written against a
> `dumpsys` capture and AOSP sources, not a running phone. It asks for device admin and
> for a background-activity-start exemption, and it deliberately wakes your screen and
> puts it back to sleep. If a guard is wrong the failure modes are a flat battery or a
> phone that fights you when you press the power button. Read
> [The four guards](#the-four-guards-and-what-each-one-prevents) before running it, keep
> `adb` within reach, and know that `adb shell pm uninstall com.gios.lightglance` ends
> any argument. Device admin has to be released first:
> `adb shell dpm remove-active-admin --user 0 com.gios.lightglance/com.gios.lightglance.admin.GlanceAdminReceiver`

When something arrives, the panel lights up for a few seconds showing a small white
glyph on black — one glyph per source, each at a fixed position. You learn to read
"two texts and a missed call" without turning the phone on properly.

```
        9:41

    ●   ○       ▪
    3
```

## What this is and why

This is **not** a hardware always-on display, and worth saying plainly because the
difference is the whole battery story. A real AOD puts the panel into a low-power
self-refresh mode with the application processor asleep, drawing something like
0.5%/hr — that path is owned by SystemUI and the panel driver, and a sideloaded app
cannot reach it on any Android device without system privileges.

What this app does instead is turn the display genuinely **on**, at the lowest
brightness the window manager will accept, showing a frame that is almost entirely
black. On OLED the lit pixels are nearly free; the awake SoC is not — budget 2–4%/hr in
`Always` mode, which on the LPIII's battery is a meaningful chunk of a night. So the
default mode is `Poke`: wake for eight seconds when something arrives, wake on lift,
sleep otherwise. That costs approximately nothing and delivers most of the value,
because in practice you glance at the phone rather than stare at it.

Built from the same plain-APK skeleton as the rest of the [gi-os Light App collection](https://github.com/gi-os/BrightAuthenticator#the-gi-os-light-app-collection),
no SDK sandbox — `NotificationListenerService` reads standard Android notifications
(LightOS posts fully standard ones), filtered by importance so it doesn't need a
hand-maintained package list to stay useful.

## Quick start

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then grant the two required permissions. Neither has a Settings screen on LightOS, so
both are adb-only. Both survive reboots; neither survives an uninstall.

```bash
adb shell cmd notification allow_listener \
  com.gios.lightglance/com.gios.lightglance.notif.NotifListener

adb shell appops set com.gios.lightglance SYSTEM_ALERT_WINDOW allow
```

The `SYSTEM_ALERT_WINDOW` appop is not about drawing an overlay — on Android 14 it's
what exempts an app from
[background-activity-start restrictions](https://developer.android.com/guide/components/activities/background-starts),
and starting an activity while the screen is off is a background activity start.
Without it nothing ever appears, and a blocked start doesn't throw: it returns
`START_ABORTED` and pretends to have worked, which is why `GlanceController`
pre-checks the appop rather than trusting `startActivity`.

Open the app to confirm both grants went through — the setup screen polls, so you can
leave it open while running the commands.

## Configuration and usage

### Reading the dots

| Glyph | Source | Package |
|---|---|---|
| ● filled circle | BrightChat | `com.gios.lightchat` (and the old `com.craigeley.chat`) |
| ○ ring | Missed call | `com.android.server.telecom` |
| ▪ square | Messages | `com.lightos` |
| ▫ frame | App updates | `dev.imranr.obtainium` |
| ▲ triangle | Alarm | `*.deskclock` |
| ▬ bar | Anything else | assigned on first sight |

Position matters as much as shape. Each package keeps its slot permanently, and empty
slots still occupy their space, so a dot never slides sideways because something
unrelated happened to be pending. A numeral under a glyph means more than one. Colour
is not used at all — LightOS renders greyscale, so an iMessage-blue dot and a
WhatsApp-green dot would arrive as two indistinguishable mid-greys.

### Optional: force-lock on dismissal

```bash
adb shell dpm set-active-admin --user 0 \
  com.gios.lightglance/com.gios.lightglance.admin.GlanceAdminReceiver
```

Finishing the ambient surface only releases `FLAG_KEEP_SCREEN_ON`; the panel then stays
lit until LightOS's own sleep timer, so an eight-second dwell would really be eight
seconds plus up to ten minutes. `DevicePolicyManager.lockNow()` is the only way an
unprivileged app can darken the screen on demand, and it needs an active device admin.
The receiver requests `force-lock` and nothing else, and is skipped when the keyguard
wasn't already up, so a glance triggered by picking the phone up doesn't force you to
re-enter a PIN.

### What gets a dot

Everything worth showing on LightOS posts at importance ≥ 3, and everything that isn't
— media transport controls, VPN and player foreground services, the framework's own
`ranker_group` placeholders — posts at 2. So importance does nearly all the filtering,
and `Filter.kt` only needs a few flag checks on top to drop group summaries that
duplicate their children. This was derived from a real `adb shell dumpsys notification`
capture rather than guessed. If something you want is filtered out, check
`LAST DECISION` on the setup screen and dump the notification:

```bash
adb shell dumpsys notification --noredact | grep -B2 -A12 "pkg=your.package"
```

### The wheel

The brightness wheel scrolls the setup screen, which is long — three adb commands, four
status rows, a preview, and a row for every source the phone has ever notified about.
The ambient surface deliberately does **not** take the wheel: there's nothing to scroll
there, and consuming a hardware key on a window shown over the keyguard is the same
class of bug [the four guards](#the-four-guards-and-what-each-one-prevents) exist to
prevent. That has one visible cost now that [BrightControl](https://github.com/gi-os/BrightControl)
exists — it passes bare turns through to `com.gios.*` rather than acting on them, and
Glance ignores them on the ambient surface, so a turn there does nothing where before it
would have changed the brightness. The surface lasts a few seconds and the wheel keeps
working the moment it goes.

Notches arrive as ordinary key events because Light patched
`/system/usr/keylayout/Generic.kl`; `hw/LightKeys.kt` resolves `WHEEL_CCW` and
`WHEEL_CW` by label at runtime and falls back to the raw scancode gated on the sensor's
device name. No service to enable, no permission, no root — the two grants above are
for the notifications and the screen, not the wheel.

```bash
# Optional: BrightControl, for brightness, the flashlight and the camera button
adb install -r LightControl-v1.0.x.apk

adb shell settings put secure enabled_accessibility_services \
  com.gios.lightcontrol/com.gios.lightcontrol.keys.ControlService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.gios.lightcontrol WRITE_SETTINGS allow
adb shell appops set com.gios.lightcontrol SYSTEM_ALERT_WINDOW allow
```

Latest APK: https://github.com/gi-os/BrightControl/releases/latest

## Architecture

No foreground service. `NotificationListenerService` is bound and kept alive by the
system, so it doubles as the app's long-lived component — which also means Glance
doesn't add a permanent notification of its own to the list it's reading.

- `notif/NotifListener.kt` — recomputes the whole dot set from `activeNotifications`
  on every event. No incremental key bookkeeping, nothing to drift.
- `notif/Filter.kt` — importance and flag filtering.
- `notif/SlotMap.kt` — package → glyph, with sticky slot assignment in prefs.
- `notif/SensorHub.kt` — significant motion for lift-to-show (runs on the sensor hub,
  so the AP stays asleep until it fires) and proximity for pocket suppression.
- `GlanceController.kt` — decides whether to wake, and is mostly guards.
- `GlanceActivity.kt` — the surface. `showWhenLocked`, `turnScreenOn`, brightness
  override, burn-in jitter. Takes no hardware keys.
- `hw/` — the brightness wheel, turns only, for the setup screen.

### The four guards, and what each one prevents

None of these are cosmetic. Each corresponds to a way the app traps the user or
flattens the battery.

- **Lift cooldown (60s, plus 5 min if the dot set hasn't changed).** Significant motion
  fires every few seconds while you walk. With one notification pending: motion →
  screen on → 8s dwell → `lockNow` → `SCREEN_OFF` → re-arm → motion — a ~10 second cycle
  with the panel lit for 8 of them, for as long as you keep moving.
- **Self-sleep window (1.5s).** `lockNow()` broadcasts `ACTION_SCREEN_OFF` exactly like
  a power press. Without distinguishing our own sleep from a real one, `Always` mode
  wakes itself back up forever.
- **Dismissal cooldown (90s), skipped for new arrivals.** Stops the power-button trap:
  press power → `SCREEN_OFF` → we wake it again → the phone cannot be turned off. Not
  applied to `POST`, or the most natural gesture in the app would mute your messages
  for a minute and a half.
- **Lifecycle-scoped dwell.** A plain `LaunchedEffect` keeps counting after `onPause` —
  only the frame clock pauses, not `delay()`. The dwell could then fire `lockNow()`
  seconds after you'd moved on to another app, locking the phone in your hand.

### Burn-in

Static white glyphs on OLED are the one failure mode here that is permanent, so the
whole field walks an eight-position ring every 45 seconds, ±12dp — enough to spread the
load, small enough that you never notice it moved. The clock is drawn grey rather than
white for the same reason: it's the largest lit area on the panel.

## Known unknowns

- **The hardware brightness wheel.** If it clamps the window-level `screenBrightness`
  override, the ambient surface will come up at whatever the wheel says instead of 2%.
  `GlanceActivity.onCreate` is the line that would appear to do nothing.
- **`com.lightos` posts one coalesced record** (`id=0`), not one per message, so it's a
  boolean dot with no count. If `android.number` or a countable title turns up in its
  extras, `SlotMap` can start parsing it.
- **LightOS power management** may decide to unbind the listener.
  `NotifListener.connected` on the setup screen is the canary.
- **A real notification LED?** Missed calls arrive with `FLAG_SHOW_LIGHTS` set, meaning
  telecom is asking for one. Whether the hardware exists:
  `adb shell ls /sys/class/leds/`. Writing to it would need root regardless.
- **Stock doze AOD** may or may not be compiled into this build of LightOS:
  `adb shell settings get secure doze_always_on`. Even if it's on, it shows SystemUI's
  own clock, not these dots.

## Building

```bash
./gradlew :app:assembleRelease
python3 scripts/generate_icon.py   # regenerate the launcher icon
```

## Contributing

Solo repo, no PR workflow: commits go straight to `main`, and every push to `main`
triggers CI, which builds, signs, and tags `v${versionName}`, publishing a GitHub
Release. **A push is a release, not a cosmetic action** — verify before pushing, not
after; bump `versionName` in `app/build.gradle.kts` or Obtainium sees no new release
and the change never reaches the phone. `versionCode` is the workflow run number.

The signing key is committed at `keystore/lightglance.jks` and pinned by
`signing-fingerprint.txt`; CI fails if the certificate ever drifts, because Android
identifies an app by `(packageName, signing cert)` and a changed cert turns Obtainium
updates into an opaque `Failure: Invalid`.

## Version history

Real tags, oldest to newest:

| Version | What changed |
| --- | --- |
| v1.0.1 | Initial release — ambient notification dots, Poke/Always modes, the four guards |
| v1.0.2 | Follows BrightChat's package rename (`com.craigeley.chat` → `com.gios.lightchat`) in `SlotMap` |
| v1.0.3 | Hardware wheel scrolls the setup screen |
| v1.0.4 | README: documents what a turn on the ambient surface does, and what the wheel needs |

## Licence

No `LICENSE` file is committed in this repo yet.

## Support

These apps are free, open, and built on my own time. Sponsorship pays the bills that don't go away: build servers, test hardware, and the crash reporter that keeps them shipping. Donation or not my code is always free for the world to use.

[Sponsor on GitHub](https://github.com/sponsors/gi-os)

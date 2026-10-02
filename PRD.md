# BlinkWell — Android Eye-Blink Monitoring App
### Build Specification for AI Coding Agent
**App Name:** BlinkWell · **By:** Mitali Purohit

> **Purpose:** Build a free, privacy-respecting, lightweight Android app that periodically checks the user's blink rate via the front camera while the phone is in active use, alerts the user via notification if blinking is abnormally low, and (only with explicit opt-in consent) sends anonymized, aggregated metrics to Supabase for a researcher-facing analytics dashboard. The app must be compiled and packaged entirely through **GitHub Actions**, since Android Studio is not installed locally.

---

## 0. Ground Rules for the Agent

1. **Privacy-first, on-device-first.** Raw camera frames/video are **never** stored, transmitted, or uploaded — ever. All face/eye analysis happens on-device. Only numeric blink-rate metrics ever leave the device, and only if the user consented.
2. **This is a wellness nudge tool, not a medical device.** Every alert, onboarding screen, and the Play Store listing must say so explicitly (see §16).
3. **Lightweight by design.** Low RAM footprint and low battery drain are hard requirements, not nice-to-haves — see §8.
4. **No local Android Studio.** The project must be structured to build entirely from the command line (Gradle wrapper) so that **GitHub Actions** can compile and package the APK — see §15. Do not assume any step requires opening Android Studio.
5. **Everything must be achievable on free tiers.** The only unavoidable real-world cost is the one-time Google Play Console registration fee (~$25, paid once) if the user later chooses to publish.
6. **Background monitoring is a user choice, not a default assumption.** The user must be explicitly asked during onboarding whether they want always-on background monitoring or a lighter, app-open-only mode — see §5 and §7.
7. **Consent and login are separate, independent things.** Core features work fully offline, with zero data leaving the device, with no account ever created.

---

## 1. App Identity

- **App name:** BlinkWell
- **Developer credit:** "By Mitali Purohit" — shown on the About screen footer, and in the Play Store listing's developer/description text.
- **Suggested package name:** `com.mitalipurohit.blinkwell` (agent may confirm with the user before finalizing, as this cannot be changed after publishing).
- `app_name` string resource = "BlinkWell" (localized per language where natural; keep the brand name itself untranslated).

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Android app | Kotlin + Jetpack Compose | Modern, free, Google-supported |
| Architecture | MVVM + Repository pattern | Testable, standard |
| Camera | CameraX (`ImageAnalysis` use case) | Free, handles lifecycle well |
| Blink/eye detection | **ML Kit Face Detection (on-device)** | Free, offline, gives `leftEyeOpenProbability` / `rightEyeOpenProbability` |
| Background execution | Foreground Service (type `camera`), **only if user opts into background mode** | Required by Android to use the camera when the app isn't on-screen |
| Local alerts | `NotificationCompat` | No FCM/server needed — everything computed on-device |
| Backend | **Supabase** (Postgres + Auth + Row Level Security) | Generous free tier, built-in anonymous auth |
| Optional login | Supabase Auth — anonymous sign-in by default, optional email/OTP upgrade | Keeps login optional |
| Research dashboard | Next.js + Supabase JS client + Recharts/Tremor | Deployed free on Vercel/Netlify |
| Widget (optional) | Jetpack Glance | Compose-based widget API |
| Multilingual | `strings.xml` per locale + per-app language API | Standard, free |
| **CI/CD & APK builds** | **GitHub Actions** (`gradlew assembleDebug` / `assembleRelease`) | Replaces local Android Studio entirely — see §15 |

---

## 3. High-Level Architecture

```
┌─────────────────────────────┐
│        Android App          │
│  Onboarding + Consent +      │
│  "Background or app-only?"  │
│            │                 │
│   ┌────────┴─────────┐       │
│   ▼                   ▼       │
│ Background mode   App-only mode
│ (Foreground        (camera runs
│  Service, persistent only while app
│  notification)      is on screen,
│   │                 no service, 
│   ▼                 lowest resource
│ CameraX → ML Kit → Blink counter → Rolling BPM
│            │ aggregated metrics only
│  ┌─────────▼──────────────┐
│  │ Local Room DB (always)  │  ← works fully offline
│  └─────────┬──────────────┘
│            │ if consented
└────────────┼─────────────────┘
             ▼
     ┌───────────────┐
     │    Supabase    │
     └───────┬────────┘
             ▼
     ┌───────────────────────┐
     │ Researcher Dashboard   │ (admin-login only)
     └───────────────────────┘
```

---

## 4. Permissions & Required Manifest Entries

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" /> <!-- Android 14+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->
<uses-permission android:name="android.permission.INTERNET" /> <!-- only used if user consents to sync -->

<service
    android:name=".monitor.BlinkMonitorService"
    android:foregroundServiceType="camera"
    android:exported="false" />
```

The `BlinkMonitorService` and its manifest entry are only ever **started** if the user chose Background Mode in §5 — the code path for App-Only Mode never touches the foreground service at all, keeping that mode's resource footprint minimal.

Request `CAMERA` and `POST_NOTIFICATIONS` as **runtime** permissions, only after the user passes the consent screen and chooses a monitoring mode.

---

## 5. Onboarding & Consent Flow (first launch)

Build exactly this sequence:

1. **Welcome screen** — what the app does, in plain language, with the BlinkWell name and "By Mitali Purohit" credit.
2. **How it works screen** — camera runs briefly at intervals, analysis happens on-device, nothing is recorded as video.
3. **Consent screen (independently optional toggles):**
   - ☐ "Allow this app to monitor my blink rate" — required to use core features.
   - ☐ "Share anonymized blink-rate data to help eye-health research" — optional, off by default.
   - Link to a plain-language Privacy Policy page.
4. **Monitoring mode screen (new — explicit user choice, not a default):**
   - **"Run in background"** — BlinkWell keeps checking periodically even when you're using other apps, via a visible ongoing notification. Slightly higher battery use.
   - **"Only check when I open BlinkWell"** — lowest battery/RAM use; monitoring happens only while the app itself is on screen.
   - Explain the trade-off in one short line each. Default selection: neither pre-checked — the user must actively pick one to continue. Editable anytime later in Settings.
5. **Permission requests** — Camera, then Notifications (only if Background Mode was chosen, since App-Only Mode may not need a persistent notification) — each with a short "why" shown before the OS dialog.
6. **Optional account screen** — "Continue without an account" (default) vs. "Create an account to sync across devices." Choosing "continue without an account" silently performs Supabase anonymous sign-in behind the scenes.
7. **Sensitivity/threshold screen (optional, skippable, sensible default).**

All choices from steps 3, 4, and 6 must be editable later from **Settings → Privacy & Monitoring**, including a "Delete my data" button.

---

## 6. Blink Detection Logic (core algorithm)

1. CameraX `ImageAnalysis`, front camera, low resolution target (e.g. 320×240), low frame rate (3–5 fps).
2. ML Kit `FaceDetector`, classification mode enabled, to read `leftEyeOpenProbability` / `rightEyeOpenProbability` → average into `eyeOpenScore` (0.0–1.0).
3. Hysteresis state machine: `< 0.4` = closed, `> 0.6` = open; a closed→open transition within ~500ms = one blink.
4. Rolling 60-second window of blink timestamps → `blinksPerMinute`; recompute every 10–15 seconds.
5. If no face is detected, pause counting rather than registering false zeros.
6. **Alert condition:** `blinksPerMinute < userThreshold` (default 10, adjustable) sustained for 2 consecutive minutes while a face is actively detected → local notification, with a 10-minute cooldown between repeat alerts.
7. Only aggregated per-minute BPM values ever reach storage or Supabase — never raw frames.

---

## 7. Monitoring Modes: Background vs. App-Only

Reflecting the user's choice from §5 step 4:

| | **Background Mode** | **App-Only Mode** |
|---|---|---|
| Runs via | Foreground Service (`camera` type) | In-Activity camera analysis only |
| Persistent notification | Yes (Android requirement) | No |
| Active when | Screen on, regardless of which app is in front | Only while BlinkWell itself is the visible app |
| Resource use | Low, but non-zero in background | Minimal — zero camera/CPU use when app is closed |
| Best for | Users who want continuous passive monitoring | Users who prefer to open the app for periodic checks and minimize footprint |

Implementation notes:
- In **Background Mode**, the service additionally listens for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` and stops camera capture immediately on screen-off — never analyze with the screen off.
- In **App-Only Mode**, camera analysis binds/unbinds with the Activity lifecycle (`onStart`/`onStop`) — nothing runs once the user leaves the app.
- The mode can be switched anytime in Settings; switching from Background → App-Only should immediately stop and remove the foreground service and its notification.

---

## 8. Performance & Resource Efficiency (hard requirements)

The app must stay lightweight in both modes:

- **Low resolution, low frame rate analysis** (§6.1) — never analyze at full camera resolution or full frame rate; this is a classifier, not a photo app.
- **CameraX backpressure strategy:** `STRATEGY_KEEP_ONLY_LATEST` on `ImageAnalysis` so frames never queue up and bloat memory.
- **Close every `ImageProxy` immediately** after processing each frame to avoid buffer leaks.
- **No wake locks** beyond what the foreground service itself implicitly requires — never acquire a manual `PowerManager.WakeLock`.
- **Unbind CameraX use cases immediately** when monitoring is paused, screen turns off, or the app/service stops — never leave the camera pipeline warm when not actively sampling.
- **Avoid polling loops**; drive everything off CameraX's own frame callbacks and Android's screen on/off broadcasts.
- **Batch Supabase writes** (e.g., once per session or every few minutes) rather than one network call per blink or per minute, to save battery on radio use.
- **No large bitmaps, no image caching** — frames are analyzed and discarded, never held in memory longer than one analysis cycle.
- **Respect Doze/App Standby** — do not attempt to defeat battery optimization; Background Mode should degrade gracefully (less frequent sampling) under Doze rather than fight the OS.
- Target: the app should be unnoticeable in the system Battery usage list under normal daily use, and use well under 100MB of RAM during active monitoring.

---

## 9. Local Notifications

- Dedicated channel `"Blink Alerts"`, importance HIGH.
- Short, friendly, non-alarming copy, e.g. *"Your blink rate looks low right now — take a 20-second break and blink a few times."*
- Tapping opens the app to the stats screen.
- Adjustable sensitivity/mute in Settings.

---

## 10. Data Model (local + Supabase)

### Local (Room DB) — always present, works fully offline
- `blink_sessions(id, start_time, end_time, avg_bpm, min_bpm, alert_count, mode)`
- `blink_minute_log(session_id, minute_timestamp, bpm)`

### Supabase (Postgres) — only populated if user opted into data sharing

```sql
create table profiles (
  id uuid primary key references auth.users(id),
  research_consent boolean default false,
  created_at timestamptz default now()
);

create table blink_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references profiles(id) not null,
  started_at timestamptz not null,
  ended_at timestamptz,
  avg_bpm numeric,
  min_bpm numeric,
  alert_count int default 0,
  monitoring_mode text check (monitoring_mode in ('background','app_only'))
);

create table blink_minute_log (
  id bigint generated always as identity primary key,
  session_id uuid references blink_sessions(id) not null,
  minute_timestamp timestamptz not null,
  bpm numeric not null
);
```

**Row Level Security (critical):**
- Enable RLS on every table.
- Users can `insert`/`select` only rows where `user_id = auth.uid()`.
- A separate `researcher` role can `select` rows only where the owning profile has `research_consent = true`. Never expose the service-role key in the mobile app or a public dashboard build.

---

## 11. Researcher Dashboard (per-user drill-down, consented data only)

- Separate Next.js project, deployed free (Vercel/Netlify).
- **Admin/researcher-login only** — never publicly browsable, even though underlying data is pseudonymous.
- Views: (1) Overview — total consented users, aggregate BPM trend; (2) Per-user drill-down — session history and BPM trend for a selected anonymous `user_id`; (3) Filters by date range and BPM range.
- Display only anonymous UUIDs by default, never real names/emails.

---

## 12. Multilingual Support

- Externalize every string (`res/values/strings.xml` + locale variants, e.g. `values-hi/`).
- Start with **English + Hindi**; structure resources so more languages are a drop-in addition.
- Use Android 13+ per-app language settings with `AppCompatDelegate` fallback for older versions.
- Translate all onboarding/consent text, the monitoring-mode choice screen, and notification/alert text — these are highest visibility.

---

## 13. UI/UX Requirements

- **Home screen:** one big, calm status indicator (gauge/ring), current monitoring mode shown clearly, single Start/Stop toggle.
- **Stats screen:** simple line chart of recent blink rate, list of past alerts.
- **Settings screen:** sensitivity slider, language picker, notification mute, monitoring mode switch (Background ⇄ App-Only), privacy/consent management with data-deletion button.
- **About screen:** app name "BlinkWell", credit "By Mitali Purohit", version number, disclaimer text (§16), privacy policy link.
- Minimal palette, large tap targets, full accessibility labels, respects system font scaling and dark mode.
- No dark patterns — both monitoring-mode options and both consent toggles must look equally easy to choose.

---

## 14. Optional: Home Screen Widget

- Build with Jetpack Glance.
- Shows current/last blink rate and a simple color state (green/amber/gray), tap opens the app.
- Only meaningful in Background Mode (App-Only Mode has nothing live to show between app opens — display last session's summary instead).
- Treat as a late-phase, lowest-priority deliverable (§17).

---

## 15. CI/CD — Building the APK via GitHub Actions

Since Android Studio is not available locally, **all compilation happens in GitHub Actions**, and the user downloads the built APK as a workflow artifact (or release asset).

### 15.1 Project requirements
- Must build cleanly with the Gradle wrapper (`./gradlew`) with zero manual Android Studio steps (no relying on IDE-only sync actions).
- Commit the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) to the repo.

### 15.2 Debug build workflow (every push — for quick testing)

```yaml
name: Android CI - Debug Build

on:
  push:
    branches: [ main ]
  pull_request:
  workflow_dispatch:

jobs:
  build-debug:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build debug APK
        run: ./gradlew assembleDebug

      - name: Upload debug APK
        uses: actions/upload-artifact@v4
        with:
          name: blinkwell-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

The user downloads this from the **Actions tab → workflow run → Artifacts** section to sideload and test on a real device.

### 15.3 Signed release build workflow (triggered manually or on a version tag)

- Generate a release keystore once (can be done via a one-off GitHub Actions job running `keytool`, or documented as a manual one-time step) and store it, base64-encoded, plus its passwords and key alias, as **GitHub Secrets** (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). Never commit the keystore or passwords to the repo.
- Add a release job that decodes the keystore from the secret, runs `./gradlew assembleRelease` with signing config wired to those secrets, and uploads the signed APK/AAB as a workflow artifact or attaches it to a GitHub Release (`softprops/action-gh-release`).
- Use an **Android App Bundle (`.aab`)** for the actual Play Store upload (`bundleRelease`), and a signed **APK** for direct sideload testing.

### 15.4 Keeping it fast
- Enable Gradle caching (shown above) and, if useful, `org.gradle.caching=true` / `org.gradle.parallel=true` in `gradle.properties` to keep CI build times low on the free GitHub Actions minutes tier.

---

## 16. Play Store Compliance Checklist

- [ ] Privacy Policy page (plain language, free-hosted, e.g. GitHub Pages) describing exactly what's collected, that it's opt-in, and how to delete data.
- [ ] Accurate Play Console Data Safety form.
- [ ] Camera permission justification: core functionality visibly uses the camera for eye-health checks.
- [ ] Foreground service camera type declared, visible non-dismissible-while-active notification (Background Mode only).
- [ ] No camera access while screen is off or monitoring is paused.
- [ ] Store listing and in-app disclaimer: **not a medical device; general wellness awareness only.**
- [ ] One-time Play Console developer registration fee (~$25) — the only non-free cost in this build.
- [ ] Listing credits "BlinkWell by Mitali Purohit."

---

## 17. Suggested Build Order (phases for the agent)

1. **Phase 1 — Skeleton:** Project scaffolding (Kotlin + Compose), Gradle wrapper committed, navigation, onboarding/consent/monitoring-mode screens (no camera, no backend yet). Confirm it builds via `./gradlew assembleDebug` locally in CI before moving on.
2. **Phase 2 — CI pipeline first:** Stand up the GitHub Actions debug-build workflow (§15.2) immediately, so every subsequent phase is verified by an actual compiled APK, not just source review.
3. **Phase 3 — Core detection:** CameraX + ML Kit blink counter in App-Only Mode, visible BPM number, tested on a real sideloaded device.
4. **Phase 4 — Background Mode:** Foreground service, screen-on/off lifecycle, local notification alerts, local Room persistence, mode switch in Settings.
5. **Phase 5 — Performance pass:** Apply all of §8 explicitly; measure RAM/battery on a real device in both modes before continuing.
6. **Phase 6 — Supabase:** Schema + RLS (§10), anonymous auth by default, optional email upgrade, sync only if consented.
7. **Phase 7 — Settings, multilingual, branding, polish:** Settings screen, English+Hindi strings, About screen with "BlinkWell by Mitali Purohit," dark mode, accessibility pass.
8. **Phase 8 — Optional widget.**
9. **Phase 9 — Dashboard:** Next.js researcher dashboard (§11), deployed to Vercel free tier.
10. **Phase 10 — Release pipeline & Play Store readiness:** Signed release workflow (§15.3), §16 checklist, privacy policy page, internal testing track.

---

## 18. Testing Notes

- Test ML Kit face detection accuracy on a **real device** (emulator camera is unreliable).
- Test across varying lighting, with/without glasses, different holding distances/angles.
- Battery-drain test across a full day of typical screen-on time, in both Background and App-Only modes.
- Confirm RAM usage stays well under 100MB during active monitoring (Android Studio Profiler is not available — use `adb shell dumpsys meminfo <package>` from the command line instead).
- Verify Background Mode truly stops the camera on screen-off and App-Only Mode truly stops on app-close.

---

## 19. Disclaimer Text (use verbatim or adapt, show in onboarding + About screen)

> "BlinkWell is a general wellness tool that estimates your blink rate using your device's camera. It is not a medical device and does not diagnose, treat, or prevent any condition. Lighting, glasses, and camera angle can affect accuracy. If you have concerns about dry eyes, eye strain, or vision changes, please consult an eye care professional."
>
> BlinkWell — By Mitali Purohit

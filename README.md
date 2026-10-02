# BlinkWell — Mindful Eye Care & Blink Wellness App

**Developed by:** Mitali Purohit  
**Package Name:** `com.mitalipurohit.blinkwell`  
**License:** Apache 2.0  

BlinkWell is a privacy-first, lightweight Android application that monitors your blink rate via the front camera while your device is in active use, alerts you via gentle notifications if blinking is abnormally low, and (only with explicit opt-in consent) syncs anonymized aggregate metrics to a researcher analytics dashboard.

---

## 🌟 Key Highlights

- **100% On-Device Eye Analysis:** Raw camera frames/video are **never** stored, cached, or transmitted. Face and eye openness analysis is executed entirely on-device via Google ML Kit.
- **Background & App-Only Modes:**
  - **Background Mode:** Runs a camera foreground service with persistent notification and duty-cycled burst sampling to minimize battery drain. Halts immediately on screen-off.
  - **App-Only Mode:** Camera only runs while BlinkWell is actively open on screen (0% background CPU/battery footprint).
- **Offline First:** Room DB stores all session history locally. Core features require no account and no internet.
- **Multilingual:** Full support for **English** and **हिन्दी (Hindi)** with per-app language settings.
- **Built entirely via GitHub Actions:** No local Android Studio needed — CI automatically compiles and packages debug APKs and signed release bundles.

---

## 🏗️ Monorepo Structure

```
BlinkWell/
├── .github/workflows/
│   ├── android-build.yml      # CI debug APK compilation on push/PR
│   ├── android-release.yml    # Release signed APK / AAB bundling
│   └── dashboard-deploy.yml   # Next.js researcher portal CI
├── android/                   # Kotlin + Jetpack Compose Android app
│   ├── app/
│   │   ├── src/main/java/com/mitalipurohit/blinkwell/
│   │   │   ├── data/          # Room DB, Preferences, Supabase sync
│   │   │   ├── detection/     # CameraX analyzer & ML Kit face/blink detector
│   │   │   ├── service/       # Foreground service & screen on/off receiver
│   │   │   ├── ui/            # Compose screens (Onboarding, Home, Stats, Settings, About)
│   │   │   └── widget/        # Jetpack Glance Home Screen Widget
│   │   └── build.gradle.kts
│   ├── gradle/wrapper/        # Gradle 8.9 wrapper binaries and configs
│   ├── gradlew / gradlew.bat
│   └── build.gradle.kts
├── dashboard/                 # Next.js 15 App Router researcher portal
│   ├── src/app/               # Overview, Users directory, Per-user drilldown
│   ├── src/components/        # Navbar, Recharts components
│   └── package.json
├── supabase/
│   └── schema.sql             # Postgres tables & Row-Level Security (RLS) policies
├── PRIVACY_POLICY.md          # Comprehensive user privacy policy
├── PLAY_STORE_DATA_SAFETY.md  # Google Play Console compliance guide
└── PRD.md                     # Product Requirements Document
```

---

## 🚀 Building the App (GitHub Actions)

Since this project is configured to build without requiring a local Android Studio installation:

1. Push your code to your GitHub repository:
   ```bash
   git add .
   git commit -m "Initialize BlinkWell"
   git push origin main
   ```
2. Open the **Actions** tab in your GitHub repository.
3. The **Android CI - Debug Build** workflow will compile the APK.
4. Download `blinkwell-debug-apk` from the workflow run's **Artifacts** section and install it directly on your Android phone.

---

## 📊 Supabase Setup (Optional for Research Sync)

1. Create a free project at [supabase.com](https://supabase.com).
2. Go to the **SQL Editor** in Supabase and paste the contents of [`supabase/schema.sql`](file:///d:/BlinkWell/supabase/schema.sql). Run the script to create tables and RLS policies.
3. In your GitHub repository settings, under **Secrets and variables → Actions**, add:
   - `SUPABASE_URL` = `https://your-project.supabase.co`
   - `SUPABASE_ANON_KEY` = `your-anon-key`

---

## ⚕️ Wellness Disclaimer

> "BlinkWell is a general wellness tool that estimates your blink rate using your device's camera. It is not a medical device and does not diagnose, treat, or prevent any condition. Lighting, glasses, and camera angle can affect accuracy. If you have concerns about dry eyes, eye strain, or vision changes, please consult an eye care professional."
> 
> BlinkWell — By Mitali Purohit

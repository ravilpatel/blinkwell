# Google Play Store Compliance & Data Safety Guide

**App Name:** BlinkWell  
**Developer Credit:** By Mitali Purohit  
**Package:** `com.mitalipurohit.blinkwell`  

---

## 1. Google Play Data Safety Form Responses

| Category | Question | BlinkWell Response | Notes |
|---|---|---|---|
| **Data Collection** | Does your app collect or share any user data? | **Yes** (Only if user opts in) | If user opts into research data sharing |
| **Data in Transit** | Is all user data encrypted in transit? | **Yes** | Transmitted exclusively via HTTPS/TLS 1.3 |
| **Account Creation** | Can users use the app without an account? | **Yes** | Anonymous / offline by default |
| **Data Deletion** | Do you provide a way for users to request data deletion? | **Yes** | Built-in "Delete All My Local Data" in Settings |
| **Biometric / Camera Data** | Does the app collect Photos or Videos? | **NO** | Raw frames are analyzed on-device in memory and never stored or sent |
| **Health & Fitness** | Health / Fitness info collected? | **Optional (Blinks Per Minute)** | Collected only with explicit opt-in consent for eye health research; strictly pseudonymous |
| **Device IDs** | Device or other IDs? | **Optional (Pseudonymous User ID)** | Random UUID generated for Supabase auth; no hardware IMEI / MAC |

---

## 2. Foreground Service Policy Declaration (`FOREGROUND_SERVICE_CAMERA`)

- **Service Type:** `camera`
- **User-Facing Purpose:** BlinkWell provides real-time eye-blink rate monitoring to help users prevent digital eye strain and dry eyes while actively using other applications on their device.
- **Trigger:** The foreground service is initiated **only upon direct user action** when the user starts monitoring in *Background Mode*.
- **Ongoing Notification:** A prominent ongoing notification is displayed throughout active monitoring with a direct action to open the app and stop the service.
- **Screen Off Policy:** The service registers a screen state receiver and halts camera analysis immediately when `ACTION_SCREEN_OFF` is received.

---

## 3. Store Listing Copy & Verbatim Disclaimers

### Short Description (80 characters)
Mindful eye care & blink rate monitor to reduce digital eye strain. By Mitali Purohit.

### Full Description Excerpt
> BlinkWell is a lightweight, privacy-first Android application developed by Mitali Purohit that monitors your blink frequency while using your device and nudges you to take mindful eye breaks.
> 
> **Key Features:**
> - 100% On-Device Face Analysis: No photos or videos are ever saved or uploaded.
> - Choice of Monitoring Mode: Background continuous/duty-cycled checks or In-App checks.
> - Gentle Wellness Reminders: Non-intrusive notifications when blink rate is low.
> - Offline-First: Works completely offline with zero account requirements.
> 
> **Wellness Disclaimer:**
> BlinkWell is a general wellness tool that estimates your blink rate using your device's camera. It is not a medical device and does not diagnose, treat, or prevent any condition. Lighting, glasses, and camera angle can affect accuracy. If you have concerns about dry eyes, eye strain, or vision changes, please consult an eye care professional.

# Privacy Policy for BlinkWell

**Effective Date:** October 2, 2026  
**Developer:** Mitali Purohit  
**Contact:** privacy@blinkwell.app  

At **BlinkWell**, we believe privacy is a fundamental human right. Our app is designed from the ground up to protect your privacy and ensure that you retain full control over your personal data.

---

## 1. Zero Video or Photo Collection (100% On-Device Processing)
- BlinkWell uses your device’s front-facing camera strictly for real-time, on-device face and eye analysis using Google ML Kit.
- **Raw camera frames, video streams, or photos are NEVER recorded, stored, cached, or transmitted to any server or third party under any circumstances.**
- Frame analysis occurs entirely in transient device memory and each frame buffer is discarded immediately after calculating eye-openness probabilities.

---

## 2. Local-First Data Storage
- By default, all session statistics (such as average blinks per minute, session duration, and alert timestamps) are saved **locally on your device** in a private SQLite database.
- You can use all core features of BlinkWell offline without ever creating an account or connecting to the internet.
- You can delete all locally stored data at any time from **Settings → Privacy & Data → Delete All My Local Data**.

---

## 3. Optional Anonymized Research Data Sharing
- BlinkWell allows users to voluntarily opt into sharing **anonymized, aggregated numeric metrics** (e.g., minute-by-minute blinks per minute, session duration) to assist eye-health researchers in studying digital eye strain.
- **This feature is strictly OPT-IN and turned OFF by default.**
- If you choose to enable research sharing:
  - Data is transmitted over encrypted HTTPS connections to our secure Supabase database.
  - Data is tied only to a pseudonymous, randomly generated participant identifier (UUID) with no personal information (no name, email, phone, location, or biometric face data).
  - You can withdraw your consent and disable data sharing at any time in Settings.

---

## 4. Permissions Requested & Why
- **Camera (`android.permission.CAMERA`):** Required to detect eye blink frequency. Used only while monitoring is actively turned on. Never active when the screen is off.
- **Foreground Service (`android.permission.FOREGROUND_SERVICE_CAMERA`):** Used only if you explicitly choose *Background Mode*, enabling the app to monitor your blink rate while you use other apps. A non-dismissible notification is displayed whenever this service is active.
- **Notifications (`android.permission.POST_NOTIFICATIONS`):** Used to deliver gentle alerts when your blink rate is sustained below your selected threshold.
- **Internet (`android.permission.INTERNET`):** Used only if you opt into research data sharing or sign in to sync.

---

## 5. Medical Disclaimer
BlinkWell is a general wellness tool designed for eye wellness awareness and is **not a medical device**. It is not intended to diagnose, treat, cure, or prevent any medical condition.

---

## 6. Contact Us
If you have questions or concerns regarding this Privacy Policy, please reach out to us at `privacy@blinkwell.app`.

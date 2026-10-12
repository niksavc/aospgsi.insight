# Monetization and distribution policy

Not legal advice; have a lawyer review it before you take money.

## Rules

1. **No proprietary Google software is hosted, bundled, sold or unlocked by payment.** Play Store,
   Play Services, Google WebView and other GApps are licensed by Google to device makers only. The
   app flags them (`ProprietaryGoogle`), inspects files the user supplies, and points to the official
   source or open alternatives (microG, Aurora Store, F-Droid, the AOSP WebView).
2. **Open-source components are redistributed under their own licenses**: keep license texts and
   copyright notices (see `third_party/`), offer source for GPL-family code, and do not reuse other
   projects' names or logos (trademarks).
3. **System images (AOSP GSI) are only linked from their official source.** Vendor blobs inside images
   are not ours to resell.
4. **Safety and compatibility stay free.** Inspecting, the install check, installing and the
   single-APK fix are never paid (`Feature.pro == false`, enforced by a test).

## What is monetized

| Channel | What it pays for | Status |
|---|---|---|
| Donations / sponsorship | the manager itself | card hidden until `ProjectLinks.support` has real https links |
| Pro features | batch fix, privileged backends (Shizuku/root), profile sync | `Feature` flags exist; `FreeTier` is the only `Entitlements` so far — no billing or license check yet |
| Install assistant | checks the device (API 24–36, ABI, page size) and links to official downloads | install check and Proprietary card |
| Support / org builds | paid help and custom builds | outside the app |

## To do before release

- Fill in `ProjectLinks.support` (GitHub Sponsors, Boosty, Open Collective...).
- Choose how Pro is sold and verified (offline signed license keys are the simplest for a
  sideloaded app); implement an `Entitlements` for it. Do not rely on Google Play Billing: the app
  is distributed outside Play.
- Add a Privacy Policy and Terms before collecting any payment or contact data.

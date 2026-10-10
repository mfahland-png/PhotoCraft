# Photo Craft (Android)

An on-device photo editor: one-tap auto-enhance plus Lightroom-style controls. Nothing leaves the phone; the app has no internet permission.

## Features

- **Auto enhance**: white balance, auto-levels, exposure, contrast and saturation in one tap
- **Light**: exposure, contrast, highlights, shadows, whites, blacks
- **Color**: temperature, tint, vibrance, saturation
- **Effects**: sharpening, vignette
- **Crop**: 1:1, 4:5, 3:2, 16:9 center crop, rotate 90°, flip
- Live preview, Original/Edited compare, Reset
- Saves a full-size JPEG to `Pictures/PhotoCraft`

Requires Android 10 (API 29) or newer.

## Get an APK

### Option A: Android Studio

1. Install [Android Studio](https://developer.android.com/studio) and open this folder (**File > Open**). Let Gradle sync.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file lands in `app/build/outputs/apk/debug/app-debug.apk`.
3. Copy it to your phone and open it. Allow "Install unknown apps" for your file manager or browser when asked.

Or plug in the phone (USB debugging on) and press **Run** to install directly.

### Option B: GitHub (no Android Studio needed)

1. Create a new GitHub repository and upload everything in this folder, including the `.github` folder.
2. Open the **Actions** tab and run **Build APK** (it also runs on every push).
3. When it finishes, download `PhotoCraft-debug-apk` from the run page, unzip it, and install `app-debug.apk` on your phone.

## Project layout

- `Enhancer.kt`: the auto-enhance algorithm (tune `GAMMA_STRENGTH`, `CONTRAST`, `SATURATION`)
- `Adjuster.kt`: slider adjustments (tone curves, color, vignette, sharpening)
- `Geometry.kt`: rotate, flip, crop
- `Pipeline.kt`: runs geometry, then auto-enhance, then sliders
- `MainActivity.kt`: the Compose UI

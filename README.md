# DualCam Director - Dual Front & Back Camera Android App

A native Android prototype built with **Jetpack Compose** and **Android Camera2 API** that simultaneously utilizes both front and back facing cameras, featuring Picture-in-Picture (PiP), independent 0.6x–10x zoom on both cameras, draggable floating inset during live video recording, and animated fancy frames.

---

## ✨ Features

### 1. Dual Concurrent Front & Back Camera
- **Hardware Concurrency Detection**: Queries `CameraManager.concurrentCameraIds` (Android 11+ / API 30+).
- **Dual Live Sessions**: Streams from both primary and secondary camera hardware sensors concurrently when supported by the device's ISP.
- **Intelligent Director Fallback**: On single-ISP devices or standard emulators where concurrent hardware locking occurs, seamlessly runs the active camera on real hardware while driving the secondary inset camera with a high-fidelity real-time simulated director selfie feed (animated facial tracking, depth grid, studio lighting, and zoom scaling).
- **One-Tap Swap**: Instant swapping between Main and Inset cameras via the swap button or by double-tapping the floating inset.

### 2. Full Camera Features & Dual Zoom (0.6x to 10x)
- **Independent Zoom Controls**: Dedicated zoom controls for **BOTH** Main and Inset cameras.
- **0.6x Ultra-Wide to 10x Super-Zoom**:
  - Quick-preset pills: `0.6x`, `1.0x`, `2.0x`, `5.0x`, `10.0x`.
  - Continuous precision slider (0.6x to 10.0x).
- **Camera Flash / Torch**: Toggle torch mode for the rear camera.
- **Continuous Auto-Focus**: Continuous picture autofocus enabled on active capture sessions.

### 3. Draggable PiP Inset (Active in Video Recording Mode)
- **Freeform Dragging**: Smooth gesture drag anywhere across the screen (`detectDragGestures`).
- **Interactive While Recording**: The inset remains 100% draggable, expandable, and customizable even while video recording is actively running!
- **3-Tier Sizing**: Tap the aspect icon to toggle between **Small (Compact)**, **Medium (Standard)**, and **Large (Expanded)**.
- **Boundary Clamping**: Prevents the inset from sliding off the screen edges.

### 4. 8 Stylized Fancy Frames for the Inset View
Choose and switch between 8 custom visual styles in real-time via the magic wand carousel:
1. **Cyberpunk Neon**: Animated electric cyan & magenta neon gradient border, pulsating glow, sci-fi HUD brackets, and target reticle.
2. **Retro Polaroid**: Authentic instant film photo frame with a white matte finish, bottom chin, "DUAL•CAM" badge, and live timestamp.
3. **Holo Ring**: Circular avatar mask with an animated 360° rotating rainbow chromatic gradient ring and orbiting badge.
4. **Gold Luxury**: Champagne gold metallic bevel with filigree corner ornaments and royal "DIRECTOR" crest badge.
5. **Streamer HUD**: Live streaming broadcast overlay with a pulsating "ON AIR" / "REC" indicator and animated audio VU meter bars.
6. **Comic Pop**: Bold comic ink outline with pop-art halftone offset drop shadow.
7. **Heart Glow**: Vlog-style glowing rose gradient border with a heart badge.
8. **Minimal**: Clean rounded frosted glass border with subtle white glow.

### 5. Video Recording Mode & Live HUD
- **Record Shutter**: Toggle video recording with an animated pulsating record button.
- **Live HUD**: Displays live recording timer (`REC 00:01:45`), active camera hardware status (`CONCURRENT HW ACTIVE` vs `DUAL DIRECTOR SIM`), and camera roles.
- **Dual Snapshot**: Capture instant snapshots with a camera shutter flash animation.

---

## 🛠️ Project Structure

```
app/src/main/java/com/example/dualcamdirector/
├── MainActivity.kt                       # Activity entrypoint hosting the Compose root
├── camera/
│   ├── CameraModels.kt                   # State definitions (CameraRole, DualCamState, LensInfo)
│   ├── DualCameraManager.kt              # Camera2 concurrency manager, capture sessions, zoom ratio
│   └── CameraPreviewViews.kt             # TextureView hardware renderer & realistic simulated feed
├── ui/
│   ├── controls/
│   │   └── CameraControls.kt             # Dual zoom tabs, 0.6x-10x slider, presets, record button, swap
│   ├── dialogs/
│   │   └── CameraInfoDialog.kt           # Hardware specs & concurrency diagnostics modal
│   ├── frames/
│   │   └── FancyFrames.kt                # 8 fancy frame styling containers with animations and badges
│   ├── hud/
│   │   └── CameraHud.kt                  # Top recording timer, hardware status badge, draggable hint
│   ├── inset/
│   │   └── DraggableInsetView.kt         # Floating PiP inset with gesture drag, sizing, double-tap swap
│   └── main/
│       └── DualCamDirectorApp.kt         # Master screen wiring permissions, camera feeds, PiP, controls
```

---

## 🚀 Building & Installing

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The resulting APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install onto a connected device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

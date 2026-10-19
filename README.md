# DualCam Director - Dual Front & Back Camera Android App

A native Android camera app inspired by **Samsung Dual Shot**, built with **Jetpack Compose** and **Android Camera2 API** that simultaneously streams from both front and back facing cameras, featuring movable Picture-in-Picture (PiP), independent 0.6x–10x zoom on both cameras, dynamic inset movement during live video recording, vintage stylized frames, and verified 1:1 undistorted aspect ratios for both photos and videos.

---

## 🏆 Verified Milestones & Quality Highlights

- **✅ Verified Pixel-Perfect Aspect Ratio (Photos & Videos)**:
  - Both photo snapshots and recorded MP4 videos maintain the exact 1:1 aspect ratio shown on screen.
  - Circles remain 100% circular (no vertical stretching or horizontal squeezing).
  - Front-facing selfie camera stream is correctly mirrored and upright.
  - Uses a GPU-to-Canvas display matrix transformation pipeline (`captureTransformedBitmap`) that pulls active display matrices directly from `TextureView` during capture.

- **✅ Live Dual-Camera Composite MP4 Video Recording**:
  - Encodes live composited dual-camera streams into standard **H.264 (`video/avc` HD @ 30 fps, 5 Mbps)** + **AAC audio (`audio/mp4a-latm` @ 128 kbps)**.
  - Video is saved directly to the device's public gallery: **`DCIM/Camera/DUAL_CAM_<timestamp>.mp4`**.
  - Dynamic live PiP: the user can freely drag, reposition, and resize the inset frame while video recording is actively running—all movements are recorded live into the final video.
  - Monotonic timestamp pacing ensures smooth, stutter-free 30 fps playback on all video players.

- **✅ High-Fidelity Dual-Shot Photo Compositing**:
  - Full-resolution photo snapshot compositing combining the fullscreen main camera, the draggable inset camera, and the active frame.
  - Automatically saved to **`DCIM/Camera/DUAL_CAM_<timestamp>.jpg`** and indexed via `MediaScannerConnection`.
  - Non-blocking background execution on `Dispatchers.IO`: UI never hangs or freezes.
  - Zero-leak memory management: source and intermediate bitmaps are explicitly recycled immediately after compression.

- **✅ Samsung Dual Shot Controls & Layout**:
  - **Portrait Dock**: Bottom controls featuring the red video record button, silver pill photo shutter, dual zoom selector (0.6x–10x), and live gallery thumbnail.
  - **Landscape Dock**: Samsung Dual Shot ergonomics—controls dock on the right side of the screen with a floating zoom pill and quick action bar (swap cameras, flash torch) on top.

- **✅ Live Interactive Gallery Thumbnail Icon**:
  - Floating circular thumbnail icon on screen that immediately updates with a pop animation showing the latest captured photo or video frame.
  - One-tap launch: clicking the thumbnail opens the saved photo in Samsung Gallery / Google Photos, or plays the video in the default video player.

- **✅ Vintage Postage Stamp Frame (Default)**:
  - Scalloped perforated teeth cut along all 4 edges.
  - Top-left postal cancellation wavy ink lines and circular date postmark (`DUAL SHOT <date>`).
  - Symmetrical inner photo margins preserving the user's face without occlusion.

---

## ✨ Full Feature Overview

### 1. Dual Concurrent Front & Back Camera
- **Hardware Concurrency Detection**: Automatically checks `CameraManager.concurrentCameraIds` (Android 11+ / API 30+).
- **Dual Live Hardware Feeds**: Streams from both front and back cameras simultaneously when supported by the device chipset.
- **Intelligent Director Fallback**: On single-ISP devices or standard emulators where concurrent hardware locking occurs, runs the active camera on real hardware while driving the secondary inset with a real-time simulated director selfie feed (animated facial tracking, depth grid, studio lighting, and zoom scaling).
- **One-Tap Swap**: Instantly switch roles between Main and Inset cameras via the UI swap button or by double-tapping the floating inset.

### 2. Independent Dual Zoom (0.6x to 10x)
- Dedicated zoom controls for **BOTH** Main and Inset cameras independently.
- Quick-preset pills: `0.6x`, `1.0x`, `2.0x`, `5.0x`, `10.0x`.
- Continuous precision slider (0.6x to 10.0x).
- Auto-focus (continuous video/picture) and rear camera torch flash.

### 3. Draggable PiP Inset
- Smooth gesture dragging anywhere across the screen (`detectDragGestures`).
- Interactive while recording: drag or resize the inset while video recording is running.
- 3-tier sizing: Compact (Small), Standard (Medium), and Expanded (Large).
- Edge clamping to prevent slipping off-screen.

### 4. Stylized Fancy Frames
Switch between frames in real time:
1. **Vintage Postage Stamp** (Default): Perforated edges, classic ivory paper base, top-left cancellation postmark.
2. **Minimal**: Clean rounded border with frosted glass styling.
3. **Retro Polaroid**: Instant film frame with bottom chin and "DUAL DIRECTOR" imprint.
4. **Holo Ring**: Circular avatar mask with an animated 360° rotating rainbow chromatic ring.
5. **Cyberpunk Neon**: Glowing neon gradient border with sci-fi HUD brackets.
6. **Gold Luxury**: Champagne gold metallic bevel with filigree ornaments.
7. **Streamer HUD**: Broadcast overlay with live "ON AIR" / "REC" indicator and audio VU bars.
8. **Comic Pop**: Comic book ink outline with pop-art styling.
9. **Heart Glow**: Vlog-style glowing rose gradient border with heart badge.

---

## 🛠️ Architecture & Technical Notes

```
app/src/main/java/com/example/dualcamdirector/
├── MainActivity.kt                       # Single activity Compose host
├── camera/
│   ├── CameraModels.kt                   # State definitions (CameraRole, DualCamState, LensInfo)
│   ├── DualCameraManager.kt              # Camera2 dual-stream manager, concurrent sessions, zoom ratio
│   ├── CameraPreviewViews.kt             # TextureView hardware renderer with AOSP orientation transform
│   ├── DualCameraCaptureHelper.kt        # captureTransformedBitmap, compositeDualShot, MediaStore saving
│   └── DualCameraVideoRecorder.kt        # MediaCodec H.264 + MediaMuxer composite video encoder pipeline
├── ui/
│   ├── controls/
│   │   └── CameraControls.kt             # Portrait & landscape docks, shutter buttons, gallery thumbnail
│   ├── dialogs/
│   │   └── CameraInfoDialog.kt           # Hardware diagnostic specs modal
│   ├── frames/
│   │   └── FancyFrames.kt                # 8 fancy frames + Postage Stamp implementation
│   ├── hud/
│   │   └── CameraHud.kt                  # Top recording timer, hardware status badge, quick actions
│   ├── inset/
│   │   └── DraggableInsetView.kt         # Floating PiP inset with gesture drag and bounds reporting
│   └── main/
│       └── DualCamDirectorApp.kt         # Master app coordinator, lifecycle, snapshot & video wiring
```

### Key Technical Solutions
1. **Display Matrix Baking (`captureTransformedBitmap`)**:
   `TextureView.getBitmap()` returns raw, unscaled sensor buffers that ignore display transformations. We query `tv.getTransform(matrix)` and render through the matrix onto the destination canvas. This guarantees that center-crop aspect ratio scaling, front-camera horizontal mirroring, and rotation calibration are baked into the saved output.
2. **Asynchronous Non-Blocking IO**:
   All heavy bitmap compositing, JPEG encoding, and MediaStore disk I/O run on `Dispatchers.IO`, keeping the Android Main Looper responsive and eliminating UI deadlocks.
3. **Zero-Allocation Bitmap Recycling**:
   `DualCameraVideoRecorder` uses pre-allocated reusable bitmap buffers to prevent Garbage Collection pauses during live 30 fps video encoding.
4. **Lifecycle-Aware Auto-Recovery (`ON_PAUSE` / `ON_RESUME`)**:
   Cleanly releases Camera2 devices, sessions, and surfaces when the app is paused (e.g., viewing gallery, switching apps, receiving calls). Upon resuming, validates `SurfaceTexture` health and safely re-opens dual camera streams with auto-retry logic for busy camera HAL drivers, preventing black screens or app freezing.

---

## 🚀 Building & Installing

### Output APK:
- **`app/build/outputs/apk/debug/DualCamDirector-v1.1.apk`**
- **`app/build/outputs/apk/debug/DualCamDirector-v1.1-debug.apk`**

### Build with Gradle:
```bash
./gradlew assembleDebug
```

### Install on Device via ADB:
```bash
adb install -r "app/build/outputs/apk/debug/DualCamDirector-v1.1.apk"
```

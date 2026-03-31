# EchoNav Troubleshooting Guide

---

## 🔴 Build Errors

### `FileNotFoundException: efficientdet_lite0.tflite`
**Cause:** The model file is missing from `app/src/main/assets/`.  
**Fix:**
```bash
./download_model.sh
```
Then sync Gradle: `File → Sync Project with Gradle Files`.

---

### `Cannot resolve symbol 'ObjectDetector'`
**Cause:** Gradle hasn't downloaded the TFLite Task Vision dependency yet.  
**Fix:**
1. Ensure you have internet access during first build.
2. `File → Sync Project with Gradle Files`
3. `Build → Clean Project` → `Build → Rebuild Project`

---

### `Unsupported class file major version XX`
**Cause:** Your JDK version is below 17.  
**Fix:** In Android Studio:  
`File → Project Structure → SDK Location → Gradle JDK` → select JDK 17+.

---

### `resource mipmap/ic_launcher not found`
**Cause:** The adaptive icon XML references a missing raster PNG.  
**Fix:** Android Studio will generate these during first sync. If not:  
Right-click `res/` → `New → Image Asset` → set Foreground Layer to the vector.

---

## 🟡 Runtime Issues

### App opens but no voice on launch
**Cause:** TTS engine not installed, or language pack missing.  
**Fix:**
1. `Settings → General Management → Language & Input → Text-to-speech`
2. Tap the gear next to your TTS engine
3. Install the English language data
4. Restart the app

---

### "Detection started" is spoken but nothing else
**Cause A:** Model loaded but all detections are below the confidence threshold.  
→ Try pointing at a person or a chair from about 1–2 meters away.

**Cause B:** `FeedbackFilterUseCase` cooldown is suppressing repeats.  
→ Move the camera to a new scene, or long-press to reset mode.

**Cause C:** You're in **Silent mode**.  
→ Long-press the screen to cycle to Explore mode. TTS will announce the mode switch.

---

### Haptic feedback not working
**Cause A:** Device is in Do Not Disturb mode — this can suppress vibration on some OEMs.  
**Fix:** Toggle DND off, or check `Settings → Sounds → Vibration`.

**Cause B:** Low battery mode on some devices silences vibration.  
**Fix:** Plug in the device while testing.

---

### Very high latency (> 300ms per frame)
**Cause A:** NNAPI not available on this SoC — falling back to CPU.  
**Diagnostic:** Check Logcat for `"ObjectDetector initialized with CPU fallback"`.

**Fix:** This is expected on some devices. CPU inference at 150–200ms is still usable
for pedestrian-speed navigation. The `STRATEGY_KEEP_ONLY_LATEST` in ImageAnalysis
ensures the most recent frame is always processed — no frame queue backlog.

**Cause B:** Device is thermally throttled.  
**Fix:** Let the device cool. Thermal throttling typically halves CPU/GPU frequency.

---

### Camera preview is black but no error
**Cause:** Another app has the camera locked (e.g., a background video call).  
**Fix:** Close other apps using the camera and relaunch EchoNav.

---

### `java.lang.IllegalStateException: CameraX is already initialized`
**Cause:** Rapid configuration changes (e.g., screen rotate) before CameraX tears down.  
**Fix:** The `android:screenOrientation="portrait"` in the Manifest prevents rotation.
If you removed that flag, add it back.

---

## 🟢 Logcat Reference

Filter by these tags in Android Studio's Logcat to isolate issues:

| Tag | What it shows |
|-----|--------------|
| `ObjectDetectorRepo` | Model init success/failure, inference errors |
| `SpeechService` | TTS init status, utterance errors |
| `HapticService` | Vibration errors (rare) |
| `EchoNavViewModel` | Detection start/stop, announcements, mode switches |
| `CameraPreviewView` | CameraX bind success/failure |
| `ImageAnalyzer` | Per-frame errors |
| `ModelBenchmark` | Latency results (if benchmark is called) |

**Quick filter command (adb):**
```bash
adb logcat -s ObjectDetectorRepo SpeechService EchoNavViewModel CameraPreviewView
```

---

## 📱 Device-Specific Notes

| Device / SoC | Notes |
|-------------|-------|
| Snapdragon 8 Gen 1+ | NNAPI works. Expect ~40ms inference. |
| Snapdragon 6xx / 7xx | NNAPI usually works. ~60–100ms. |
| MediaTek Dimensity | NNAPI may not be available. CPU fallback ~100–180ms. |
| Exynos 990 and below | Mixed NNAPI support. Test with benchmark. |
| Emulator (x86_64) | NNAPI unavailable. CPU only. ~200–400ms. Usable for UI testing only. |

---

## 🧪 Testing Without a Blind User

To verify the audio-first UX:
1. Put on headphones or earbuds.
2. Cover the screen with a sheet of paper (simulate no visual feedback).
3. Walk around a room and verify the app guides you by voice alone.
4. Test all three modes: Explore, Focus, Silent.
5. Approach an object quickly — verify "Caution" and strong haptic fire before contact.

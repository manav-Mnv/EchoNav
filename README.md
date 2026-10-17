# EchoNav — Offline AI Navigation Companion for Blind Users

![Status](https://img.shields.io/badge/status-prototype-0969da?style=flat-square)

> *"Your ears are ahead of your eyes. EchoNav makes sure they stay that way."*

EchoNav is a fully offline Android app that uses real-time computer vision to detect nearby objects and communicate them via voice and haptic feedback — designed for blind and visually impaired users who need ambient situational awareness.

---

## 🚀 Quick Start

### 1. Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog / Iguana / Meerkat (2023.1+) |
| JDK | 17+ |
| Android device | API 26+ (Android 8.0+) |
| `curl` or `wget` | For model download |

### 2. Download the TFLite model

```bash
cd EchoNav
chmod +x download_model.sh
./download_model.sh
```

This places two files in `app/src/main/assets/`:
- `efficientdet_lite0.tflite` — 4.4MB quantized object detection model
- `coco_labels.txt` — 80 COCO class labels

> **Windows users:** Download manually:
> - Model: https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite0_detection_metadata_1.tflite
> - Save as `app/src/main/assets/efficientdet_lite0.tflite`
> - Create `app/src/main/assets/coco_labels.txt` with one label per line (see `download_model.sh` for content)

### 3. Open in Android Studio

```
File → Open → Select the EchoNav/ root folder
```

### 4. Sync & Build

```
File → Sync Project with Gradle Files
Run → Run 'app'  (or Shift+F10)
```

### 5. Grant Permissions

When prompted, allow **Camera** access. That's the only permission needed.

---

## 🎯 How to Use

| Gesture | Action |
|---------|--------|
| **Tap anywhere** | Start / Stop detection |
| **Long press** | Cycle through modes: Explore → Focus → Silent |

### Modes

| Mode | Behaviour |
|------|-----------|
| **Explore** | Announces all detected objects. Best for open spaces. |
| **Focus** | Announces only the closest object. Best for crowds. |
| **Silent** | Voice off, haptic only. Best for meetings / quiet spaces. |

### Voice Examples

```
"Person ahead"
"Chair on your left"
"Car on your right, nearby"
"Person ahead, very close. Caution."
```

### Haptic Patterns

| Pattern | Meaning |
|---------|---------|
| Three strong pulses | Danger — object very close |
| Single short buzz | General awareness |

---

## 🏗️ Architecture

```
com.echonav/
│
├── data/
│   ├── model/
│   │   ├── DetectedObject.kt       ← Domain data class (label, bbox, confidence)
│   │   ├── DetectionResult.kt      ← Inference output wrapper
│   │   └── AppMode.kt              ← Explore / Focus / Silent enum
│   │
│   └── repository/
│       └── ObjectDetectorRepository.kt  ← TFLite inference engine
│
├── domain/
│   └── usecase/
│       ├── DirectionUseCase.kt          ← left / ahead / right from bbox
│       ├── DistanceUseCase.kt           ← very close / nearby / ahead from area
│       ├── FeedbackFilterUseCase.kt     ← cooldown + change detection
│       └── BuildAnnouncementUseCase.kt  ← composes natural speech sentence
│
├── services/
│   ├── SpeechService.kt            ← TextToSpeech wrapper
│   └── HapticService.kt            ← Vibrator wrapper
│
├── presentation/
│   ├── ui/
│   │   ├── MainActivity.kt         ← Entry point, permission handling
│   │   ├── EchoNavScreen.kt        ← Main Composable screen
│   │   ├── CameraPreviewView.kt    ← CameraX + Composable bridge
│   │   ├── EchoNavImageAnalyzer.kt ← ImageAnalysis.Analyzer impl
│   │   ├── PermissionScreen.kt     ← Camera permission prompt
│   │   └── theme/
│   │       └── EchoNavTheme.kt     ← Dark Material3 theme
│   │
│   └── viewmodel/
│       ├── EchoNavUiState.kt       ← Immutable UI state snapshot
│       └── EchoNavViewModel.kt     ← Central orchestrator
│
└── EchoNavApplication.kt           ← Application class
```

### Data flow

```
Camera frame
    ↓  (CameraX ImageAnalysis)
EchoNavImageAnalyzer.analyze()
    ↓  (Bitmap + rotation)
EchoNavViewModel.analyzeFrame()
    ↓  (background coroutine)
ObjectDetectorRepository.detect()    ← TFLite inference (NNAPI / CPU)
    ↓  List<DetectedObject>
DistanceUseCase / DirectionUseCase
    ↓  distance + direction strings
FeedbackFilterUseCase.shouldAnnounce()
    ↓  (cooldown gate)
BuildAnnouncementUseCase.execute()
    ↓  "Person ahead, very close. Caution."
SpeechService.speak()  +  HapticService.danger()
    ↓
User hears and feels the feedback
```

---

## 🧠 ML Model Details

**EfficientDet-Lite0** (quantized INT8)

| Property | Value |
|----------|-------|
| Size | ~4.4 MB |
| Input | 320 × 320 RGB |
| Inference (NNAPI) | ~40–80ms |
| Inference (CPU 4-thread) | ~80–150ms |
| Classes | 80 (COCO) |
| Confidence threshold | 0.45 |

The model uses the **TFLite Task Vision** library's `ObjectDetector` — a high-level API that handles preprocessing, inference, and postprocessing internally. No manual tensor manipulation needed.

**NNAPI acceleration** is attempted first. If the device doesn't have a neural processing unit, it falls back transparently to 4-thread CPU inference.

---

## ⚡ Performance & Battery

| Optimization | Implementation |
|-------------|---------------|
| Drop slow frames | `STRATEGY_KEEP_ONLY_LATEST` in ImageAnalysis |
| Avoid feedback spam | 2-second cooldown per label |
| Minimal UI redraws | StateFlow only emits on meaningful change |
| OLED-friendly | Near-black background (#0A0A0A) |
| Single analysis thread | `Executors.newSingleThreadExecutor()` |

Expected battery impact: ~8–12% per hour on mid-range hardware with NNAPI enabled.

---

## 🛠️ Customisation

### Change detection confidence
In `ObjectDetectorRepository.kt`:
```kotlin
private const val MIN_CONFIDENCE = 0.45f  // Lower = more detections, more noise
```

### Change announcement cooldown
In `FeedbackFilterUseCase.kt`:
```kotlin
private const val COOLDOWN_MS = 2_000L    // Milliseconds between repeats
```

### Add more object labels to announce
In `ObjectDetectorRepository.kt`:
```kotlin
private val ALLOWED_LABELS = setOf(
    "person", "chair", "door",   // existing
    "bicycle", "stairs"           // add any COCO label here
)
```

### Change distance thresholds
In `DistanceUseCase.kt`:
```kotlin
const val THRESHOLD_VERY_CLOSE = 0.25f   // bbox fills >25% of frame
const val THRESHOLD_NEARBY     = 0.08f   // bbox fills >8% of frame
```

### Swap to a different TFLite model
1. Place the new `.tflite` file in `app/src/main/assets/`
2. Update `MODEL_FILE` constant in `ObjectDetectorRepository.kt`
3. Adjust `INPUT_SIZE` if the model uses a different resolution

---

## 🔒 Permissions

| Permission | Why |
|-----------|-----|
| `CAMERA` | Required for object detection |
| `VIBRATE` | Haptic feedback |
| `WAKE_LOCK` | Prevents screen sleep during navigation |

No internet permission is declared. The app is **100% offline**.

---

## 📱 Accessibility

- All interactive elements have `contentDescription` for TalkBack
- Full-screen tap target (no small buttons to find)
- Voice-led UX — user never needs to look at the screen
- TTS rate and pitch tuned for clarity without harshness
- Danger interrupts current speech immediately (`QUEUE_FLUSH`)

---

## 🔮 Future Phases

| Phase | Feature |
|-------|---------|
| v1.1 | Depth estimation using dual-camera or lidar |
| v1.2 | Scene description mode ("Describe my surroundings") |
| v1.3 | Landmark recognition (doors, elevator buttons) |
| v1.4 | Custom wake word ("Hey EchoNav, what's ahead?") |
| v1.5 | Bluetooth earpiece integration |

---

## 📄 License

MIT License. Free to use, modify, and distribute.

---

*Built with care for the community that navigates the world by sound.*

# EchoNav Architecture Deep-Dive

## Guiding Principle

> "The user never looks at the screen. Every design decision flows from that."

This means:
- **No UI-dependent features** — mode changes, start/stop, all happen via gesture + voice.
- **Zero tolerance for crashes** — a crash mid-navigation is a safety event.
- **Latency over accuracy** — a 60ms slightly-less-accurate detection is more useful than a 200ms perfect one.

---

## Layer Map

```
┌─────────────────────────────────────────────────────────────────┐
│  PRESENTATION LAYER                                             │
│  ┌─────────────────┐   ┌──────────────────────────────────┐    │
│  │  MainActivity   │   │  Jetpack Compose UI              │    │
│  │  (permission,   │   │  EchoNavScreen                   │    │
│  │   lifecycle)    │   │  CameraPreviewView               │    │
│  └────────┬────────┘   │  PermissionScreen                │    │
│           │            └──────────────┬───────────────────┘    │
│           └──────────────────────────┼────────────────────┐    │
│                                      ↓                    ↓    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  VIEWMODEL LAYER                                          │  │
│  │  EchoNavViewModel                                         │  │
│  │  - Owns UI state (StateFlow<EchoNavUiState>)             │  │
│  │  - Orchestrates: inference → filter → speak → haptic     │  │
│  │  - Survives config changes                               │  │
│  └──────────┬──────────────────────────────────────────────┘  │
└─────────────┼───────────────────────────────────────────────────┘
              │
┌─────────────┼───────────────────────────────────────────────────┐
│  DOMAIN LAYER                                                   │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │  DirectionUseCase    │  │  DistanceUseCase                │  │
│  │  (bbox → direction)  │  │  (bbox area → distance label)   │  │
│  └──────────────────────┘  └─────────────────────────────────┘  │
│  ┌──────────────────────┐  ┌─────────────────────────────────┐  │
│  │  FeedbackFilterUseCase│  │  BuildAnnouncementUseCase       │  │
│  │  (cooldown + change) │  │  (composes speech string)       │  │
│  └──────────────────────┘  └─────────────────────────────────┘  │
└─────────────┼───────────────────────────────────────────────────┘
              │
┌─────────────┼───────────────────────────────────────────────────┐
│  DATA + SERVICES LAYER                                          │
│  ┌──────────────────────────┐  ┌──────────────────────────────┐ │
│  │  ObjectDetectorRepository│  │  SpeechService               │ │
│  │  (TFLite Task Vision)    │  │  (Android TTS)               │ │
│  └──────────────────────────┘  └──────────────────────────────┘ │
│                                ┌──────────────────────────────┐ │
│                                │  HapticService               │ │
│                                │  (Vibrator API 26+)          │ │
│                                └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Threading Model

```
Main Thread                Background (IO)           Analysis Executor
    │                           │                          │
    │ viewModelScope.launch     │                          │
    │ ──────────────────────►   │                          │
    │                      TFLite init                     │
    │                      TTS init                        │
    │                           │                          │
    │                           │◄──────────────────────── │
    │                           │  analyzeFrame(bitmap)    │
    │                           │  (CameraX calls this     │
    │                           │   on analysis executor)  │
    │                           │                          │
    │                     inference()                      │
    │                     filter()                         │
    │                     buildAnnouncement()              │
    │                           │                          │
    │◄──────────────────────────│                          │
    │  _uiState.update()        │                          │
    │  speechService.speak()    │                          │
    │  hapticService.danger()   │                          │
```

Key points:
- `analyzeFrame()` is called from CameraX's analysis executor (a single background thread).
- The ViewModel's coroutine scope handles TFLite init on `Dispatchers.IO`.
- UI state updates via `_uiState.update {}` — thread-safe because `MutableStateFlow` uses atomic CAS.
- `SpeechService.speak()` and `HapticService` are thread-safe (Android TTS handles its own queue).

---

## FeedbackFilterUseCase — State Machine

The filter is the most critical piece for usability. Here's how it decides:

```
shouldAnnounce(obj, distance)
        │
        ├── label NOT in lastSeenMap?
        │         └── YES → ANNOUNCE + record (label, now, distance)
        │
        ├── now - lastSeen.timestamp < COOLDOWN_MS (2s)?
        │         └── YES → SUPPRESS
        │
        ├── lastSeen.distanceCategory != distance?
        │         └── YES → ANNOUNCE (object is approaching/receding)
        │
        ├── now - lastSeen.timestamp > FORGET_WINDOW_MS (8s)?
        │         └── YES → ANNOUNCE (object reappeared after absence)
        │
        └── else → SUPPRESS
```

Why distance-change triggers re-announcement even within cooldown:
- A person standing still 2 meters away is "nearby" — announce once, then silence.
- If they walk toward you, the bounding box grows → distance changes to "very close" → re-announce immediately.
- This is the key safety mechanism: approaching hazards always speak, even at high frequency.

---

## Distance Estimation

We use **bounding box area as a depth proxy**. This is a monocular depth heuristic:

```
Normalized area = bbox.width × bbox.height   (both in [0..1] range)

area > 0.25  →  "very close"    (~0.5–1.0m for a person)
area > 0.08  →  "nearby"        (~1.0–2.5m for a person)
area ≤ 0.08  →  "ahead"         (> 2.5m)
```

**Limitations:**
- Works poorly for objects of vastly different real-world sizes (a bicycle vs. a bus both trigger "very close" at different actual distances).
- Calibrated for person-sized objects at walking speeds.
- Does NOT account for camera focal length variation — consistent on a fixed device.

**Future improvement:** Use Android Depth API (Pixel 4+) or ARCore depth mesh for metric depth estimates.

---

## TTS Design Decisions

### `QUEUE_FLUSH` vs `QUEUE_ADD`
- Danger announcements (`isDanger = true`) use `QUEUE_FLUSH` — they interrupt whatever is playing. A "Person very close. Caution." should never wait in queue behind "Chair on your left."
- Routine announcements use `QUEUE_ADD` — they wait their turn, preventing speech overlap.

### Speech rate (1.05×)
- Slightly faster than the 1.0× default.
- Faster TTS = user hears the warning sooner = more reaction time.
- Tested to not feel rushed while still being clearly intelligible.

### Pitch (0.92)
- Slightly lower than default.
- Perceived as calmer and less alarming — important for an app used constantly.

---

## CameraX Configuration

```kotlin
ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()
```

`STRATEGY_KEEP_ONLY_LATEST` is the most important setting:
- If inference takes 120ms and the camera runs at 30fps (33ms/frame), there are ~3 frames waiting.
- Without `KEEP_ONLY_LATEST`, those 3 frames would queue up and create a 360ms lag.
- With `KEEP_ONLY_LATEST`, stale frames are **dropped** and inference always runs on the freshest frame.
- Result: latency stays constant at ~inference_time, regardless of camera FPS.

`OUTPUT_IMAGE_FORMAT_RGBA_8888` avoids the YUV→RGB conversion that `toBitmap()` performs internally in older CameraX versions, saving ~5–10ms per frame.

---

## AppMode Design

Modes are expressed as a cyclic enum:

```kotlin
enum class AppMode {
    EXPLORE, FOCUS, SILENT;
    fun next(): AppMode = ...
}
```

This means adding a new mode in future is a one-line change to `next()`. The ViewModel selects object candidates based on mode:

```kotlin
val candidates = when (mode) {
    EXPLORE → all detected objects
    FOCUS   → only the single largest (closest) object
    SILENT  → empty list (no voice), but haptic still fires for danger
}
```

SILENT mode still running the camera + TFLite matters: the user might be in a meeting and want silent navigation, but still needs the danger pulse if they're about to walk into a wall.

---

## What This App Intentionally Does NOT Do

| Feature | Why excluded |
|---------|-------------|
| GPS navigation | Requires internet; redundant with existing nav apps |
| Face recognition | Privacy concern; legally fraught |
| OCR / text reading | Separate use case — would need a different model |
| Cloud fallback | Hard constraint: 100% offline |
| Background service | Requires foreground service notification; adds complexity |
| Wearable integration | Out of scope for v1 |

All of these are valid v2+ features — the architecture (ViewModel + UseCase separation) makes them easy to add without touching the core pipeline.

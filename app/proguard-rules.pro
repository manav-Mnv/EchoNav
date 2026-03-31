# ── TensorFlow Lite ─────────────────────────────────────────────────────────
# Keep all TFLite classes from being stripped or renamed during minification.
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# TFLite GPU delegate
-keep class org.tensorflow.lite.gpu.** { *; }

# ── CameraX ─────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# General Android
-keepattributes Signature
-keepattributes Exceptions

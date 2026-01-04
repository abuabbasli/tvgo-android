# =============================================================================
# ProGuard/R8 Rules for Android TV IPTV App
# Optimized for maximum size reduction while preserving functionality
# =============================================================================

# =============================================================================
# General Optimizations
# =============================================================================

# Optimize aggressively
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove System.out.println
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# =============================================================================
# Kotlin
# =============================================================================

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# =============================================================================
# Jetpack Compose
# =============================================================================

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# TV Compose
-keep class androidx.tv.** { *; }

# =============================================================================
# Retrofit & OkHttp & Gson
# =============================================================================

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep API models (they use Gson serialization)
-keep class com.example.androidtviptvapp.data.api.** { *; }
-keep class com.example.androidtviptvapp.data.Channel { *; }
-keep class com.example.androidtviptvapp.data.Movie { *; }
-keep class com.example.androidtviptvapp.data.Category { *; }

# =============================================================================
# Media3 / ExoPlayer
# =============================================================================

-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer extension libraries
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# =============================================================================
# Coil Image Loading
# =============================================================================

-keep class coil.** { *; }
-keep class coil.compose.** { *; }
-dontwarn coil.**

# SVG decoder
-keep class coil.decode.SvgDecoder { *; }

# =============================================================================
# Navigation
# =============================================================================

-keep class androidx.navigation.** { *; }
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# =============================================================================
# ZXing (QR Code)
# =============================================================================

-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# =============================================================================
# Android Components
# =============================================================================

# Keep Activities
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# View constructors are accessed by reflection
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# =============================================================================
# Remove unused warnings
# =============================================================================

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**

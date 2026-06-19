# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-optimizationpasses 5
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose

# ============================================================================
# Project Specific Keep Rules
# ============================================================================

# Safely keep your launcher MainActivity without strict class matching syntax
-keep public class * extends android.app.Activity {
    public *;
}

# Keep all core Android framework components intact
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.view.View

# ============================================================================
# Jetpack Compose Multiplatform Specific Rules
# ============================================================================

# Prevent Compose runtime attributes from being stripped
-keepattributes TypedData,LineNumberTable,SourceFile

# Keep Compose internal compiler markers safely
-keep class androidx.compose.runtime.RecomposeScopeImpl { *; }
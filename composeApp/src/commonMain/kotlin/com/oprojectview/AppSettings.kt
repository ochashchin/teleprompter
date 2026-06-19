package com.oprojectview

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings

// ── CompositionLocal for Settings ─────────────────────────────────────────────
//
// Why: Settings() no-arg requires a platform Context on Android.
// Calling it at file-top-level or via lazy{} still crashes the Compose
// preview renderer because the preview classloader has no Application context.
//
// Solution: provide Settings via CompositionLocal.
//   • Real app  → App.kt provides Settings() with a real context already set up.
//   • Previews  → get NoOpSettings() automatically via the default value,
//                 which never touches SharedPreferences / NSUserDefaults.

val LocalSettings = compositionLocalOf<Settings> { NoOpSettings() }

// ── No-op Settings for previews ───────────────────────────────────────────────
// Returns empty/default values for every read; silently drops every write.
// Keeps previews fast and context-free.
private class NoOpSettings : Settings {
    override val keys: Set<String>              get() = emptySet()
    override val size: Int                      get() = 0
    override fun clear()                                = Unit
    override fun remove(key: String)                    = Unit
    override fun hasKey(key: String): Boolean           = false
    override fun putInt(key: String, value: Int)        = Unit
    override fun getInt(key: String, defaultValue: Int) = defaultValue
    override fun getIntOrNull(key: String): Int?        = null
    override fun putLong(key: String, value: Long)      = Unit
    override fun getLong(key: String, defaultValue: Long) = defaultValue
    override fun getLongOrNull(key: String): Long?      = null
    override fun putString(key: String, value: String)  = Unit
    override fun getString(key: String, defaultValue: String) = defaultValue
    override fun getStringOrNull(key: String): String?  = null
    override fun putFloat(key: String, value: Float)    = Unit
    override fun getFloat(key: String, defaultValue: Float) = defaultValue
    override fun getFloatOrNull(key: String): Float?    = null
    override fun putDouble(key: String, value: Double)  = Unit
    override fun getDouble(key: String, defaultValue: Double) = defaultValue
    override fun getDoubleOrNull(key: String): Double?  = null
    override fun putBoolean(key: String, value: Boolean) = Unit
    override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = null
}

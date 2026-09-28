package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    LIGHT,
    DARK,
    AMOLED
}

enum class AccentColor(val displayName: String, val hex: Long) {
    GOLD("Vegas Gold", 0xFFFFD700),
    EMERALD("Emerald Felt", 0xFF10B981),
    RUBY("Royal Ruby", 0xFFEF4444),
    SAPPHIRE("Sapphire Blue", 0xFF3B82F6),
    AMBER("Warm Amber", 0xFFF59E0B)
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.GOLD,
    val masterSound: Boolean = true,
    val actionSound: Boolean = true,
    val winnerMusic: Boolean = true,
    val soundVolume: Float = 0.8f,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: String = "Medium", // Light, Medium, Strong
    val animationsMode: String = "Smooth", // Smooth, Reduced
    val confirmLock: Boolean = true,
    val confirmNewGame: Boolean = true,
    val emergencyEdit: Boolean = false,
    val prizeEnabled: Boolean = false,
    val prizeAmount: Int = 500
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeStr = prefs.getString("themeMode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val themeMode = try { AppThemeMode.valueOf(themeStr) } catch (_: Exception) { AppThemeMode.DARK }

        val accentStr = prefs.getString("accentColor", AccentColor.GOLD.name) ?: AccentColor.GOLD.name
        val accentColor = try { AccentColor.valueOf(accentStr) } catch (_: Exception) { AccentColor.GOLD }

        return AppSettings(
            themeMode = themeMode,
            accentColor = accentColor,
            masterSound = prefs.getBoolean("masterSound", true),
            actionSound = prefs.getBoolean("actionSound", true),
            winnerMusic = prefs.getBoolean("winnerMusic", true),
            soundVolume = prefs.getFloat("soundVolume", 0.8f),
            hapticsEnabled = prefs.getBoolean("hapticsEnabled", true),
            hapticStrength = prefs.getString("hapticStrength", "Medium") ?: "Medium",
            animationsMode = prefs.getString("animationsMode", "Smooth") ?: "Smooth",
            confirmLock = prefs.getBoolean("confirmLock", true),
            confirmNewGame = prefs.getBoolean("confirmNewGame", true),
            emergencyEdit = prefs.getBoolean("emergencyEdit", false),
            prizeEnabled = prefs.getBoolean("prizeEnabled", false),
            prizeAmount = prefs.getInt("prizeAmount", 500)
        )
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("themeMode", mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun toggleDarkLight() {
        val next = if (_settings.value.themeMode == AppThemeMode.LIGHT) AppThemeMode.DARK else AppThemeMode.LIGHT
        updateThemeMode(next)
    }

    fun updateAccentColor(accent: AccentColor) {
        prefs.edit().putString("accentColor", accent.name).apply()
        _settings.value = _settings.value.copy(accentColor = accent)
    }

    fun updateSoundSettings(master: Boolean, action: Boolean, winner: Boolean, volume: Float) {
        prefs.edit()
            .putBoolean("masterSound", master)
            .putBoolean("actionSound", action)
            .putBoolean("winnerMusic", winner)
            .putFloat("soundVolume", volume)
            .apply()
        _settings.value = _settings.value.copy(
            masterSound = master,
            actionSound = action,
            winnerMusic = winner,
            soundVolume = volume
        )
    }

    fun updateHaptics(enabled: Boolean, strength: String) {
        prefs.edit()
            .putBoolean("hapticsEnabled", enabled)
            .putString("hapticStrength", strength)
            .apply()
        _settings.value = _settings.value.copy(
            hapticsEnabled = enabled,
            hapticStrength = strength
        )
    }

    fun updateAnimations(mode: String) {
        prefs.edit().putString("animationsMode", mode).apply()
        _settings.value = _settings.value.copy(animationsMode = mode)
    }

    fun updateConfirmations(confirmLock: Boolean, confirmNewGame: Boolean, emergencyEdit: Boolean) {
        prefs.edit()
            .putBoolean("confirmLock", confirmLock)
            .putBoolean("confirmNewGame", confirmNewGame)
            .putBoolean("emergencyEdit", emergencyEdit)
            .apply()
        _settings.value = _settings.value.copy(
            confirmLock = confirmLock,
            confirmNewGame = confirmNewGame,
            emergencyEdit = emergencyEdit
        )
    }

    fun updatePrize(enabled: Boolean, amount: Int) {
        prefs.edit()
            .putBoolean("prizeEnabled", enabled)
            .putInt("prizeAmount", amount)
            .apply()
        _settings.value = _settings.value.copy(
            prizeEnabled = enabled,
            prizeAmount = amount
        )
    }

    fun resetSettings() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }
}

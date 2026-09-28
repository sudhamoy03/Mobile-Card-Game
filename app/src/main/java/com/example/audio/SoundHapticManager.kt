package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

class SoundHapticManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)

    var isMasterSoundEnabled: Boolean = true
    var isActionSoundEnabled: Boolean = true
    var isWinnerMusicEnabled: Boolean = true
    var soundVolume: Float = 0.8f

    var isHapticsEnabled: Boolean = true
    var hapticStrength: String = "Medium" // "Light", "Medium", "Strong"

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playShuffleSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            // Rapid soft white-noise / rustle bursts
            playToneSweep(startFreq = 400f, endFreq = 200f, durationMs = 150, volume = 0.4f * soundVolume)
            playToneSweep(startFreq = 350f, endFreq = 180f, durationMs = 150, volume = 0.5f * soundVolume)
        }
    }

    fun playCardSlideSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            playToneSweep(startFreq = 700f, endFreq = 400f, durationMs = 40, volume = 0.35f * soundVolume)
        }
    }

    fun playDealSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            // Crisp whoosh-snap sound
            playToneSweep(startFreq = 500f, endFreq = 800f, durationMs = 60, volume = 0.5f * soundVolume)
        }
    }

    fun playCardPlaySound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            // Satisfying tactile card slap / flick
            playTone(frequency = 600f, durationMs = 45, volume = 0.6f * soundVolume)
        }
    }

    fun playInvalidActionSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            // Low double buzz
            playTone(frequency = 180f, durationMs = 90, volume = 0.7f * soundVolume)
        }
    }

    fun playTrickWonSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            // Ascending major chime
            playTone(frequency = 523.25f, durationMs = 70, volume = 0.6f * soundVolume) // C5
            kotlinx.coroutines.delay(60)
            playTone(frequency = 659.25f, durationMs = 90, volume = 0.6f * soundVolume) // E5
        }
    }

    fun playScoreUpdateSound() {
        if (!isMasterSoundEnabled || !isActionSoundEnabled) return
        scope.launch {
            playTone(frequency = 440f, durationMs = 80, volume = 0.5f * soundVolume)
            kotlinx.coroutines.delay(70)
            playTone(frequency = 587.33f, durationMs = 100, volume = 0.5f * soundVolume)
        }
    }

    fun playWinnerMusic() {
        if (!isMasterSoundEnabled || !isWinnerMusicEnabled) return
        scope.launch {
            // Triumphant victory fanfare (C - E - G - high C)
            val notes = listOf(523.25f, 659.25f, 783.99f, 1046.50f)
            for (freq in notes) {
                playTone(frequency = freq, durationMs = 160, volume = 0.8f * soundVolume)
                kotlinx.coroutines.delay(130)
            }
        }
    }

    fun triggerHapticFeedback(type: HapticType = HapticType.SELECTION) {
        if (!isHapticsEnabled || vibrator == null || !vibrator!!.hasVibrator()) return

        val durationMultiplier = when (hapticStrength) {
            "Light" -> 0.6f
            "Strong" -> 1.5f
            else -> 1.0f
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (millis, amplitude) = when (type) {
                    HapticType.SELECTION -> (15 * durationMultiplier).toLong() to (80 * durationMultiplier).toInt().coerceIn(1, 255)
                    HapticType.CARD_PLAY -> (30 * durationMultiplier).toLong() to (160 * durationMultiplier).toInt().coerceIn(1, 255)
                    HapticType.TRICK_WON -> (50 * durationMultiplier).toLong() to (220 * durationMultiplier).toInt().coerceIn(1, 255)
                    HapticType.ERROR -> (70 * durationMultiplier).toLong() to (255 * durationMultiplier).toInt().coerceIn(1, 255)
                    HapticType.VICTORY -> (100 * durationMultiplier).toLong() to (255 * durationMultiplier).toInt().coerceIn(1, 255)
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(millis.coerceAtLeast(5), amplitude))
            } else {
                @Suppress("DEPRECATION")
                val millis = when (type) {
                    HapticType.SELECTION -> (15 * durationMultiplier).toLong()
                    HapticType.CARD_PLAY -> (30 * durationMultiplier).toLong()
                    HapticType.TRICK_WON -> (50 * durationMultiplier).toLong()
                    HapticType.ERROR -> (70 * durationMultiplier).toLong()
                    HapticType.VICTORY -> (100 * durationMultiplier).toLong()
                }
                vibrator?.vibrate(millis.coerceAtLeast(5))
            }
        } catch (_: Exception) {
            // Ignore any haptic device exceptions
        }
    }

    private fun playTone(frequency: Float, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 22050
            val numSamples = (durationMs * sampleRate) / 1000
            val generatedSnd = ShortArray(numSamples)
            val decaySamples = (numSamples * 0.4f).toInt()

            for (i in 0 until numSamples) {
                val envelope = if (i > numSamples - decaySamples) {
                    (numSamples - i).toFloat() / decaySamples
                } else {
                    1.0f
                }
                val sample = (sin(2.0 * Math.PI * i * frequency / sampleRate) * Short.MAX_VALUE * volume * envelope).toInt()
                generatedSnd[i] = sample.toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(generatedSnd, 0, numSamples)
            track.play()
            scope.launch {
                kotlinx.coroutines.delay(durationMs.toLong() + 100)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // Ignore audio generation failures gracefully
        }
    }

    private fun playToneSweep(startFreq: Float, endFreq: Float, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 22050
            val numSamples = (durationMs * sampleRate) / 1000
            val generatedSnd = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val currentFreq = startFreq + (endFreq - startFreq) * progress
                val envelope = 1.0f - progress * 0.7f
                val sample = (sin(2.0 * Math.PI * i * currentFreq / sampleRate) * Short.MAX_VALUE * volume * envelope).toInt()
                generatedSnd[i] = sample.toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(generatedSnd, 0, numSamples)
            track.play()
            scope.launch {
                kotlinx.coroutines.delay(durationMs.toLong() + 100)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    enum class HapticType {
        SELECTION,
        CARD_PLAY,
        TRICK_WON,
        ERROR,
        VICTORY
    }
}

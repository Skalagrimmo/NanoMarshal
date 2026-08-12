package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.AIState
import com.example.data.model.Enemy
import com.example.data.model.EnemyType
import com.example.data.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.sqrt

enum class AudioIntensityCategory(val label: String) {
    CALM_PATROL("PATROL // AMBIENT STEALTH"),
    CAUTION_SUSPICIOUS("SUSPICIOUS // CAUTION TENSION"),
    COMBAT_HIGH("COMBAT // HIGH INTENSITY"),
    BOSS_CRITICAL("CRITICAL // BOSS ENGAGEMENT")
}

data class AudioIntensityState(
    val intensity: Float = 0.05f, // 0.0f to 1.0f
    val targetIntensity: Float = 0.05f,
    val category: AudioIntensityCategory = AudioIntensityCategory.CALM_PATROL,
    val tempoBpm: Int = 75,
    val activePatrolEnemies: Int = 0,
    val activeSuspiciousEnemies: Int = 0,
    val activeCombatEnemies: Int = 0,
    val activeFlankingEnemies: Int = 0,
    val isMuted: Boolean = false
)

/**
 * Adaptive Audio Manager that dynamically modulates audio intensity, tempo (BPM), bass pulse synthesis,
 * and combat stingers based on the live Finite State Machine (FSM) states of all active enemies.
 */
class AdaptiveAudioManager {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _audioState = MutableStateFlow(AudioIntensityState())
    val audioState: StateFlow<AudioIntensityState> = _audioState.asStateFlow()

    private var audioLoopJob: Job? = null
    private var isEngineRunning = false
    private var previousCategory: AudioIntensityCategory = AudioIntensityCategory.CALM_PATROL

    /**
     * Starts the adaptive procedural audio synthesis loop.
     */
    fun startEngine() {
        if (isEngineRunning) return
        isEngineRunning = true

        audioLoopJob = scope.launch {
            var stepCounter = 0
            while (isEngineRunning) {
                val state = _audioState.value
                if (!state.isMuted) {
                    val delayMs = (60000 / state.tempoBpm / 2).coerceIn(100, 500)
                    playProceduralAudioStep(state, stepCounter)
                    stepCounter = (stepCounter + 1) % 16
                    delay(delayMs.toLong())
                } else {
                    delay(250)
                }
            }
        }
    }

    /**
     * Stops the audio engine.
     */
    fun stopEngine() {
        isEngineRunning = false
        audioLoopJob?.cancel()
        audioLoopJob = null
        try {
            streamingAudioTrack?.stop()
            streamingAudioTrack?.release()
        } catch (_: Exception) {}
        streamingAudioTrack = null
    }

    /**
     * Toggles audio mute setting.
     */
    fun setMuted(muted: Boolean) {
        _audioState.value = _audioState.value.copy(isMuted = muted)
    }

    /**
     * Evaluates all alive enemies in the world, calculates the overall threat vector and FSM state distribution,
     * and smoothly lerps the adaptive audio intensity and tempo.
     */
    fun update(
        enemies: List<Enemy>,
        player: PlayerState,
        deltaSec: Float
    ) {
        var patrolCount = 0
        var suspiciousCount = 0
        var combatCount = 0
        var flankingCount = 0
        var bossEngaged = false

        var maxThreat = 0.05f

        val aliveEnemies = enemies.filter { it.health > 0f }

        for (enemy in aliveEnemies) {
            val dx = player.x - enemy.x
            val dy = player.y - enemy.y
            val dist = sqrt(dx * dx + dy * dy)
            val isProximityClose = dist < 220f

            when (enemy.state) {
                AIState.PATROL -> {
                    patrolCount++
                    maxThreat = maxOf(maxThreat, 0.08f)
                }

                AIState.SUSPICIOUS, AIState.INVESTIGATING -> {
                    suspiciousCount++
                    val threat = if (isProximityClose) 0.45f else 0.30f
                    maxThreat = maxOf(maxThreat, threat)
                }

                AIState.ENGAGED, AIState.SEEKING_COVER -> {
                    combatCount++
                    val threat = if (isProximityClose) 0.85f else 0.70f
                    if (enemy.type == EnemyType.BOUNTY_BOSS) bossEngaged = true
                    maxThreat = maxOf(maxThreat, threat)
                }

                AIState.FLANKING, AIState.SUPPRESSING -> {
                    flankingCount++
                    combatCount++
                    val threat = if (isProximityClose) 0.95f else 0.80f
                    if (enemy.type == EnemyType.BOUNTY_BOSS) bossEngaged = true
                    maxThreat = maxOf(maxThreat, threat)
                }

                AIState.RETREAT -> {
                    combatCount++
                    maxThreat = maxOf(maxThreat, 0.50f)
                }

                AIState.STUNNED, AIState.DEAD -> {}
            }
        }

        // Calculate target intensity based on enemy pressure & FSM states
        val activeCombatTotal = combatCount + flankingCount
        val combatPressure = (activeCombatTotal * 0.20f).coerceAtMost(0.40f)

        val rawTargetIntensity = if (bossEngaged) {
            1.0f
        } else {
            (maxThreat + combatPressure).coerceIn(0.05f, 1.0f)
        }

        val currIntensity = _audioState.value.intensity

        // Escalation is rapid; de-escalation is gradual
        val lerpSpeed = if (rawTargetIntensity > currIntensity) 2.5f else 0.6f
        val newIntensity = (currIntensity + (rawTargetIntensity - currIntensity) * (deltaSec * lerpSpeed)).coerceIn(0.05f, 1.0f)

        // Map intensity to Category & Tempo BPM
        val category = when {
            bossEngaged && newIntensity > 0.85f -> AudioIntensityCategory.BOSS_CRITICAL
            newIntensity >= 0.60f -> AudioIntensityCategory.COMBAT_HIGH
            newIntensity >= 0.25f -> AudioIntensityCategory.CAUTION_SUSPICIOUS
            else -> AudioIntensityCategory.CALM_PATROL
        }

        val tempoBpm = when (category) {
            AudioIntensityCategory.CALM_PATROL -> 75 + (newIntensity * 30).toInt()
            AudioIntensityCategory.CAUTION_SUSPICIOUS -> 95 + (newIntensity * 35).toInt()
            AudioIntensityCategory.COMBAT_HIGH -> 120 + (newIntensity * 25).toInt()
            AudioIntensityCategory.BOSS_CRITICAL -> 150
        }

        // Trigger combat escalation stinger when shifting into COMBAT or BOSS_CRITICAL
        if (category != previousCategory) {
            if ((category == AudioIntensityCategory.COMBAT_HIGH || category == AudioIntensityCategory.BOSS_CRITICAL) &&
                (previousCategory == AudioIntensityCategory.CALM_PATROL || previousCategory == AudioIntensityCategory.CAUTION_SUSPICIOUS)
            ) {
                SoundFX.play(SoundFX.SoundType.ALERT_SIREN)
            }
            previousCategory = category
        }

        _audioState.value = _audioState.value.copy(
            intensity = newIntensity,
            targetIntensity = rawTargetIntensity,
            category = category,
            tempoBpm = tempoBpm,
            activePatrolEnemies = patrolCount,
            activeSuspiciousEnemies = suspiciousCount,
            activeCombatEnemies = combatCount,
            activeFlankingEnemies = flankingCount
        )
    }

    /**
     * Synthesizes dynamic procedural audio beats based on the current intensity state.
     */
    private fun playProceduralAudioStep(state: AudioIntensityState, step: Int) {
        val intensity = state.intensity
        val sampleRate = 22050

        // Determine step duration (1/16th note at tempo)
        val stepMs = (60000 / state.tempoBpm / 2).coerceIn(80, 300)
        val numSamples = (sampleRate * (stepMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        // Fundamental frequencies based on intensity:
        // Calm: E1 (41.2 Hz) / A1 (55.0 Hz) drone
        // Combat: Aggressive C2 (65.4 Hz) / G2 (98.0 Hz) driving synth pulse
        val baseFreq = when (state.category) {
            AudioIntensityCategory.CALM_PATROL -> 41.2f
            AudioIntensityCategory.CAUTION_SUSPICIOUS -> 55.0f
            AudioIntensityCategory.COMBAT_HIGH -> 65.4f
            AudioIntensityCategory.BOSS_CRITICAL -> 82.4f
        }

        val isKickStep = (step % 4 == 0)
        val isOffBeat = (step % 2 == 1)
        val isHiHat = (step % 2 == 0) && intensity > 0.25f
        val isArpStep = (step % 2 == 0) && intensity > 0.60f

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val timeSec = i.toDouble() / sampleRate

            var sample = 0.0

            // 1. Sub-Bass Synth Drone / Kick Pulse
            if (isKickStep) {
                // Pitch slide for kick drum impact
                val kickFreq = baseFreq * (1.0 + (1.0 - t) * 2.0)
                val kickEnvelope = (1.0 - t) * (1.0 - t)
                sample += sin(2.0 * Math.PI * kickFreq * timeSec) * kickEnvelope * (0.3 + intensity * 0.4)
            } else {
                // Low ambient drone
                val droneEnvelope = 0.4 + 0.2 * sin(2.0 * Math.PI * 0.5 * timeSec)
                sample += sin(2.0 * Math.PI * baseFreq * timeSec) * droneEnvelope * 0.15
            }

            // 2. Mid-register Rhythmic Tension Pulse (Suspicious & Combat)
            if (isOffBeat && intensity > 0.20f) {
                val tensionFreq = baseFreq * 1.5f // Fifth harmonic
                val pulseEnvelope = (1.0 - t)
                sample += sin(2.0 * Math.PI * tensionFreq * timeSec) * pulseEnvelope * (0.15 + intensity * 0.2)
            }

            // 3. High Percussive Ticks / Metallic Hi-Hats (Combat High)
            if (isHiHat && intensity > 0.35f) {
                val hatEnvelope = (1.0 - t) * (1.0 - t) * (1.0 - t)
                val noise = (Math.random() * 2.0 - 1.0)
                sample += noise * hatEnvelope * (0.08 + intensity * 0.12)
            }

            // 4. Arpeggiated Synthesizer Lead Layer (Combat & Boss Critical)
            if (isArpStep && intensity > 0.55f) {
                val arpNoteMultiplier = when ((step / 2) % 4) {
                    0 -> 1.0f
                    1 -> 1.25f  // Minor third
                    2 -> 1.50f  // Fifth
                    else -> 1.875f // Minor seventh
                }
                val arpFreq = baseFreq * 4f * arpNoteMultiplier
                val arpEnvelope = 1.0 - t
                sample += sin(2.0 * Math.PI * arpFreq * timeSec) * arpEnvelope * (0.10 + intensity * 0.25)
            }

            // Final volume scaling and clamping
            val finalVal = (sample * 32767.0 * 0.35).toInt().coerceIn(-32768, 32767)
            buffer[i] = finalVal.toShort()
        }

        playRawBuffer(buffer, sampleRate)
    }

    private var streamingAudioTrack: AudioTrack? = null

    private fun getOrCreateAudioTrack(sampleRate: Int): AudioTrack? {
        val existing = streamingAudioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }
        try {
            existing?.release()
        } catch (_: Exception) {}

        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            streamingAudioTrack = track
            track
        } catch (e: Exception) {
            null
        }
    }

    private fun playRawBuffer(buffer: ShortArray, sampleRate: Int) {
        try {
            val track = getOrCreateAudioTrack(sampleRate) ?: return
            track.write(buffer, 0, buffer.size)
        } catch (_: Exception) {
            // Fallback if audio track cannot write
        }
    }
}

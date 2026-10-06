package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameStatus {
    NOT_STARTED,
    PLAYING,
    WINNER_CELEBRATION
}

sealed class GameEvent {
    object Collision : GameEvent()
    data class WinnerDeclared(val country: Country) : GameEvent()
    object NewRoundStarted : GameEvent()
}

data class FlagState(
    val country: Country,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float = 24f,
    var maxHp: Int = 3,
    var hp: Int = 3,
    var cooldown: Int = 0
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Long,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f
)

data class FloatingText(
    val text: String,
    var x: Float,
    var y: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f,
    val color: Long = 0xFFFF3B30
)

class FlagBattleViewModel : ViewModel() {

    private val _gameStatus = MutableStateFlow(GameStatus.NOT_STARTED)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()

    private val _flags = MutableStateFlow<List<FlagState>>(emptyList())
    val flags: StateFlow<List<FlagState>> = _flags.asStateFlow()

    private val _eliminatedFlags = MutableStateFlow<List<Country>>(emptyList())
    val eliminatedFlags: StateFlow<List<Country>> = _eliminatedFlags.asStateFlow()

    private val _particles = MutableStateFlow<List<Particle>>(emptyList())
    val particles: StateFlow<List<Particle>> = _particles.asStateFlow()

    private val _floatingTexts = MutableStateFlow<List<FloatingText>>(emptyList())
    val floatingTexts: StateFlow<List<FloatingText>> = _floatingTexts.asStateFlow()

    private val _winner = MutableStateFlow<Country?>(null)
    val winner: StateFlow<Country?> = _winner.asStateFlow()

    private val _roundNumber = MutableStateFlow(1)
    val roundNumber: StateFlow<Int> = _roundNumber.asStateFlow()

    private val _totalRoundFlags = MutableStateFlow(0)
    val totalRoundFlags: StateFlow<Int> = _totalRoundFlags.asStateFlow()

    // Physics tick flow to force recomposition of Compose Canvas on every frame
    private val _physicsTick = MutableStateFlow(0L)
    val physicsTick: StateFlow<Long> = _physicsTick.asStateFlow()

    // Shared flow for Sound Playback & Speech TTS events
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var gameJob: Job? = null
    private var transitionJob: Job? = null
    private val random = Random(System.currentTimeMillis())

    // Virtual Canvas coordinates scale perfectly to 1000x1000
    private val centerX = 500f
    private val centerY = 500f
    private val arenaRadius = 430f

    fun startGame() {
        _roundNumber.value = 1
        startNewRound()
    }

    fun startNewRound() {
        // 1. Cancel and clean up any ongoing or pending tasks/loops completely
        transitionJob?.cancel()
        gameJob?.cancel()

        // 2. Clear state lists to prevent leaks or duplicate objects across rounds
        _flags.value = emptyList()
        _particles.value = emptyList()
        _floatingTexts.value = emptyList()
        _eliminatedFlags.value = emptyList()
        _winner.value = null

        val allAvailable = CountryProvider.allCountries
        // Use 80 random countries for the ultimate balanced battle simulation
        val numCountries = 80
        val selectedCountries = allAvailable.shuffled(random).take(numCountries)
        
        _totalRoundFlags.value = numCountries

        // Dispatch NewRoundStarted event so view layer plays the Bangla voice prompt!
        _events.tryEmit(GameEvent.NewRoundStarted)

        // 3. Spatially position countries inside the circular arena
        val initialFlags = mutableListOf<FlagState>()
        val maxPlacementRadius = arenaRadius - 40f
        
        for (country in selectedCountries) {
            // Position uniformly using polar coordinates
            val r = sqrt(random.nextFloat()) * maxPlacementRadius
            val theta = random.nextFloat() * 2f * Math.PI.toFloat()
            val x = centerX + r * cos(theta)
            val y = centerY + r * sin(theta)

            // Random initial velocity vectors
            val speed = 180f + random.nextFloat() * 80f
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val vx = speed * cos(angle)
            val vy = speed * sin(angle)

            initialFlags.add(
                FlagState(
                    country = country,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    radius = 22f,
                    maxHp = 3,
                    hp = 3,
                    cooldown = 0
                )
            )
        }

        // 4. Resolve overlapping positions right at startup
        repeat(25) {
            for (i in 0 until initialFlags.size) {
                for (j in i + 1 until initialFlags.size) {
                    val f1 = initialFlags[i]
                    val f2 = initialFlags[j]
                    val dx = f2.x - f1.x
                    val dy = f2.y - f1.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val minDist = f1.radius + f2.radius
                    if (dist < minDist && dist > 0.1f) {
                        val overlap = minDist - dist
                        val pushX = (dx / dist) * overlap * 0.5f
                        val pushY = (dy / dist) * overlap * 0.5f
                        f1.x -= pushX
                        f1.y -= pushY
                        f2.x += pushX
                        f2.y += pushY

                        // Clamp back into bounds
                        val dist1 = sqrt((f1.x - centerX) * (f1.x - centerX) + (f1.y - centerY) * (f1.y - centerY))
                        if (dist1 > maxPlacementRadius) {
                            f1.x = centerX + ((f1.x - centerX) / dist1) * maxPlacementRadius
                            f1.y = centerY + ((f1.y - centerY) / dist1) * maxPlacementRadius
                        }
                        val dist2 = sqrt((f2.x - centerX) * (f2.x - centerX) + (f2.y - centerY) * (f2.y - centerY))
                        if (dist2 > maxPlacementRadius) {
                            f2.x = centerX + ((f2.x - centerX) / dist2) * maxPlacementRadius
                            f2.y = centerY + ((f2.y - centerY) / dist2) * maxPlacementRadius
                        }
                    }
                }
            }
        }

        _flags.value = initialFlags
        _gameStatus.value = GameStatus.PLAYING
        
        // Launch a single active game loop
        startGameLoop()
    }

    private fun startGameLoop() {
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val dt = (currentTime - lastTime) / 1000f
                lastTime = currentTime

                // Safe dt ceiling
                val stepDt = dt.coerceAtMost(0.03f)

                updatePhysics(stepDt)
                
                // Force a StateFlow update to trigger Canvas redraw in Compose on every tick!
                _physicsTick.value = currentTime
                
                delay(16) // Solid 60 FPS
            }
        }
    }

    private fun updatePhysics(dt: Float) {
        val currentFlags = _flags.value.toMutableList()
        if (_gameStatus.value != GameStatus.PLAYING) return

        // Guard against single winner remaining
        if (currentFlags.size <= 1) {
            val finalWinner = currentFlags.firstOrNull()?.country ?: CountryProvider.allCountries.random()
            triggerWinner(finalWinner)
            return
        }

        // LATE GAME ACTION ACCELERATOR
        // Magnetic seek force active below 12 remaining flags. Escalates as flags drop.
        if (currentFlags.size <= 12) {
            val size = currentFlags.size
            for (f1 in currentFlags) {
                var nearest: FlagState? = null
                var minDist = Float.MAX_VALUE
                for (f2 in currentFlags) {
                    if (f1 === f2) continue
                    val dx = f2.x - f1.x
                    val dy = f2.y - f1.y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < minDist) {
                        minDist = d
                        nearest = f2
                    }
                }
                
                if (nearest != null && minDist > 0.1f) {
                    val dx = nearest.x - f1.x
                    val dy = nearest.y - f1.y
                    
                    val remainingFactor = (13 - size).coerceAtLeast(1)
                    val pullStrength = 180f * remainingFactor
                    
                    f1.vx += (dx / minDist) * pullStrength * dt
                    f1.vy += (dy / minDist) * pullStrength * dt
                }
            }
        }

        // 1. Position update and high-velocity speed enforcement
        for (flag in currentFlags) {
            flag.x += flag.vx * dt
            flag.y += flag.vy * dt
            if (flag.cooldown > 0) {
                flag.cooldown--
            }

            // Target velocities are scaled slightly higher in late game for added drama
            val minSpeed = if (currentFlags.size <= 8) 220f else 180f
            val maxSpeed = if (currentFlags.size <= 8) 420f else 380f
            
            val speed = sqrt(flag.vx * flag.vx + flag.vy * flag.vy)
            if (speed < minSpeed) {
                if (speed > 0.1f) {
                    flag.vx = (flag.vx / speed) * minSpeed
                    flag.vy = (flag.vy / speed) * minSpeed
                } else {
                    val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                    flag.vx = minSpeed * cos(angle).toFloat()
                    flag.vy = minSpeed * sin(angle).toFloat()
                }
            } else if (speed > maxSpeed) {
                flag.vx = (flag.vx / speed) * maxSpeed
                flag.vy = (flag.vy / speed) * maxSpeed
            }
        }

        // 2. Circular boundary collision bounce
        for (flag in currentFlags) {
            val dx = flag.x - centerX
            val dy = flag.y - centerY
            val dist = sqrt(dx * dx + dy * dy)
            val limit = arenaRadius - flag.radius
            if (dist > limit) {
                val nx = dx / dist
                val ny = dy / dist

                val dot = flag.vx * nx + flag.vy * ny
                if (dot > 0f) {
                    flag.vx = flag.vx - 2f * dot * nx
                    flag.vy = flag.vy - 2f * dot * ny
                }

                flag.x = centerX + nx * limit
                flag.y = centerY + ny * limit
            }
        }

        // 3. Flags pairwise collisions
        val toEliminate = mutableSetOf<FlagState>()
        
        for (i in 0 until currentFlags.size) {
            for (j in i + 1 until currentFlags.size) {
                val f1 = currentFlags[i]
                val f2 = currentFlags[j]
                if (toEliminate.contains(f1) || toEliminate.contains(f2)) continue

                val dx = f2.x - f1.x
                val dy = f2.y - f1.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = f1.radius + f2.radius

                if (dist < minDist && dist > 0.1f) {
                    // Push apart to prevent overlapping capture
                    val overlap = minDist - dist
                    val nx = dx / dist
                    val ny = dy / dist
                    
                    val pushX = nx * overlap * 0.5f
                    val pushY = ny * overlap * 0.5f
                    
                    f1.x -= pushX
                    f1.y -= pushY
                    f2.x += pushX
                    f2.y += pushY

                    // Elastic bounce logic
                    val rvx = f2.vx - f1.vx
                    val rvy = f2.vy - f1.vy
                    val velAlongNormal = rvx * nx + rvy * ny

                    if (velAlongNormal < 0f) {
                        val impulse = -1.85f * velAlongNormal / 2f
                        f1.vx -= impulse * nx
                        f1.vy -= impulse * ny
                        f2.vx += impulse * nx
                        f2.vy += impulse * ny
                    }

                    // Damage and cooldown combat timers
                    if (f1.cooldown == 0 && f2.cooldown == 0) {
                        f1.cooldown = 12
                        f2.cooldown = 12

                        // Dispatch audio pop sound event
                        _events.tryEmit(GameEvent.Collision)

                        // Damage randomly applied
                        val damageTarget = if (random.nextBoolean()) f1 else f2
                        damageTarget.hp -= 1
                        
                        // Spawn impact sparks
                        val collX = (f1.x + f2.x) / 2f
                        val collY = (f1.y + f2.y) / 2f
                        spawnSparks(collX, collY, ColorPalette.NEON_RED)

                        if (damageTarget.hp <= 0) {
                            toEliminate.add(damageTarget)
                        }
                    }
                }
            }
        }

        // 4. Handle Eliminations & list maintenance
        if (toEliminate.isNotEmpty()) {
            val updatedList = currentFlags.filter { !toEliminate.contains(it) }
            
            for (elim in toEliminate) {
                _eliminatedFlags.value = _eliminatedFlags.value + elim.country
                spawnExplosion(elim.x, elim.y)
                spawnFloatingText("- ${elim.country.name}", elim.x, elim.y - 15f)
            }

            _flags.value = updatedList

            // Trigger winner if count drops below 2
            if (updatedList.size <= 1) {
                triggerWinner(updatedList.firstOrNull()?.country ?: toEliminate.first().country)
            }
        } else {
            _flags.value = currentFlags
        }

        // 5. Visual effect timers tick
        updateVisualEffects(dt)
    }

    private fun spawnSparks(x: Float, y: Float, color: Long) {
        val current = _particles.value
        if (current.size > 80) return

        val newSparks = (0..4).map {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 80f + random.nextFloat() * 100f
            Particle(
                x = x,
                y = y,
                vx = speed * cos(angle),
                vy = speed * sin(angle),
                color = color,
                life = 0.4f + random.nextFloat() * 0.3f
            )
        }
        _particles.value = current + newSparks
    }

    private fun spawnExplosion(x: Float, y: Float) {
        val current = _particles.value
        val count = if (current.size > 80) 6 else 15

        val colors = listOf(ColorPalette.NEON_RED, ColorPalette.NEON_YELLOW, ColorPalette.NEON_WHITE)
        val newParticles = (0..count).map {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 100f + random.nextFloat() * 180f
            Particle(
                x = x,
                y = y,
                vx = speed * cos(angle),
                vy = speed * sin(angle),
                color = colors[random.nextInt(colors.size)],
                life = 0.6f + random.nextFloat() * 0.6f
            )
        }
        _particles.value = current.takeLast(100) + newParticles
    }

    private fun spawnFloatingText(text: String, x: Float, y: Float) {
        val current = _floatingTexts.value
        val newText = FloatingText(
            text = text,
            x = x,
            y = y,
            life = 1.2f,
            color = ColorPalette.NEON_RED
        )
        _floatingTexts.value = current.takeLast(8) + newText
    }

    private fun updateVisualEffects(dt: Float) {
        val activeParticles = _particles.value.mapNotNull { p ->
            p.life -= dt
            if (p.life > 0) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vx *= 0.95f
                p.vy *= 0.95f
                p.alpha = (p.life / 1.0f).coerceIn(0f, 1f)
                p
            } else null
        }
        _particles.value = activeParticles

        val activeTexts = _floatingTexts.value.mapNotNull { t ->
            t.life -= dt
            if (t.life > 0) {
                t.y -= 30f * dt
                t.alpha = (t.life / 1.2f).coerceIn(0f, 1f)
                t
            } else null
        }
        _floatingTexts.value = activeTexts
    }

    private fun triggerWinner(winnerCountry: Country) {
        if (_gameStatus.value != GameStatus.PLAYING) return
        _gameStatus.value = GameStatus.WINNER_CELEBRATION

        gameJob?.cancel()
        _winner.value = winnerCountry

        // Play TTS speech announcement
        _events.tryEmit(GameEvent.WinnerDeclared(winnerCountry))

        // Large celebration fountains at center
        viewModelScope.launch {
            repeat(6) {
                if (_gameStatus.value == GameStatus.WINNER_CELEBRATION) {
                    spawnExplosion(centerX, centerY)
                    delay(400)
                }
            }
        }

        // Safe automatic delayed next round transition
        transitionJob?.cancel()
        transitionJob = viewModelScope.launch {
            delay(5000)
            _roundNumber.value += 1
            startNewRound()
        }
    }
}

object ColorPalette {
    val NEON_RED = 0xFFFF3B30
    val NEON_WHITE = 0xFFFFFFFF
    val NEON_YELLOW = 0xFFFFCC00
    val DARK_BG = 0xFF0D0D0D
}

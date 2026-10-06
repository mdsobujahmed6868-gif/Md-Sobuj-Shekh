package com.example

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // Launch Trigger Comment: Forces build system to deploy and launch APK in the emulator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(ColorPalette.DARK_BG)
                ) {
                    FlagBattleGameScreen()
                }
            }
        }
    }
}

@Composable
fun FlagBattleGameScreen(viewModel: FlagBattleViewModel = viewModel()) {
    val status by viewModel.gameStatus.collectAsState()
    val flags by viewModel.flags.collectAsState()
    val eliminatedFlags by viewModel.eliminatedFlags.collectAsState()
    val particles by viewModel.particles.collectAsState()
    val floatingTexts by viewModel.floatingTexts.collectAsState()
    val winner by viewModel.winner.collectAsState()
    val roundNumber by viewModel.roundNumber.collectAsState()
    val totalRoundFlags by viewModel.totalRoundFlags.collectAsState()
    val physicsTick by viewModel.physicsTick.collectAsState()

    val context = LocalContext.current
    
    // Smooth border rotation transition animation
    val infiniteTransition = rememberInfiniteTransition(label = "border_glow")
    val borderRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    
    // Instantiate Audio and Speech Managers
    val soundPlayer = remember { CollisionSoundPlayer() }
    val voiceSpeaker = remember { WinnerVoiceSpeaker(context) }

    // Connect to ViewModel Event Flows
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.Collision -> {
                    soundPlayer.playCollisionSound()
                }
                is GameEvent.WinnerDeclared -> {
                    coroutineScope.launch {
                        // 1. Play exciting victory sound
                        voiceSpeaker.playVictorySound()
                        delay(600)
                        
                        // 2. FIRST announce the winner country name clearly
                        voiceSpeaker.speak("And the winner is ${event.country.name}!")
                    }
                }
                is GameEvent.NewRoundStarted -> {
                    coroutineScope.launch {
                        // 3. Play Like/Subscribe voice in Bangla at the start of the new round
                        voiceSpeaker.speakBangla("নতুন রাউন্ড শুরু হচ্ছে! ভিডিওতে একটা Like দাও এবং চ্যানেলটি Subscribe করে দাও!")
                    }
                }
            }
        }
    }

    // Release audio systems on dispose
    DisposableEffect(Unit) {
        onDispose {
            soundPlayer.release()
            voiceSpeaker.release()
        }
    }

    // 9:16 aspect ratio container with professional dark theme borders
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(9f / 16f)
                .background(Color(ColorPalette.DARK_BG))
                .border(2.dp, Color(ColorPalette.NEON_RED))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP PANEL
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GameLogo()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FLAG BATTLE",
                        color = Color(ColorPalette.NEON_RED),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "195 COUNTRIES BATTLE ROYALE",
                        color = Color(ColorPalette.NEON_WHITE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Round Number indicator
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF220D0D))
                            .border(1.dp, Color(ColorPalette.NEON_RED), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(ColorPalette.NEON_RED))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ROUND $roundNumber",
                            color = Color(ColorPalette.NEON_WHITE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status == GameStatus.PLAYING) "BATTLE RUNNING" else "COMPLETED",
                            color = Color(ColorPalette.NEON_YELLOW),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // CENTER GAME ARENA
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (status != GameStatus.NOT_STARTED) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("battle_arena_canvas")
                        ) {
                            val tick = physicsTick
                            val scale = min(size.width, size.height) / 1000f
                            val offsetX = (size.width - 1000f * scale) / 2f
                            val offsetY = (size.height - 1000f * scale) / 2f

                            val arenaCenterX = offsetX + 500f * scale
                            val arenaCenterY = offsetY + 500f * scale
                            val arenaRadius = 430f * scale

                            // Draw clean arena background
                            drawCircle(
                                color = Color(0x10FF3B30),
                                radius = arenaRadius,
                                center = Offset(arenaCenterX, arenaCenterY)
                            )

                            // Draw glowing circular border
                            drawCircle(
                                color = Color(ColorPalette.NEON_RED),
                                radius = arenaRadius,
                                center = Offset(arenaCenterX, arenaCenterY),
                                style = Stroke(width = 4.dp.toPx())
                            )

                            // Inner bright white neon highlight
                            drawCircle(
                                color = Color(ColorPalette.NEON_WHITE),
                                radius = arenaRadius - 1.dp.toPx(),
                                center = Offset(arenaCenterX, arenaCenterY),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Draw glowing rotatory progress highlighting arcs (Yellow/Gold)
                            val sweepAngle = 50f
                            val angleOffset = borderRotationAngle
                            drawArc(
                                color = Color(ColorPalette.NEON_YELLOW),
                                startAngle = angleOffset.toFloat(),
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(arenaCenterX - arenaRadius, arenaCenterY - arenaRadius),
                                size = Size(arenaRadius * 2f, arenaRadius * 2f),
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color(ColorPalette.NEON_YELLOW),
                                startAngle = (angleOffset + 180f) % 360,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(arenaCenterX - arenaRadius, arenaCenterY - arenaRadius),
                                size = Size(arenaRadius * 2f, arenaRadius * 2f),
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // 1. Draw Active Flags
                            for (flag in flags) {
                                val fx = offsetX + flag.x * scale
                                val fy = offsetY + flag.y * scale
                                val flagWidth = 44f * scale
                                val flagHeight = 32f * scale

                                // Card background
                                drawRoundRect(
                                    color = Color(0xFF1E1E1E),
                                    topLeft = Offset(fx - flagWidth / 2f, fy - flagHeight / 2f),
                                    size = Size(flagWidth, flagHeight),
                                    cornerRadius = CornerRadius(6f * scale, 6f * scale)
                                )

                                // Neon border showing health status
                                val hpBorderColor = when (flag.hp) {
                                    3 -> Color(ColorPalette.NEON_WHITE)
                                    2 -> Color(ColorPalette.NEON_YELLOW)
                                    else -> Color(ColorPalette.NEON_RED)
                                }
                                drawRoundRect(
                                    color = hpBorderColor,
                                    topLeft = Offset(fx - flagWidth / 2f, fy - flagHeight / 2f),
                                    size = Size(flagWidth, flagHeight),
                                    cornerRadius = CornerRadius(6f * scale, 6f * scale),
                                    style = Stroke(width = 1.5f.dp.toPx())
                                )

                                // Draw native Flag Emoji perfectly centered inside
                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        textSize = 22f * scale
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.DEFAULT
                                    }
                                    val fontMetrics = paint.fontMetrics
                                    val yOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
                                    drawText(
                                        flag.country.flagEmoji,
                                        fx,
                                        fy - yOffset,
                                        paint
                                    )
                                }
                            }

                            // 2. Draw Particles Sparkles
                            for (particle in particles) {
                                val px = offsetX + particle.x * scale
                                val py = offsetY + particle.y * scale
                                val pSize = 5f * scale * particle.life
                                drawCircle(
                                    color = Color(particle.color).copy(alpha = particle.alpha),
                                    radius = pSize,
                                    center = Offset(px, py)
                                )
                            }

                            // 3. Draw Floating Texts
                            drawContext.canvas.nativeCanvas.apply {
                                for (t in floatingTexts) {
                                    val tx = offsetX + t.x * scale
                                    val ty = offsetY + t.y * scale
                                    val textPaint = android.graphics.Paint().apply {
                                        textSize = 18f * scale
                                        color = t.color.toInt()
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        alpha = (t.alpha * 255).toInt()
                                        typeface = android.graphics.Typeface.create(
                                            android.graphics.Typeface.DEFAULT,
                                            android.graphics.Typeface.BOLD
                                        )
                                    }
                                    drawText(t.text, tx, ty, textPaint)
                                }
                            }
                        }
                    } else {
                        // Start Screen before Game begins
                        StartBattleView(onStartClick = { viewModel.startGame() })
                    }
                }

                // BOTTOM PANEL: COUNTER & ELIMINATED LIST
                if (status != GameStatus.NOT_STARTED) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Remaining count
                        val remaining = flags.size
                        val startTotal = totalRoundFlags
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ALIVE FLAGS",
                                color = Color(ColorPalette.NEON_WHITE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$remaining / $startTotal",
                                color = Color(ColorPalette.NEON_YELLOW),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("alive_flags_counter")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Progress indicator bar (neon yellow/red)
                        val progressFraction = if (startTotal > 0) remaining.toFloat() / startTotal.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E1E1E))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(ColorPalette.NEON_YELLOW))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Eliminated section title
                        Text(
                            text = "ELIMINATED COUNTRIES (${eliminatedFlags.size})",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Eliminated list grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141414))
                                .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            if (eliminatedFlags.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No countries eliminated yet",
                                        color = Color(0xFF444444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 36.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(eliminatedFlags.asReversed()) { country ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF1A1A1A))
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = country.flagEmoji,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = country.code,
                                                    color = Color(0xFF666666),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // WINNER OVERLAY SCREEN (DRAMATIC CELEBRATION)
            AnimatedVisibility(
                visible = status == GameStatus.WINNER_CELEBRATION && winner != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                winner?.let { win ->
                    WinnerCelebrationOverlay(winner = win)
                }
            }
        }
    }
}

@Composable
fun StartBattleView(onStartClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0x15FF3B30))
                .border(2.dp, Color(ColorPalette.NEON_RED), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚔️",
                fontSize = 44.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "READY FOR WAR",
            color = Color(ColorPalette.NEON_WHITE),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Continuous, 100% automated flag battle royale. Perfect for YouTube videos & unattended streams.",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onStartClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(ColorPalette.NEON_RED)),
            modifier = Modifier
                .scale(pulseScale)
                .height(56.dp)
                .width(220.dp)
                .testTag("start_battle_button"),
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text(
                text = "START BATTLE",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun WinnerCelebrationOverlay(winner: Country) {
    val infiniteTransition = rememberInfiniteTransition(label = "winner_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "winner_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF00D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        ConfettiOverlay()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            GameLogo(modifier = Modifier.scale(0.85f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "👑 ROUND WINNER 👑",
                color = Color(ColorPalette.NEON_YELLOW),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Large Pulsating Winner Flag Card
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .size(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(3.dp, Color(ColorPalette.NEON_YELLOW), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = winner.flagEmoji,
                    fontSize = 96.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = winner.name.uppercase(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("winner_country_name")
            )

            Text(
                text = "CONQUERED THE ARENA!",
                color = Color(ColorPalette.NEON_RED),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Progress timer indicating next round starting
            Text(
                text = "PREPARING NEXT ROUND...",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                color = Color(ColorPalette.NEON_YELLOW),
                trackColor = Color(0xFF1E1E1E),
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun GameLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(90.dp)
            .testTag("game_logo"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.width / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // 1. Draw outer gold neon border
            drawCircle(
                color = Color(ColorPalette.NEON_YELLOW),
                radius = radius - 4.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. Draw outer dark background
            drawCircle(
                color = Color(0xFF141414),
                radius = radius - 5.dp.toPx(),
                center = Offset(cx, cy)
            )

            // 3. Draw inner red neon ring
            drawCircle(
                color = Color(ColorPalette.NEON_RED),
                radius = radius - 16.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f.dp.toPx())
            )
        }

        // 4. Overlap textual and flag elements
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Text(
                text = "FLAG",
                color = Color(ColorPalette.NEON_RED),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                lineHeight = 10.sp
            )
            
            // Stylized intersecting racing flags in center
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 1.dp)
            ) {
                Text(text = "🏁", fontSize = 15.sp)
                Text(text = "🏳️", fontSize = 15.sp)
            }

            Text(
                text = "BATTLE",
                color = Color(ColorPalette.NEON_RED),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                lineHeight = 10.sp
            )
            
            // red sub-banner for 195 COUNTRIES
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(ColorPalette.NEON_RED))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "195 COUNTRIES",
                    color = Color.White,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 5. Draw multiple country flag elements around the badge!
        val surroundingFlags = listOf("🇧🇩", "🇯🇵", "🇺🇸", "🇧🇷", "🇨🇦", "🇦🇷", "🇬🇧", "🇮🇳")
        surroundingFlags.forEachIndexed { index, emoji ->
            val angle = (index * (360f / surroundingFlags.size)) * Math.PI / 180f
            val distanceDp = 38.dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (distanceDp * kotlin.math.cos(angle).toFloat()),
                        y = (distanceDp * kotlin.math.sin(angle).toFloat())
                    )
            ) {
                Text(text = emoji, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun ConfettiOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    
    // Animate phase offset continuously between 0 and 1
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Generate static properties for 45 confetti particles
    val particles = remember {
        List(45) {
            val randomX = (0..100).random() / 100f
            val randomSpeed = 0.4f + (0..60).random() / 100f
            val randomSize = 6 + (0..8).random()
            val colors = listOf(
                Color(0xFFFF3B30), // Red
                Color(0xFFFFCC00), // Yellow
                Color(0xFF34C759), // Green
                Color(0xFF007AFF), // Blue
                Color(0xFFAF52DE), // Purple
                Color(0xFFFFFFFF)  // White
            )
            ConfettiParticle(
                startX = randomX,
                speed = randomSpeed,
                color = colors.random(),
                size = randomSize.dp,
                angleSpeed = 180f + (0..360).random().toFloat(),
                amplitude = (20 + (0..30).random()).dp
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (p in particles) {
                val progress = (phase * p.speed) % 1.0f
                val y = progress * size.height
                val xOffset = sin(progress * 2f * 3.1415927f * 2f) * p.amplitude.toPx()
                val x = p.startX * size.width + xOffset
                
                // Draw falling and rotating colorful squares
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.translate(x, y)
                drawContext.canvas.nativeCanvas.rotate(progress * p.angleSpeed)
                
                drawRect(
                    color = p.color,
                    topLeft = Offset(-p.size.toPx() / 2f, -p.size.toPx() / 2f),
                    size = Size(p.size.toPx(), p.size.toPx())
                )
                
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }
}

data class ConfettiParticle(
    val startX: Float,
    val speed: Float,
    val color: Color,
    val size: androidx.compose.ui.unit.Dp,
    val angleSpeed: Float,
    val amplitude: androidx.compose.ui.unit.Dp
)

// ==========================================
// AUDIO SYNTHESIZER AND SPEECH SYSTEMS
// ==========================================

/**
 * Highly responsive, zero-latency synthesizer using ToneGenerator
 * Plays a pleasant retro-arcade impact beep.
 */
class CollisionSoundPlayer {
    private var toneGenerator: ToneGenerator? = null
    private var lastPlayTime = 0L

    init {
        try {
            // Allocate a ToneGenerator to general media stream
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun playCollisionSound() {
        val now = System.currentTimeMillis()
        // Throttle to 120ms to prevent buffer overloading and clipping noise
        if (now - lastPlayTime > 120) {
            lastPlayTime = now
            try {
                // Short, pleasant, retro-impact synthesizer beep
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
            } catch (t: Throwable) {
                // Graceful fallback if streams are busy
            }
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (t: Throwable) {
            // Ignore release exceptions
        }
        toneGenerator = null
    }
}

/**
 * Text-to-Speech winner engine utilizing native Android Speech TTS engine.
 */
class WinnerVoiceSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
        }
    }

    fun speak(text: String) {
        if (isReady && tts != null) {
            try {
                // Safely set language before speaking to completely avoid asynchronous init race conditions
                tts?.setLanguage(Locale.US)
                // Queue flush overrides any previous talk to prioritize winner
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "winner_announce_id")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun speakBangla(text: String) {
        if (isReady && tts != null) {
            try {
                // Safely set language to Bangla before speaking
                tts?.setLanguage(Locale("bn", "BD"))
                // Queue flush overrides any previous talk
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bangla_announce_id")
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun playVictorySound() {
        try {
            // Play a short pleasant victory fanfare beep sequence
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            toneGenerator?.release()
        } catch (t: Throwable) {
            // Ignore teardown exceptions
        }
        tts = null
        toneGenerator = null
        isReady = false
    }
}

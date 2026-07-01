package com.zor.monitor.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zor.monitor.R

@Composable
fun SplashScreen() {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Проигрываем звук on.mp3 при появлении заставки
    LaunchedEffect(Unit) {
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.on)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (_: Exception) {
            // Игнорируем ошибки, чтобы приложение не падало
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Анимация логотипа
            val infiniteTransition = rememberInfiniteTransition()
            val ringRadius by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 48f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            val ringAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            val ringRadius2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 48f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing, delayMillis = 1500),
                    repeatMode = RepeatMode.Restart
                )
            )
            val ringAlpha2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing, delayMillis = 1500),
                    repeatMode = RepeatMode.Restart
                )
            )
            val scanY by infiniteTransition.animateFloat(
                initialValue = 30f,
                targetValue = 80f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val scanAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val dotOpacity by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val dotY by infiniteTransition.animateFloat(
                initialValue = 30f,
                targetValue = 28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Canvas(
                modifier = Modifier.size(200.dp)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.45f
                val strokeWidth = size.minDimension * 0.08f

                // Используем заранее вычисленные цвета
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 0.2f * size.minDimension)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.2f),
                    radius = radius * 0.67f,
                    center = center,
                    style = Stroke(width = 0.2f * size.minDimension)
                )

                drawCircle(
                    color = primaryColor.copy(alpha = ringAlpha),
                    radius = ringRadius * radius / 48f,
                    center = center,
                    style = Stroke(width = 0.5f * size.minDimension)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = ringAlpha2),
                    radius = ringRadius2 * radius / 48f,
                    center = center,
                    style = Stroke(width = 0.5f * size.minDimension)
                )

                val path = Path().apply {
                    moveTo(center.x - radius * 0.6f, center.y - radius * 0.5f)
                    lineTo(center.x, center.y + radius * 0.5f)
                    lineTo(center.x + radius * 0.6f, center.y - radius * 0.5f)
                }
                val brush = Brush.linearGradient(
                    colors = listOf(primaryColor, primaryColor.copy(alpha = 0.6f)),
                    start = Offset(center.x - radius * 0.6f, center.y - radius * 0.5f),
                    end = Offset(center.x + radius * 0.6f, center.y - radius * 0.5f)
                )
                drawPath(
                    path = path,
                    brush = brush,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawLine(
                    color = onSurfaceColor.copy(alpha = scanAlpha * 0.4f),
                    start = Offset(center.x - radius * 0.6f, center.y - radius * 0.5f + scanY - 30f),
                    end = Offset(center.x + radius * 0.6f, center.y - radius * 0.5f + scanY - 30f),
                    strokeWidth = 1f * size.minDimension / 100f
                )

                drawCircle(
                    color = primaryColor.copy(alpha = dotOpacity),
                    radius = 3f * size.minDimension / 100f,
                    center = Offset(center.x, center.y - radius * 0.5f + dotY - 30f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VZOR",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp
                ),
                color = primaryColor
            )
        }
    }
}

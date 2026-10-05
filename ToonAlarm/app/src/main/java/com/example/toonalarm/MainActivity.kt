package com.example.toonalarm

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AlarmScreen() } }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
    }
}

@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val p = remember { Alarms.prefs(context) }
    var hour by remember { mutableIntStateOf(p.getInt("hour", 7)) }
    var minute by remember { mutableIntStateOf(p.getInt("minute", 0)) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var demo by remember { mutableStateOf(false) }
    var nightOverride by remember { mutableStateOf<Boolean?>(null) }
    val ringing by Alarms.ringing.collectAsState()
    val awake = ringing || demo
    val night = nightOverride ?: (now.hour >= 22 || now.hour < 7)
    var status by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    LaunchedEffect(demo) { if (demo) { delay(6500); demo = false } }
    val background = if (night && !awake) listOf(Color(0xFF10152E), Color(0xFF32335F)) else listOf(Color(0xFFFFE8CB), Color(0xFFFFC4A0))
    Surface { Column(Modifier.fillMaxSize().background(Brush.verticalGradient(background)).systemBarsPadding()
        .verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val ink = if (night && !awake) Color(0xFFFFE6BE) else Color(0xFF503146)
        Text("СОНЯ", color = ink, fontSize = 34.sp, letterSpacing = 8.sp)
        Text(if (awake) "Доброе утро! Пора жить приключения!" else if (night) "Тсс… я набираюсь сил" else "Жду нашего следующего утра", color = ink)
        ClockCharacter(now, night && !awake, awake, Modifier.fillMaxWidth().height(350.dp))
        Text("%02d:%02d:%02d".format(now.hour, now.minute, now.second), color = ink, fontSize = 30.sp)
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E9))) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Наше следующее приключение", color = Color(0xFF503146))
                TextButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }) { Text("%02d:%02d".format(hour, minute), fontSize = 38.sp) }
                Button(onClick = {
                    if (!Alarms.allowed(context)) {
                        status = "Разрешите точные будильники, вернитесь и нажмите ещё раз"
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    } else try {
                        Alarms.schedule(context, hour, minute)
                        status = "Будильник: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.getLong("at", 0)))
                    } catch (_: SecurityException) { status = "Нет разрешения на точный будильник" }
                }, Modifier.fillMaxWidth()) { Text("Завести будильник") }
                TextButton(onClick = { Alarms.cancel(context); status = "Будильник отменён" }) { Text("Отменить") }
                if (status.isNotEmpty()) Text(status)
                else if (p.getBoolean("enabled", false)) Text("Назначен: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.getLong("at", 0))))
            }
        }
        if (ringing) {
            Button(onClick = { context.stopService(Intent(context, RingingService::class.java)) }, Modifier.fillMaxWidth()) { Text("Я проснулся! Выключить") }
            TextButton(onClick = {
                try {
                    Alarms.schedule(context, hour, minute, System.currentTimeMillis() + 5 * 60 * 1000L)
                    context.stopService(Intent(context, RingingService::class.java))
                    status = "Ещё 5 минут…"
                } catch (_: SecurityException) { Toast.makeText(context, "Разрешите точные будильники", Toast.LENGTH_LONG).show() }
            }) { Text("Ещё 5 минут") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { nightOverride = !night }) { Text(if (night) "☀ День" else "☾ Ночь", color = ink) }
            TextButton(onClick = { demo = !demo }) { Text(if (demo) "Остановить" else "Показать пробуждение", color = ink) }
        }
        Text("Ночной сон: 22:00–07:00 • Демонстрация без звука", color = ink, fontSize = 12.sp)
    } }
}

@Composable
fun ClockCharacter(time: LocalTime, sleeping: Boolean, waking: Boolean, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "character")
    val breath by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathing")
    val phase by loop.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(650, easing = LinearEasing)), label = "ring")
    val eyes by animateFloatAsState(if (sleeping) 0f else 1f, tween(650), label = "eyes")
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(waking) { reveal.snapTo(0f); if (waking) reveal.animateTo(1f, tween(900)) }
    Canvas(modifier) {
        val unit = min(size.width / 360f, size.height / 350f)
        translate(size.width / 2, size.height / 2 + 16 * unit) {
            scale(unit, unit, Offset.Zero) {
                val jump = if (waking) -abs(sin(phase / 2)) * 26 * reveal.value else 0f
                drawOval(Color.Black.copy(alpha = .12f), Offset(-105f, 118f), Size(210f, 21f))
                translate(0f, jump) {
                    rotate(if (waking) sin(phase * 3) * 7 * reveal.value else if (sleeping) sin(breath * PI).toFloat() * 1.2f else 0f, Offset.Zero) {
                        scale(1f + if (sleeping) breath * .025f else 0f, 1f + if (sleeping) breath * .045f else 0f, Offset(0f, 110f)) {
                            val outline = Color(0xFF503146)
                            fun line(a: Offset, b: Offset, width: Float = 8f, color: Color = outline) = drawLine(color, a, b, width, StrokeCap.Round)
                            line(Offset(-68f, 80f), Offset(-86f, 118f), 17f)
                            line(Offset(68f, 80f), Offset(86f, 118f), 17f)
                            line(Offset(-56f, -87f), Offset(-71f, -119f))
                            line(Offset(56f, -87f), Offset(71f, -119f))
                            for (x in listOf(-72f, 72f)) {
                                rotate(if (waking) sin(phase * 4) * 12 else if (x < 0) -20f else 20f, Offset(x, -118f)) {
                                    drawOval(outline, Offset(x - 42, -155f), Size(84f, 48f))
                                    drawOval(Color(0xFFFFC44D), Offset(x - 36, -151f), Size(72f, 38f))
                                    line(Offset(x - 42, -116f), Offset(x + 42, -116f), 7f)
                                }
                            }
                            line(Offset(0f, -109f), Offset(if (waking) sin(phase * 4) * 14 else 0f, -151f), 5f)
                            drawCircle(outline, 11f, Offset(0f, -151f))
                            drawCircle(outline, 111f)
                            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDB71), Color(0xFFFF9B58))), 104f)
                            drawCircle(Color(0xFFFFF5DA), 89f)
                            drawCircle(outline.copy(alpha = .2f), 89f, style = Stroke(2f))
                            repeat(12) { i ->
                                val a = i * PI / 6
                                val v = Offset(sin(a).toFloat(), -cos(a).toFloat())
                                line(v * 76f, v * (if (i % 3 == 0) 65f else 71f), if (i % 3 == 0) 4f else 2f)
                            }
                            fun hand(degrees: Float, length: Float, width: Float, color: Color) {
                                val a = degrees * PI / 180
                                line(Offset.Zero, Offset(sin(a).toFloat() * length, -cos(a).toFloat() * length), width, color)
                            }
                            hand((time.hour % 12 + time.minute / 60f) * 30f, 43f, 7f, outline)
                            hand((time.minute + time.second / 60f) * 6f, 62f, 4f, outline)
                            hand(time.second * 6f, 70f, 2f, Color(0xFFE76667))
                            drawCircle(Color(0xFFE76667), 7f)
                            for (x in listOf(-35f, 35f)) {
                                val lid = Path().apply { moveTo(x - 17, -24f); quadraticBezierTo(x, -10f, x + 17, -24f) }
                                if (eyes < .1f) drawPath(lid, outline, style = Stroke(4f, cap = StrokeCap.Round))
                                else {
                                    drawOval(outline, Offset(x - 18, -39f), Size(36f, 32f * eyes))
                                    drawOval(Color.White, Offset(x - 15, -37f), Size(30f, 28f * eyes))
                                    drawOval(outline, Offset(x - 5, -33f), Size(13f, 21f * eyes))
                                    drawCircle(Color.White, 3f, Offset(x + 1, -29f))
                                }
                                drawOval(Color(0xFFFF8993).copy(alpha = .55f), Offset(x - 20, 26f), Size(30f, 15f))
                            }
                            val smile = Path().apply { moveTo(-20f, 37f); quadraticBezierTo(0f, if (waking) 69f else 54f, 20f, 37f) }
                            drawPath(smile, outline, style = Stroke(4f, cap = StrokeCap.Round))
                            if (sleeping) {
                                drawCircle(Color.White.copy(alpha = .35f), 7 + breath * 5, Offset(26f, 45f))
                                drawContext.canvas.nativeCanvas.drawText("z Z", 103f, -78f - breath * 14, android.graphics.Paint().apply {
                                    color = android.graphics.Color.rgb(237, 221, 255); textSize = 23f; isAntiAlias = true
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.projectx.script.impl.cryptic.harvesthollow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One frame of the panel, built on the game thread; the panel draws on the render thread and never reads the game. */
data class HollowHud(
    val activity: String = "",
    val status: String = "Starting",
    val runtime: String = "00:00:00",
    val meter: HudMeter? = null,
    val tiles: List<HudTile> = emptyList(),
)

data class HudMeter(val title: String, val value: String, val fraction: Float)

data class HudTile(val title: String, val value: String, val icon: HollowIcon)

/** What an activity hands the panel each refresh. */
data class HollowReport(val status: String, val meter: HudMeter?, val tiles: List<HudTile>)

enum class HollowIcon(val draw: DrawScope.() -> Unit) {
    FLAME({ flameIcon() }),
    LOGS({ logsIcon() }),
    PUMPKIN({ jackOLantern(glow = false) }),
    HOURGLASS({ hourglassIcon() }),
    SKULL({ skullIcon() }),
    CANDLE({ candleIcon() }),
}

private object Spooky {
    val night = Color(0xFF09060F)
    val dusk = Color(0xFF1B0F2B)
    val haze = Color(0xFF2E1A47)
    val witch = Color(0xFF8A4FCB)
    val pumpkin = Color(0xFFFF7518)
    val ember = Color(0xFFFFB648)
    val candle = Color(0xFFFFE27A)
    val pumpkinDeep = Color(0xFFA63C06)
    val stem = Color(0xFF4E6B2A)
    val slime = Color(0xFF9BE15D)
    val bone = Color(0xFFEFE6D2)
    val fog = Color(0xFFA79BBE)
    val moon = Color(0xFFF6EBC4)
    val silhouette = Color(0xFF0B0613)
    val bark = Color(0xFF6B4423)
    val track = Color(0xFF07040C)
}

private object HollowFonts {
    val creepy: FontFamily by lazy {
        FontFamily(
            Font(
                identity = "hollow-creepster",
                data = HollowFonts::class.java.getResourceAsStream("fonts/Creepster-Regular.ttf")!!.use { it.readBytes() },
                weight = FontWeight.Normal,
            ),
        )
    }
}

private val label = TextStyle(fontSize = 10.sp, letterSpacing = 1.3.sp, color = Spooky.fog, fontWeight = FontWeight.SemiBold)
private val figure = TextStyle(fontSize = 15.sp, color = Spooky.bone, fontWeight = FontWeight.Bold)
private val caption = TextStyle(fontSize = 11.sp, color = Spooky.fog)

@Composable
fun HollowPanel(hud: HollowHud) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(hud)
        hud.meter?.let { Meter(it) }
        if (hud.tiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { hud.tiles.forEach { Tile(it) } }
        }
        Footer(hud)
    }
}

@Composable
private fun Header(hud: HollowHud) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().height(108.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Spooky.night, Spooky.dusk, Spooky.haze)))
            .border(1.dp, Brush.verticalGradient(listOf(Spooky.pumpkin.copy(alpha = 0.65f), Spooky.witch.copy(alpha = 0.25f))), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) { graveyardNight() }
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(68.dp) { jackOLantern(glow = true) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(
                    "Harvest Hollow",
                    style = TextStyle(
                        fontFamily = HollowFonts.creepy, fontSize = 30.sp,
                        brush = Brush.verticalGradient(listOf(Spooky.candle, Spooky.pumpkin, Spooky.pumpkinDeep)),
                        shadow = Shadow(Spooky.pumpkin.copy(alpha = 0.55f), Offset(0f, 0f), 14f),
                    ),
                )
                ActivityChip(hud.activity)
            }
            Column(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Spooky.night.copy(alpha = 0.72f))
                    .border(1.dp, Spooky.witch.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(12.dp) { hourglassIcon() }
                    BasicText(hud.runtime, style = figure.copy(fontSize = 13.sp, color = Spooky.moon))
                }
                BasicText("RUNTIME", style = label.copy(fontSize = 8.sp))
            }
        }
    }
}

@Composable
private fun ActivityChip(activity: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Spooky.night.copy(alpha = 0.75f))
            .border(1.dp, Spooky.slime.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(6.dp)) {
            drawCircle(Spooky.slime.copy(alpha = 0.35f), radius = size.minDimension)
            drawCircle(Spooky.slime)
        }
        BasicText(activity.uppercase(), style = label.copy(fontSize = 9.sp, letterSpacing = 1.1.sp, color = Spooky.slime))
    }
}

@Composable
private fun Meter(meter: HudMeter) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(meter.title.uppercase(), style = label, modifier = Modifier.weight(1f))
            BasicText(meter.value, style = figure.copy(fontSize = 12.sp, color = Spooky.ember))
        }
        GooBar(meter.fraction)
    }
}

/** A channel of glowing pumpkin goo that drips off its underside wherever it has filled. */
@Composable
private fun GooBar(fraction: Float) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600))
    Canvas(Modifier.fillMaxWidth().height(20.dp)) {
        val barHeight = size.height * 0.55f
        val radius = CornerRadius(barHeight / 2)
        val filled = size.width * shown
        for (x in DRIPS) {
            val at = size.width * x
            if (at > filled - 6f) continue
            val length = barHeight * (0.55f + (x * 7f) % 0.45f)
            val drip = Brush.verticalGradient(listOf(Spooky.pumpkinDeep, Spooky.pumpkin), barHeight * 0.5f, barHeight * 0.5f + length)
            drawRoundRect(drip, Offset(at - 3.5f, barHeight * 0.5f), Size(7f, length), CornerRadius(3.5f))
            drawCircle(Spooky.pumpkin, radius = 4.6f, center = Offset(at, barHeight * 0.5f + length))
            drawCircle(Spooky.ember.copy(alpha = 0.7f), radius = 1.4f, center = Offset(at - 1.4f, barHeight * 0.5f + length - 1.6f))
        }
        drawRoundRect(Spooky.track, size = Size(size.width, barHeight), cornerRadius = radius)
        if (filled > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Spooky.pumpkinDeep, Spooky.pumpkin, Spooky.ember), endX = size.width),
                size = Size(filled, barHeight), cornerRadius = radius,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.2f), topLeft = Offset(2f, 1.5f),
                size = Size((filled - 4f).coerceAtLeast(0f), barHeight * 0.35f), cornerRadius = radius,
            )
        }
        drawRoundRect(Spooky.witch.copy(alpha = 0.55f), size = Size(size.width, barHeight), cornerRadius = radius, style = Stroke(1f))
    }
}

private val DRIPS = listOf(0.07f, 0.19f, 0.34f, 0.47f, 0.61f, 0.76f, 0.9f)

@Composable
private fun RowScope.Tile(tile: HudTile) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.weight(1f).clip(shape)
            .background(Brush.verticalGradient(listOf(Spooky.haze.copy(alpha = 0.7f), Spooky.night.copy(alpha = 0.75f))))
            .border(1.dp, Brush.verticalGradient(listOf(Spooky.pumpkin.copy(alpha = 0.45f), Spooky.witch.copy(alpha = 0.15f))), shape)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(20.dp, tile.icon.draw)
        BasicText(tile.value, style = figure, maxLines = 1)
        BasicText(tile.title.uppercase(), style = label.copy(fontSize = 8.sp, letterSpacing = 0.9.sp), maxLines = 1)
    }
}

@Composable
private fun Footer(hud: HollowHud) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Spooky.night.copy(alpha = 0.6f))
            .border(1.dp, Spooky.witch.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(16.dp) { ghostIcon() }
        BasicText(
            hud.status, style = caption.copy(color = Spooky.bone), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicText("Happy Hallowe'en", style = TextStyle(fontFamily = HollowFonts.creepy, fontSize = 13.sp, color = Spooky.slime))
    }
}

@Composable
private fun Icon(size: Dp, draw: DrawScope.() -> Unit) = Canvas(Modifier.size(size), onDraw = draw)

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000f)
    else -> "$n"
}

fun perHour(count: Int, hours: Double): Int = if (hours < 1.0 / 360) 0 else (count / hours).toInt()

fun clock(seconds: Long): String = "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)

// Drawn art: all vector, sized to its box, so it stays sharp at any overlay scale and ships in no file.

private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private val STARS = listOf(
    0.30f to 0.12f, 0.41f to 0.30f, 0.52f to 0.08f, 0.60f to 0.22f, 0.70f to 0.10f,
    0.76f to 0.38f, 0.95f to 0.15f, 0.47f to 0.48f, 0.66f to 0.52f, 0.36f to 0.62f,
)

private fun DrawScope.graveyardNight() {
    STARS.forEachIndexed { i, (x, y) -> drawCircle(Spooky.bone.copy(alpha = 0.25f + (i % 3) * 0.15f), 1f + (i % 2) * 0.6f, p(x, y)) }

    val moon = p(0.71f, 0.3f)
    val moonRadius = size.height * 0.2f
    drawCircle(Brush.radialGradient(listOf(Spooky.moon.copy(alpha = 0.35f), Color.Transparent), moon, moonRadius * 2.6f), moonRadius * 2.6f, moon)
    drawCircle(Spooky.moon, moonRadius, moon)
    drawCircle(Spooky.moon.copy(red = 0.86f, green = 0.8f, blue = 0.62f), moonRadius * 0.22f, moon + Offset(-moonRadius * 0.35f, -moonRadius * 0.2f))
    drawCircle(Spooky.moon.copy(red = 0.86f, green = 0.8f, blue = 0.62f), moonRadius * 0.14f, moon + Offset(moonRadius * 0.3f, moonRadius * 0.35f))

    bat(p(0.62f, 0.22f), size.height * 0.09f)
    bat(p(0.8f, 0.14f), size.height * 0.06f)
    bat(p(0.77f, 0.5f), size.height * 0.05f)

    val hills = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height * 0.84f)
        quadraticTo(size.width * 0.25f, size.height * 0.72f, size.width * 0.5f, size.height * 0.82f)
        quadraticTo(size.width * 0.75f, size.height * 0.9f, size.width, size.height * 0.76f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(hills, Spooky.silhouette)
    tombstone(0.58f, 0.8f, 0.035f)
    tombstone(0.68f, 0.84f, 0.028f)
    tombstone(0.86f, 0.78f, 0.032f)
    cross(0.77f, 0.81f)
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Spooky.witch.copy(alpha = 0.12f), Color.Transparent), size.height * 0.62f, size.height),
        topLeft = Offset(0f, size.height * 0.62f), size = Size(size.width, size.height * 0.38f),
    )
}

private fun DrawScope.bat(centre: Offset, span: Float) {
    val (x, y) = centre
    val wing = Path().apply {
        moveTo(x, y)
        quadraticTo(x - span * 0.45f, y - span * 0.55f, x - span, y - span * 0.2f)
        quadraticTo(x - span * 0.8f, y + span * 0.05f, x - span * 0.62f, y + span * 0.12f)
        quadraticTo(x - span * 0.5f, y - span * 0.02f, x - span * 0.36f, y + span * 0.16f)
        quadraticTo(x - span * 0.22f, y + span * 0.02f, x, y + span * 0.12f)
        quadraticTo(x + span * 0.22f, y + span * 0.02f, x + span * 0.36f, y + span * 0.16f)
        quadraticTo(x + span * 0.5f, y - span * 0.02f, x + span * 0.62f, y + span * 0.12f)
        quadraticTo(x + span * 0.8f, y + span * 0.05f, x + span, y - span * 0.2f)
        quadraticTo(x + span * 0.45f, y - span * 0.55f, x, y)
        close()
    }
    drawPath(wing, Spooky.silhouette)
    drawCircle(Spooky.silhouette, span * 0.16f, Offset(x, y - span * 0.02f))
}

private fun DrawScope.tombstone(x: Float, ground: Float, width: Float) {
    val w = size.width * width
    val h = w * 1.5f
    val left = size.width * x - w / 2
    val bottom = size.height * ground
    drawRoundRect(
        Spooky.haze, Offset(left, bottom - h), Size(w, h),
        CornerRadius(w / 2, w / 2),
    )
    drawLine(Spooky.silhouette.copy(alpha = 0.7f), Offset(left + w * 0.3f, bottom - h * 0.55f), Offset(left + w * 0.7f, bottom - h * 0.55f), 1f)
}

private fun DrawScope.cross(x: Float, ground: Float) {
    val cx = size.width * x
    val bottom = size.height * ground
    val h = size.height * 0.16f
    drawLine(Spooky.haze, Offset(cx, bottom), Offset(cx, bottom - h), 2.4f, StrokeCap.Round)
    drawLine(Spooky.haze, Offset(cx - h * 0.3f, bottom - h * 0.7f), Offset(cx + h * 0.3f, bottom - h * 0.7f), 2.4f, StrokeCap.Round)
}

fun DrawScope.jackOLantern(glow: Boolean) {
    if (glow) {
        drawCircle(
            Brush.radialGradient(listOf(Spooky.ember.copy(alpha = 0.45f), Color.Transparent), p(0.5f, 0.6f), size.minDimension * 0.62f),
            size.minDimension * 0.62f, p(0.5f, 0.6f),
        )
    }
    drawRoundRect(Spooky.stem, p(0.45f, 0.13f), Size(size.width * 0.11f, size.height * 0.2f), CornerRadius(size.width * 0.03f))
    drawOval(Spooky.pumpkinDeep, p(0.08f, 0.28f), Size(size.width * 0.5f, size.height * 0.64f))
    drawOval(Spooky.pumpkinDeep, p(0.42f, 0.28f), Size(size.width * 0.5f, size.height * 0.64f))
    drawOval(
        Brush.verticalGradient(listOf(Spooky.ember, Spooky.pumpkin, Spooky.pumpkinDeep), size.height * 0.28f, size.height * 0.92f),
        p(0.24f, 0.26f), Size(size.width * 0.52f, size.height * 0.68f),
    )
    drawOval(Spooky.pumpkin, p(0.13f, 0.31f), Size(size.width * 0.3f, size.height * 0.58f), alpha = 0.85f)
    drawOval(Spooky.pumpkin, p(0.57f, 0.31f), Size(size.width * 0.3f, size.height * 0.58f), alpha = 0.85f)

    val face = Spooky.candle
    drawPath(Path().apply { moveTo(p(0.28f, 0.58f)); lineTo(p(0.36f, 0.43f)); lineTo(p(0.44f, 0.58f)); close() }, face)
    drawPath(Path().apply { moveTo(p(0.56f, 0.58f)); lineTo(p(0.64f, 0.43f)); lineTo(p(0.72f, 0.58f)); close() }, face)
    drawPath(Path().apply { moveTo(p(0.46f, 0.66f)); lineTo(p(0.5f, 0.6f)); lineTo(p(0.54f, 0.66f)); close() }, face)
    drawPath(
        Path().apply {
            moveTo(p(0.24f, 0.7f))
            lineTo(p(0.32f, 0.77f)); lineTo(p(0.37f, 0.71f)); lineTo(p(0.44f, 0.79f)); lineTo(p(0.5f, 0.72f))
            lineTo(p(0.56f, 0.79f)); lineTo(p(0.63f, 0.71f)); lineTo(p(0.68f, 0.77f)); lineTo(p(0.76f, 0.7f))
            quadraticTo(size.width * 0.5f, size.height * 0.95f, size.width * 0.24f, size.height * 0.7f)
            close()
        },
        face,
    )
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)

private fun DrawScope.flameIcon() {
    val outer = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.04f)
        cubicTo(size.width * 0.95f, size.height * 0.45f, size.width * 0.85f, size.height * 0.95f, size.width * 0.5f, size.height * 0.96f)
        cubicTo(size.width * 0.15f, size.height * 0.95f, size.width * 0.05f, size.height * 0.5f, size.width * 0.5f, size.height * 0.04f)
        close()
    }
    drawPath(outer, Brush.verticalGradient(listOf(Spooky.ember, Spooky.pumpkin, Spooky.pumpkinDeep)))
    val inner = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.4f)
        cubicTo(size.width * 0.75f, size.height * 0.65f, size.width * 0.68f, size.height * 0.92f, size.width * 0.5f, size.height * 0.92f)
        cubicTo(size.width * 0.32f, size.height * 0.92f, size.width * 0.25f, size.height * 0.65f, size.width * 0.5f, size.height * 0.4f)
        close()
    }
    drawPath(inner, Spooky.candle)
}

private fun DrawScope.logsIcon() {
    listOf(0.58f, 0.32f).forEach { y ->
        drawRoundRect(Spooky.bark, p(0.08f, y), Size(size.width * 0.84f, size.height * 0.24f), CornerRadius(size.height * 0.12f))
        drawCircle(Color(0xFFD9A066), size.height * 0.1f, p(0.84f, y + 0.12f))
        drawCircle(Spooky.bark, size.height * 0.04f, p(0.84f, y + 0.12f))
    }
}

private fun DrawScope.hourglassIcon() {
    val frame = Spooky.witch
    drawLine(frame, p(0.18f, 0.08f), p(0.82f, 0.08f), size.height * 0.1f, StrokeCap.Round)
    drawLine(frame, p(0.18f, 0.92f), p(0.82f, 0.92f), size.height * 0.1f, StrokeCap.Round)
    drawPath(Path().apply { moveTo(p(0.26f, 0.14f)); lineTo(p(0.74f, 0.14f)); lineTo(p(0.5f, 0.5f)); close() }, Spooky.fog.copy(alpha = 0.5f))
    drawPath(Path().apply { moveTo(p(0.5f, 0.5f)); lineTo(p(0.74f, 0.86f)); lineTo(p(0.26f, 0.86f)); close() }, Spooky.pumpkin)
}

private fun DrawScope.skullIcon() {
    drawCircle(Spooky.bone, size.width * 0.36f, p(0.5f, 0.42f))
    drawRoundRect(Spooky.bone, p(0.32f, 0.55f), Size(size.width * 0.36f, size.height * 0.3f), CornerRadius(size.width * 0.06f))
    drawCircle(Spooky.night, size.width * 0.1f, p(0.36f, 0.45f))
    drawCircle(Spooky.night, size.width * 0.1f, p(0.64f, 0.45f))
    drawPath(Path().apply { moveTo(p(0.5f, 0.56f)); lineTo(p(0.45f, 0.66f)); lineTo(p(0.55f, 0.66f)); close() }, Spooky.night)
    listOf(0.42f, 0.5f, 0.58f).forEach { x -> drawLine(Spooky.night, p(x, 0.72f), p(x, 0.84f), 1.2f) }
}

private fun DrawScope.candleIcon() {
    drawRoundRect(Spooky.bone, p(0.34f, 0.42f), Size(size.width * 0.32f, size.height * 0.52f), CornerRadius(size.width * 0.05f))
    drawRoundRect(Spooky.bone, p(0.58f, 0.42f), Size(size.width * 0.08f, size.height * 0.2f), CornerRadius(size.width * 0.04f))
    drawLine(Spooky.night, p(0.5f, 0.42f), p(0.5f, 0.34f), 1.2f)
    drawOval(Brush.verticalGradient(listOf(Spooky.candle, Spooky.pumpkin)), p(0.41f, 0.06f), Size(size.width * 0.18f, size.height * 0.3f))
}

private fun DrawScope.ghostIcon() {
    val body = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.95f)
        lineTo(size.width * 0.18f, size.height * 0.45f)
        cubicTo(size.width * 0.18f, size.height * 0.02f, size.width * 0.82f, size.height * 0.02f, size.width * 0.82f, size.height * 0.45f)
        lineTo(size.width * 0.82f, size.height * 0.95f)
        lineTo(size.width * 0.7f, size.height * 0.82f)
        lineTo(size.width * 0.58f, size.height * 0.95f)
        lineTo(size.width * 0.5f, size.height * 0.82f)
        lineTo(size.width * 0.42f, size.height * 0.95f)
        lineTo(size.width * 0.3f, size.height * 0.82f)
        close()
    }
    drawPath(body, Spooky.bone.copy(alpha = 0.92f))
    drawOval(Spooky.night, p(0.32f, 0.34f), Size(size.width * 0.12f, size.height * 0.18f))
    drawOval(Spooky.night, p(0.56f, 0.34f), Size(size.width * 0.12f, size.height * 0.18f))
}

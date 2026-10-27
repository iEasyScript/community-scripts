package com.projectx.script.impl.devin.aiobozocombat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectx.ui.compose.theme.LocalType
import kotlin.math.abs

/** The game tick as a sweeping bar; [tick] adds the raw tick number under it. */
@Composable
fun Metronome(phase: () -> Float, tick: (() -> Int)?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(METRONOME_TRACK)) {
            Box(Modifier.fillMaxWidth(phase().coerceIn(0f, 1f)).fillMaxHeight().background(METRONOME_FILL))
        }
        if (tick != null) BasicText("${tick()}", style = LocalType.current.dataSmall.copy(color = TICK_TEXT))
    }
}

/**
 * The queue as a strip of action-bar tiles: what was just used, the next press ringed in gold, and what comes
 * after, with one caption under the rail saying what to do and when.
 */
@Composable
fun QueueRail(model: QueueModel, phase: () -> Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).background(RAIL).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.used.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.width(GAP))
                Slot(entry, QueueKind.USED)
            }
            if (model.used.isNotEmpty()) SectionBreak()
            Slot(model.next, QueueKind.NEXT)
            if (model.upcoming.isNotEmpty()) SectionBreak()
            model.upcoming.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.width(GAP))
                Slot(entry, QueueKind.UPCOMING)
            }
        }
        Caption(model.next, phase)
    }
}

@Composable
private fun Slot(entry: QueueEntry?, kind: QueueKind) {
    val highlighted = kind == QueueKind.NEXT
    val interrupt = highlighted && entry?.urgency == Urgency.INTERRUPT
    val shape = RoundedCornerShape(3.dp)
    val fill = when {
        entry == null -> CARD_EMPTY
        entry.actionKind == ActionKind.ITEM -> CARD_ITEM
        entry.actionKind == ActionKind.PRAYER -> CARD_PRAYER
        entry.actionKind == ActionKind.CUE -> CARD_CUE
        kind == QueueKind.USED -> CARD_USED
        kind == QueueKind.NEXT -> CARD_NEXT
        else -> CARD_UPCOMING
    }
    val edge = when {
        interrupt -> ALERT
        highlighted -> GOLD
        else -> CARD_EDGE
    }
    Box(
        Modifier.size(TILE).clip(shape).background(fill).border(if (highlighted) 2.dp else 1.dp, edge, shape).padding(3.dp),
    ) {
        if (entry == null) return@Box
        val strength = when (kind) {
            QueueKind.USED -> USED_ALPHA
            QueueKind.NEXT -> 1f
            QueueKind.UPCOMING -> entry.confidence.coerceIn(MIN_FADE, 1f)
        }
        val small = LocalType.current.dataSmall
        BasicText(
            abbreviate(entry.name).joinToString("\n"),
            style = small.copy(color = KEY.copy(alpha = strength), fontSize = 9.sp, lineHeight = 11.sp, textAlign = TextAlign.Center),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )
        entry.keybind?.let { key ->
            BasicText(
                key,
                style = small.copy(color = (if (highlighted) GOLD_TEXT else KEY).copy(alpha = strength), fontSize = 9.sp),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun SectionBreak() {
    Spacer(Modifier.width(SECTION_GAP))
    Box(Modifier.width(3.dp).height(TILE).background(DIVIDER))
    Spacer(Modifier.width(SECTION_GAP))
}

/**
 * Counts down in ticks because presses land on tick boundaries. Only an ability counts down; a prayer, an item
 * or a cue is wanted now.
 */
@Composable
private fun Caption(next: QueueEntry?, phase: () -> Float) {
    val line = when {
        next == null -> " "
        next.urgency == Urgency.INTERRUPT -> "${next.name} - ${next.reason ?: "now"}"
        next.actionKind == ActionKind.PRAYER -> "pray ${next.name}"
        next.actionKind == ActionKind.ITEM -> "use ${next.name}"
        next.actionKind == ActionKind.CUE -> next.name
        else -> "${next.name} in ${next.readyInTicks.coerceAtLeast(0)}"
    }
    val colour = when {
        next == null -> CARD_EMPTY
        next.urgency == Urgency.INTERRUPT -> ALERT_TEXT
        next.actionKind == ActionKind.CUE -> pulsed(CUE_TEXT, phase())
        next.timing == Timing.NOW -> pulsed(READY, phase())
        else -> WAIT
    }
    BasicText(line, style = LocalType.current.bodyStrong.copy(color = colour))
}

/** Drops the dose suffix and connecting words so two short lines carry the parts that identify the action. */
private fun abbreviate(name: String): List<String> {
    val words = name.replace(DOSE_SUFFIX, "").split(' ').filter { it.isNotBlank() }
    val meaningful = words.filter { it.lowercase() !in CONNECTING_WORDS }
    return (meaningful.ifEmpty { words }).take(TEXT_TILE_LINES).map { it.take(TEXT_TILE_CHARS) }
}

/** Breathes the ready cue in time with the tick so "press now" is visible peripherally. */
private fun pulsed(color: Color, phase: Float): Color =
    color.copy(alpha = color.alpha * (PULSE_FLOOR + (1f - PULSE_FLOOR) * abs(1f - 2f * phase)))

private val TILE = 44.dp
private val GAP = 2.dp
private val SECTION_GAP = 8.dp
private const val MIN_FADE = 0.4f
private const val USED_ALPHA = 0.45f
private const val TEXT_TILE_CHARS = 6
private const val TEXT_TILE_LINES = 2
private const val PULSE_FLOOR = 0.45f
private val DOSE_SUFFIX = Regex("\\s*\\(\\d+\\)\\s*$")
private val CONNECTING_WORDS = setOf("of", "the", "a", "an", "and")

private val METRONOME_FILL = Color(0xFFC9A227)
private val METRONOME_TRACK = Color(0xFF241E19)
private val TICK_TEXT = Color(0xFF6E6152)
private val RAIL = Color(0xFF5C4A36)
private val CARD_USED = Color(0xFF221B14)
private val CARD_NEXT = Color(0xFF453218)
private val CARD_UPCOMING = Color(0xFF2B2219)
private val CARD_EMPTY = Color(0xFF241D15)
private val CARD_ITEM = Color(0xFF1F2A22)
private val CARD_PRAYER = Color(0xFF1B2436)
private val CARD_CUE = Color(0xFF33241B)
private val CUE_TEXT = Color(0xFFFFC24D)
private val CARD_EDGE = Color(0xFF52422F)
private val DIVIDER = Color(0xFF241D15)
private val GOLD = Color(0xFFD4A02A)
private val GOLD_TEXT = Color(0xFFFFE9A8)
private val KEY = Color(0xFFF2ECE2)
private val READY = Color(0xFF5BFF8F)
private val WAIT = Color(0xFFC7B9A4)
private val ALERT = Color(0xFFD9543B)
private val ALERT_TEXT = Color(0xFFFF9A7A)

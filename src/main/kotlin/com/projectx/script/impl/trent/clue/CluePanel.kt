package com.projectx.script.impl.trent.clue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.projectx.ui.compose.components.Card
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.Pill
import com.projectx.ui.compose.theme.LocalType
import com.projectx.ui.compose.theme.Palette

data class ClueHud(val autoSolve: Boolean = false, val steps: List<ClueReadout> = emptyList())

private val solving = Color(0xFF5CC8F0)

private val ClueTone.color: Color
    get() = when (this) {
        ClueTone.WORKING -> solving
        ClueTone.DONE -> Palette.running
        ClueTone.FAILED -> Palette.stop
        ClueTone.UNSURE -> Palette.amber
    }

@Composable
fun CluePanel(hud: ClueHud) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Hint("Auto-solve")
            if (hud.autoSolve) Pill("On", Palette.running, dot = true) else Pill("Off", Palette.muted, dot = true)
        }
        if (hud.steps.isEmpty()) Hint("No clue step on screen. Open a puzzle, compass or scan clue.")
        hud.steps.forEach { Step(it) }
    }
}

@Composable
private fun Step(step: ClueReadout) {
    val type = LocalType.current
    Card(padding = 12) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(step.step, style = type.heading, modifier = Modifier.weight(1f))
            Pill(step.status, step.tone.color, dot = true)
        }
        if (step.lines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                step.lines.forEach { BasicText(it, style = type.data.copy(color = Palette.text)) }
            }
        }
    }
}

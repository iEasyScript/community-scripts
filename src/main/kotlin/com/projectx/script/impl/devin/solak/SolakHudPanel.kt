package com.projectx.script.impl.devin.solak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.projectx.ui.backend.dsl.utils.ImGuiColors
import com.projectx.ui.compose.components.Divider
import com.projectx.ui.compose.components.Pill
import com.projectx.ui.compose.components.ProgressBar
import com.projectx.ui.compose.theme.LocalType
import com.projectx.ui.compose.theme.Palette
import com.projectx.ui.compose.theme.fromImGuiColor

data class SolakHudData(
    val headline: String,
    val headlineAccent: Int,
    val badge: Pair<String, Int>?,
    val instruction: Pair<String, Int>?,
    val note: Pair<String, Int>?,
    val lines: List<Pair<String, Int>>,
    val bars: List<Bar>
) {
    /** A null [fraction] means the maximum is unknown, so no bar is drawn - only the raw value. */
    data class Bar(val label: String, val value: String, val fraction: Float?, val color: Int)
}

object SolakHudColors {
    val NEUTRAL_ACCENT = ImGuiColors.rgba(104, 148, 82, 235)
    val CRITICAL = ImGuiColors.rgba(226, 44, 74, 240)
    val WARNING = ImGuiColors.rgba(214, 142, 30, 235)
    val INFO = ImGuiColors.rgba(64, 132, 96, 225)
    val GOOD = ImGuiColors.rgba(120, 226, 140, 255)
    val MUTED = ImGuiColors.rgba(150, 168, 142, 255)
    val BOSS_BAR = ImGuiColors.rgba(198, 82, 62, 240)
    val LIMB_BAR = ImGuiColors.rgba(126, 178, 96, 240)
    val TRACKED_BAR = ImGuiColors.rgba(226, 186, 76, 240)
}

@Composable
fun SolakHud(data: SolakHudData) {
    val type = LocalType.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(5.dp)).background(Palette.raised),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(data.headlineAccent.fromImGuiColor()))
            BasicText(
                data.headline,
                style = type.bodyStrong.copy(color = Color.White),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            data.badge?.let { (text, accent) ->
                Box(Modifier.padding(end = 6.dp)) { Pill(text, accent.fromImGuiColor()) }
            }
        }
        data.instruction?.let { (label, accent) ->
            Box(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(5.dp)).background(accent.fromImGuiColor()),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = type.bodyStrong.copy(color = Color.White))
            }
        }
        data.note?.let { (text, color) ->
            BasicText(
                text,
                style = type.body.copy(color = color.fromImGuiColor(), textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for ((text, color) in data.lines) BasicText(text, style = type.body.copy(color = color.fromImGuiColor()))
        if (data.bars.isEmpty()) return@Column
        Divider()
        for (bar in data.bars) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    BasicText(bar.label, style = type.label.copy(color = Palette.muted), modifier = Modifier.weight(1f))
                    BasicText(bar.value, style = type.data.copy(color = Palette.text))
                }
                bar.fraction?.let { ProgressBar(it, color = bar.color.fromImGuiColor(), height = 6.dp) }
            }
        }
    }
}

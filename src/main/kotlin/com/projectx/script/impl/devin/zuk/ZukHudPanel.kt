package com.projectx.script.impl.devin.zuk

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.projectx.ui.backend.dsl.utils.ImGuiColors
import com.projectx.ui.compose.components.Divider
import com.projectx.ui.compose.components.Pill
import com.projectx.ui.compose.theme.LocalType
import com.projectx.ui.compose.theme.Palette
import com.projectx.ui.compose.theme.fromImGuiColor

data class ZukHudData(
    val prayerLabel: String,
    val prayerAccent: Int,
    val mode: Pair<String, Int>?,
    val instruction: Pair<String, Int>?,
    val abilities: String?,
    val sections: List<Section>
) {
    data class Section(val title: String, val tokens: List<Pair<String, Int>>)
}

object ZukHudColors {
    val NEUTRAL_ACCENT = ImGuiColors.rgba(122, 100, 68, 235)
    val SECTION_TEXT = ImGuiColors.rgba(255, 238, 205)
}

private val ABILITY_TEXT = Color(0xFFFFECA0)
private val SECTION_TITLE = Color(0xFFFF981F)

@Composable
fun ZukCoachCard(data: ZukHudData) {
    val type = LocalType.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(5.dp)).background(Palette.raised),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(data.prayerAccent.fromImGuiColor()))
            BasicText(
                data.prayerLabel,
                style = type.bodyStrong.copy(color = Color.White),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            data.mode?.let { (text, accent) ->
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
        data.abilities?.let {
            BasicText(it, style = type.body.copy(color = ABILITY_TEXT, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        }
        if (data.sections.isEmpty()) return@Column
        Divider()
        for (section in data.sections) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(section.title, style = type.label.copy(color = SECTION_TITLE))
                if (section.tokens.isNotEmpty()) {
                    BasicText(
                        buildAnnotatedString {
                            section.tokens.forEachIndexed { i, (token, color) ->
                                if (i > 0) append("   ")
                                withStyle(SpanStyle(color = color.fromImGuiColor())) { append(token) }
                            }
                        },
                        style = type.body,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

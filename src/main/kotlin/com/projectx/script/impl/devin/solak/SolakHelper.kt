package com.projectx.script.impl.devin.solak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectx.game.highlight.EntityHighlight
import com.projectx.game.nxt.entity.npc.NPC
import com.projectx.script.BooleanConfigItem
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigSection
import com.projectx.script.ConfigurableScript
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.ui.compose.components.Hint
import java.util.Locale

@ScriptDescription(
    name = "Solak Helper",
    version = "0.2.0",
    author = "Devin",
    description = "Read-only mechanic HUD for Solak, Guardian of the Grove: Blight stacks, the tracked health bar " +
        "scaled to your team size, Solak's and the limbs' lifepoints, Nature's Blessing readiness, the current " +
        "telegraph read off Solak's animation, and an outline on the priority target. It renders information only - " +
        "it never clicks, walks or presses anything. Phase 1; hazard-tile geometry is not mapped yet.",
    visible = false,
    category = ScriptCategory.BOSSES
)
class SolakHelper : Script(), ConfigurableScript, ComposePanel {

    val displaySection = ConfigSection("Display")
    val showHud = BooleanConfigItem("Mechanic HUD", "The coach card.", true)
    val showEncounterStatus = BooleanConfigItem(
        "Encounter status",
        "Encounter liveness, team size and the rootling / lasher count.",
        true
    )
    val showBlightStacks = BooleanConfigItem("Blight stacks", "Your Blight stack count as the headline figure.", true)
    val showTrackedBar = BooleanConfigItem(
        "Tracked health bar",
        "Whatever the second health bar is following, as a percentage of the team-scaled maximum.",
        true
    )
    val showBossHealth = BooleanConfigItem("Solak & limbs", "Solak's lifepoints and every limb that is alive.", true)
    val showBlessing = BooleanConfigItem("Nature's Blessing", "Whether the blessing and the extra action are available.", true)
    val showTelegraph = BooleanConfigItem("Telegraph", "The current mechanic, read off Solak's animation.", true)
    val showDpsWindow = BooleanConfigItem("DPS window", "Whether a stagger or kneel window is open and which limb to hit.", true)
    val showTargetOutline = BooleanConfigItem(
        "Priority target outline",
        "Outline the exposed Core, else the limbs of the open DPS window, else the rootlings.",
        true
    )
    val debugSection = ConfigSection("Debug & testing", "Diagnostic logging for mapping the encounter.", defaultOpen = false)
    val debugCapture = BooleanConfigItem(
        "Debug capture",
        "Log Solak's animation transitions, limb / core spawns and deaths, varbit changes and your position.",
        false
    )

    private var hud by mutableStateOf<SolakHudData?>(null)

    @Volatile
    private var targets: List<OutlineTarget> = emptyList()

    @Volatile
    private var outlined: Map<Int, Long> = emptyMap()

    override fun onStart() {
        super.onStart()
        SolakArena.reset()
        SolakEncounter.reset()
        SolakCapture.reset()
    }

    override fun onStop() {
        super.onStop()
        clearOutlines()
    }

    override suspend fun loop() {
        SolakArena.refresh()
        val state = runCatching { SolakEncounter.read() }.getOrNull()
        hud = state?.let { runCatching { hudData(it) }.getOrNull() }
        targets = state?.let { runCatching { outlineTargets(it) }.getOrDefault(emptyList()) } ?: emptyList()
        if (debugCapture.value) SolakCapture.tick(state)
        delay(LOOP_DELAY_MS)
    }

    override fun render() = drawOutlines()

    @Composable
    override fun Panel() {
        val data = hud
        when {
            !showHud.value -> Hint("Mechanic HUD is off in settings")
            data == null -> Hint("Waiting for the encounter")
            else -> SolakHud(data)
        }
    }

    private fun hudData(state: SolakState): SolakHudData? {
        val instruction = if (showTelegraph.value) {
            state.telegraph?.let { it.instruction to severityColor(it.severity) }
        } else null
        val note = if (showDpsWindow.value) dpsWindowNote(state) else null
        val lines = ArrayList<Pair<String, Int>>(2)
        if (showEncounterStatus.value) lines += statusLine(state) to SolakHudColors.MUTED
        if (showBlessing.value) lines += blessingLine(state)
        val bars = ArrayList<SolakHudData.Bar>(5)
        val trackedShowsCore = showTrackedBar.value && state.trackedBar?.tracksCore == true
        if (showTrackedBar.value) state.trackedBar?.let { bars += bar(it.label, it.lifepoints, it.maxLifepoints, SolakHudColors.TRACKED_BAR) }
        if (showBossHealth.value) {
            if (state.coreLifepoints > 0 && !trackedShowsCore) {
                bars += bar("BLIGHT-AFFLICTED CORE", state.coreLifepoints, state.coreMaxLifepoints, SolakHudColors.TRACKED_BAR)
            }
            if (state.bossLifepoints > 0) bars += bar("SOLAK", state.bossLifepoints, bossMax(state), SolakHudColors.BOSS_BAR)
            for (limb in state.limbs) bars += bar(limb.limb.label, limb.lifepoints, limb.maxLifepoints, SolakHudColors.LIMB_BAR)
        }
        val headline = headline(state)
        if (headline == null && instruction == null && note == null && lines.isEmpty() && bars.isEmpty()) return null
        return SolakHudData(
            headline = headline ?: "SOLAK",
            headlineAccent = if ((state.blightStacks ?: 0) > 0) SolakHudColors.WARNING else SolakHudColors.NEUTRAL_ACCENT,
            badge = if (showEncounterStatus.value && state.teamSize > 0) "TEAM ${state.teamSize}" to SolakHudColors.NEUTRAL_ACCENT else null,
            instruction = instruction,
            note = note,
            lines = lines,
            bars = bars
        )
    }

    private fun headline(state: SolakState): String? {
        if (!showBlightStacks.value) return null
        val stacks = state.blightStacks ?: return "BLIGHT - UNKNOWN"
        return "BLIGHT x$stacks"
    }

    private fun dpsWindowNote(state: SolakState): Pair<String, Int>? {
        val window = state.dpsWindow ?: return null
        val focus = state.focusLimb ?: return "${window.label} OPEN" to SolakHudColors.GOOD
        return "${window.label} OPEN - hit ${focus.limb.label}" to SolakHudColors.GOOD
    }

    private fun statusLine(state: SolakState): String {
        val parts = ArrayList<String>(3)
        parts += "ENCOUNTER LIVE"
        if (state.rootlings.isNotEmpty()) parts += "ROOTLINGS ${state.rootlings.size}"
        if (state.lashers.isNotEmpty()) parts += "LASHERS ${state.lashers.size}"
        return parts.joinToString(" · ")
    }

    private fun blessingLine(state: SolakState): Pair<String, Int> = when {
        state.extraActionArmed -> "NATURE'S BLESSING - EXTRA ACTION ARMED" to SolakHudColors.GOOD
        state.blessingReady -> "NATURE'S BLESSING READY" to SolakHudColors.GOOD
        else -> "NATURE'S BLESSING SPENT" to SolakHudColors.MUTED
    }

    private fun bar(label: String, lifepoints: Int, max: Int, color: Int): SolakHudData.Bar {
        if (max <= 0) return SolakHudData.Bar(label, lifepoints.grouped(), null, color)
        val percent = (lifepoints * 100L / max).toInt()
        return SolakHudData.Bar(label, "${lifepoints.grouped()} · $percent%", lifepoints.toFloat() / max, color)
    }

    private fun bossMax(state: SolakState): Int =
        state.boss?.let { runCatching { it.maxHealth }.getOrDefault(0) } ?: 0

    private fun severityColor(severity: Severity): Int = when (severity) {
        Severity.CRITICAL -> SolakHudColors.CRITICAL
        Severity.WARNING -> SolakHudColors.WARNING
        Severity.INFO -> SolakHudColors.INFO
    }

    private fun outlineTargets(state: SolakState): List<OutlineTarget> {
        state.core?.takeIf { state.coreLifepoints > 0 }?.let { return listOf(OutlineTarget(it, CORE_RGB)) }
        state.windowLimbs.takeIf { it.isNotEmpty() }?.let { limbs ->
            return limbs.map { OutlineTarget(it.npc, LIMB_RGB) }
        }
        return state.rootlings.map { OutlineTarget(it, ROOTLING_RGB) }
    }

    private fun drawOutlines() {
        if (!showTargetOutline.value) {
            clearOutlines()
            return
        }
        val snapshot = targets
        val current = HashMap<Int, Long>(snapshot.size)
        for (target in snapshot) {
            runCatching {
                EntityHighlight.apply(target.npc, target.rgb, EntityHighlight.Mode.OUTLINE, OUTLINE_SCALE)
                val addr = EntityHighlight.renderModelAddr(target.npc)
                if (addr != 0L) current[target.npc.serverIndex] = addr
            }
        }
        for ((index, addr) in outlined) {
            if (index !in current) runCatching { EntityHighlight.clearByRenderModelAddr(addr) }
        }
        outlined = current
    }

    /** The outline is a write into the client's own render model, so it outlives us unless cleared. */
    private fun clearOutlines() {
        val stale = outlined
        if (stale.isEmpty()) return
        outlined = emptyMap()
        for (addr in stale.values) runCatching { EntityHighlight.clearByRenderModelAddr(addr) }
    }

    private fun Int.grouped(): String = String.format(Locale.ROOT, "%,d", this)

    private class OutlineTarget(val npc: NPC, val rgb: Int)

    private companion object {
        const val LOOP_DELAY_MS = 60
        const val OUTLINE_SCALE = 5
        const val CORE_RGB = 0xFFC24A
        const val LIMB_RGB = 0x30D158
        const val ROOTLING_RGB = 0xFF7A3B
    }
}

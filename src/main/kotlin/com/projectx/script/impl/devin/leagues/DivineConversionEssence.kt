package com.projectx.script.impl.devin.leagues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.projectx.game.interfaces.IFSlot
import com.projectx.game.interfaces.actionbarSlots
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigurableScript
import com.projectx.script.IntConfigItem
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.SkillTrackerReadout
import com.projectx.script.api.SkillTrackerSnapshot
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.inventory
import com.projectx.script.api.varps
import com.projectx.ui.compose.components.Hint
import com.projectx.util.componentIdFromHash
import com.projectx.util.gaussian
import com.projectx.util.interfaceIdFromHash
import org.projectx.core.game.combat.ActionBarModel
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.gameval.Gameval

private const val PURE_ESSENCE = 7936
private const val ALTAR_RANGE = 12

@ScriptDescription(
    name = "Divine Conversion Essence",
    version = "1.1.0",
    author = "Devin",
    description = "Alchemises a rune stack into pure essence, then crafts it at the nearest runecrafting altar.",
    category = ScriptCategory.RUNECRAFTING
)
class DivineConversionEssence : Script(), ConfigurableScript, ComposePanel {

    private val runeId = IntConfigItem(
        name = "Rune id",
        description = "Item id of the rune stack Low Level Alchemy is cast on.",
        initialValue = 556,
        min = 0
    )

    private val castYield = IntConfigItem(
        name = "Cast yield",
        description = "Runes consumed and pure essence produced by a single cast.",
        initialValue = 10,
        min = 1,
        max = 28
    )

    private val tracker = SkillTracker(Skill.RUNECRAFTING)
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }
    private var lastReport by mutableStateOf("")

    override suspend fun loop() {
        if (inventory.freeSlots >= castYield.value && inventory.count(runeId.value) >= castYield.value)
            transmute()
        else
            craft()
    }

    private suspend fun transmute() {
        val spell = alchemyCastSlot() ?: return idle("Low Level Alchemy is not on an active action bar")
        val runes = inventory.getItem(runeId.value) ?: return idle("No ${Gameval.objLabel(runeId.value)} to transmute")
        val before = inventory.count(PURE_ESSENCE)
        if (!spell.select() || !runes.slot.target()) return idle("Could not aim Low Level Alchemy at the rune stack")
        delayUntil(gaussian(2400L, 700L), pollingDelayMillis = 40) { inventory.count(PURE_ESSENCE) > before }
        val gained = inventory.count(PURE_ESSENCE) - before
        if (gained <= 0) return idle("Cast produced no essence - is Low Level Alchemy on the bar and the rune alchable?")
        tracker.add("Essence", gained)
        lastReport = ""
        delay(88, 52)
    }

    private suspend fun craft() {
        if (inventory.count(PURE_ESSENCE) <= 0) return idle("No essence to craft and no room to transmute")
        val altar = findClosestObject(ALTAR_RANGE) { it.hasOption("Craft runes") }
            ?: return idle("No runecrafting altar within $ALTAR_RANGE tiles")
        val option = if (altar.hasOption("Use")) "Use" else "Craft runes"
        if (!altar.interact(option)) return idle("${altar.name()} would not accept \"$option\"")
        delayUntil(gaussian(4800L, 1400L), pollingDelayMillis = 40) { inventory.count(PURE_ESSENCE) <= 0 }
        tracker.add("Crafts")
        lastReport = ""
        delay(180, 90)
    }

    private suspend fun idle(reason: String) {
        if (reason != lastReport) {
            lastReport = reason
            println("[Divine Conversion] $reason")
        }
        delay(900, 400)
    }

    private fun alchemyCastSlot(): IFSlot? {
        val alchemy = Gameval.id(Gameval.STRUCT, "magic_low_alchemy") ?: return null
        for ((barLocation, barNumber) in ActionBarModel.activeBars(varps))
            for (slot in 1..ActionBarModel.SLOTS)
                if (ActionBarModel.resolveSlot(barNumber, slot, varps).structId == alchemy)
                    return castComponent(barLocation, slot)
        return null
    }

    /** The bar-1 slot table points at prayer_bg_N, but a spell is aimed from the slot's graphic_N. */
    private fun castComponent(barLocation: Int, slot: Int): IFSlot? {
        val base = actionbarSlots[barLocation]?.get(slot) ?: return null
        val hash = Gameval.interfaceName(base.interfaceId)
            ?.let { Gameval.componentHash("$it:graphic_$slot") }
            ?: return base
        return IFSlot(interfaceIdFromHash(hash), componentIdFromHash(hash))
    }

    @Composable
    override fun Panel() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkillTrackerReadout(progress)
            if (lastReport.isNotEmpty()) Hint(lastReport)
        }
    }
}

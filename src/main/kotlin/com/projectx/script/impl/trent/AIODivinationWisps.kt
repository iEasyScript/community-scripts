package com.projectx.script.impl.trent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import org.projectx.core.game.skill.Skill
import com.projectx.game.chat.MessageType
import com.projectx.game.nxt.entity.npc.NPC
import com.projectx.script.ComposePanel
import com.projectx.script.Script
import com.projectx.script.ScriptDescription
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Chat
import com.projectx.ui.compose.components.Readout
import com.projectx.util.*

@ScriptDescription(
    name = "AIO Wisp Gatherer",
    version = "1.1.0",
    author = "Trent",
    description = "Gathers wisps at any colony you are near."
)
class AIODivinationWisps : Script(), ComposePanel {
    var startTime = 0L
    var tryGrabFragmentUntil = 0L
    val grabFragment
        get() = tryGrabFragmentUntil > System.currentTimeMillis()
    val energies get() = inventory.count(""".*energy$""".toRegex())
    var startEnergies = 0

    private val tracker = SkillTracker(Skill.DIVINATION)
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }
    private val energiesPerHour by live(0) { if (startTime == 0L) 0 else getUnitsPerHour(energies - startEnergies, startTime) }

    override fun onStart() {
        println("AIO divination wisp gatherer started.")
        startTime = System.currentTimeMillis()
        startEnergies = energies
    }

    override suspend fun loop() {
        if (prayerPoints < 100 && drinkPrayerPot()) delay(600, 300)
        if (prayerPoints > 1 && onCursesPrayers && getRealLevel(Skill.PRAYER) >= 87) togglePrayer(Prayer.CHRONICLE_ATTRACTION, true)

        if (inventory.isFull) {
            if (interactClosestObject("Convert memories")) {
                println("Converting memories.")
                waitThenDelayUntil(1500, 60000) { !inventory.hasItem(Regex(".* memory$")) }
            } else
                println("Failed to find object with 'Convert memories' option.")
            return
        }

        if (grabFragment && !Prayer.CHRONICLE_ATTRACTION.active) {
            println("Checking for chronicle fragment.")
            delay(1200, 500)
            if (interactClosestNPC("Chronicle fragment", "Capture", range = 8)) {
                delayWhile(gaussian(2500L, 800L)) { grabFragment }
                return
            }
            if (interactClosestNPC("Guthixian butterfly", "Catch", range = 8)) {
                delayWhile(gaussian(2500L, 800L)) { grabFragment }
                return
            }
            return
        }

        println("Finding spring.")
        val target = findEnriched() ?: findNonEnriched() ?: run {
            println("No springs found.")
            delay(500, 850)
            return
        }

        if (target.interact("Harvest")) {
            val wasEnriched = target.isEnriched()
            println("Harvesting ${if (wasEnriched) "enriched" else ""} spring.")
            if (!target.isActive())
                delayUntil(gaussian(9852L, 1000L)) { target.isActive() }
            else
                delay(3729, 1500)
            delayUntil(300000, 600) {
                timeSinceLastXpDrop > 5000L ||
                !target.isActive() ||
                inventory.isFull ||
                grabFragment ||
                (!wasEnriched && findEnriched() != null)
            }
        }
    }

    override fun onEvent(event: Event) {
        if (event !is Chat || event.messageType != MessageType.UNFILTERABLE) return
        if (event.message.contains("A chronicle escapes") || event.message.contains("A Guthixian Butterfly blossoms"))
            tryGrabFragmentUntil = System.currentTimeMillis() + random(10000, 20000)
        else if (event.message.contains("cannot capture another player") || event.message.contains("You capture the chronicle fragment"))
            tryGrabFragmentUntil = 0L
    }

    fun findEnriched() = findClosestNPC(maxRange = 7) { it.isEnriched() }
    fun findNonEnriched() = findClosestNPC(maxRange = 7) { Regex(".*\\s(wisp|spring)$").matches(it.name) && it.hasOption("Harvest") }
    fun NPC.isEnriched() = Regex("^Enriched.*(wisp|spring)$").matches(name) && hasOption("Harvest")
    fun NPC.isActive() = exists() && !headbars.isEmpty()

    @Composable
    override fun Panel() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Readout(listOf("Energies/hr" to format(energiesPerHour)))
            SkillTrackerReadout(progress)
        }
    }
}

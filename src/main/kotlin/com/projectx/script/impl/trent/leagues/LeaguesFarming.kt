package com.projectx.script.impl.trent.leagues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.projectx.core.game.skill.Skill
import com.projectx.game.chat.MessageType
import org.projectx.core.game.combat.Effect
import com.projectx.game.nxt.entity.npc.NPC
import com.projectx.script.ComposePanel
import com.projectx.script.Script
import com.projectx.script.ScriptDescription
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Chat
import com.projectx.util.*

private val seeds = Regex(".*\\sseed$")

@ScriptDescription(
    name = "Leagues Farming",
    version = "1.1.0",
    author = "Trent",
    description = "Leagues farming"
)
class LeaguesFarming : Script(), ComposePanel {
    private val tracker = SkillTracker(Skill.FARMING)
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }

    override suspend fun loop() {
        val butterfly = findClosestNPC("Guthixian butterfly")
        if (butterfly != null && butterfly.interact("Catch")) {
            delayUntil(gaussian(1592L, 2205L)) { !butterfly.exists() }
            return
        }
        if (Effect.JUJU_FARMING_POTION.notActive) {
            if (inventory.clickItem(Regex(".*farming potion.*", RegexOption.IGNORE_CASE), 1))
                delayUntil(gaussian(1592L, 2205L)) { Effect.JUJU_FARMING_POTION.active }
        }
        val patch = findClosestObject(93287) ?: return
        if (patch.hasOption("Pick")) {
            delay(950, 100)
            if (patch.interact("Pick"))
                delayUntil(gaussian(820L, 450L)) { findClosestObject { it.id == 93287 && it.hasOption("Pick") } == null }
            return
        }
        if (inventory.getItem(seeds)?.useOn(patch) == true)
            delayUntil(gaussian(7820L, 2000L)) { findClosestObject { it.id == 93287 && it.hasOption("Pick") } != null }
    }

    @Composable
    override fun Panel() = SkillTrackerReadout(progress)
}

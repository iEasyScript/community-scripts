package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.Script
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.findClosestNPC
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.inventory
import com.projectx.script.api.localPlayer
import com.projectx.util.gaussian
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.type.Tile

/**
 * Loots the graveyard. Only one mausoleum or coffin is open at a time and it moves every couple of minutes; a Loot
 * keeps going on its own until the spot closes, so the wait follows the stack of loot and the spot itself, and the
 * next open one is found once it shuts. The loot stacks, and Peckish takes it all for the event's rewards.
 */
object Thieving : HollowActivity {
    private val OPEN_SPOTS = setOf(131351, 131353)
    private const val LOOT_ANIMATION = 24887
    private const val PECKISH = 31286
    private const val VALUABLES = 57584
    private const val HEIRLOOMS = 57585
    private const val RANGE = 45
    private const val PULSE_GAP_MS = 5_000L
    private const val HAND_IN_RETRY_MS = 60_000L

    private var looted = 0
    private var handInRetryAt = 0L
    private var status = "Starting"
    private val xp = SkillTracker(Skill.THIEVING)

    private val carried get() = inventory.count(VALUABLES, HEIRLOOMS)

    override fun begin() {
        looted = 0
        handInRetryAt = 0L
        status = "Starting"
        xp.reset()
    }

    override fun report(settings: HollowSettings, hours: Double): HollowReport {
        val handInAt = settings.handInAt.value
        val meter = if (settings.handInLoot.value && handInAt > 0) {
            HudMeter("Loot towards Peckish", "$carried / $handInAt", carried / handInAt.toFloat())
        } else {
            HudMeter("Loot carried", "$carried", 0f)
        }
        return HollowReport(
            status,
            meter,
            listOf(
                HudTile("Looted", compact(looted), HollowIcon.SKULL),
                HudTile("Loot / hr", compact(perHour(looted, hours)), HollowIcon.CANDLE),
                HudTile("XP gained", compact(xp.xpGained(Skill.THIEVING)), HollowIcon.FLAME),
                HudTile("XP / hr", compact(xp.xpPerHour(Skill.THIEVING)), HollowIcon.HOURGLASS),
            ),
        )
    }

    override suspend fun step(script: Script, settings: HollowSettings): Boolean {
        if (settings.handInLoot.value && settings.handInAt.value in 1..carried && System.currentTimeMillis() >= handInRetryAt) {
            return handIn(script)
        }
        val spot = findClosestObject(RANGE) { it.id in OPEN_SPOTS }
        if (spot == null) {
            status = "Waiting for a grave to open"
            script.delay(gaussian(2200, 600))
            return true
        }
        return loot(script, spot)
    }

    private suspend fun loot(script: Script, spot: SceneObject): Boolean {
        val tile = spot.tile
        var seen = carried
        status = "Looting the ${spot.name().lowercase()}"
        if (!spot.interact("Loot")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(20_000L, 2500L)) { carried != seen || !open(tile) }
        if (carried == seen) return true
        var lastLoot = System.currentTimeMillis()
        while (!script.stopped && open(tile)) {
            if (carried != seen) {
                looted += (carried - seen).coerceAtLeast(0)
                seen = carried
                lastLoot = System.currentTimeMillis()
            } else if (localPlayer.animationId != LOOT_ANIMATION && System.currentTimeMillis() - lastLoot > PULSE_GAP_MS) {
                break
            }
            script.delayUntil(gaussian(1800L, 400L)) { carried != seen || !open(tile) }
        }
        looted += (carried - seen).coerceAtLeast(0)
        script.delay(gaussian(520, 200))
        return true
    }

    private fun open(tile: Tile) =
        findClosestObject(RANGE) { it.id in OPEN_SPOTS && it.tile == tile } != null

    private suspend fun handIn(script: Script): Boolean {
        val peckish = findClosestNPC(PECKISH, RANGE)
        if (peckish == null) {
            handInRetryAt = System.currentTimeMillis() + HAND_IN_RETRY_MS
            return true
        }
        status = "Handing loot to Peckish"
        if (!peckish.interact("Hand in")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(15_000L, 2000L)) { carried == 0 }
        script.delay(gaussian(450, 160))
        return true
    }
}

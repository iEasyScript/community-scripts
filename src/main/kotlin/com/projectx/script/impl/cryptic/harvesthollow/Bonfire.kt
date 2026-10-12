package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.script.Script
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.bank
import com.projectx.script.api.bankOpen
import com.projectx.script.api.depositBankInventory
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.findSerenSpirit
import com.projectx.script.api.inventory
import com.projectx.script.api.log
import com.projectx.script.api.withdrawBankItem
import com.projectx.util.gaussian
import org.projectx.core.game.skill.Skill

/**
 * Feeds the hub bonfire from the bank chest beside it. One "Add logs to" burns the whole backpack a log at a time,
 * so the wait follows the log count down and only clicks again once it stops falling.
 */
object Bonfire : HollowActivity {
    private const val BONFIRE = 134172
    private const val BANK_CHEST = 131378
    private const val RANGE = 30
    private const val KEEP_EXTRAS_FREE_SLOTS = 20
    private const val BACKPACK = 28

    private val ANY_LOGS = Regex("(.+ )?logs", RegexOption.IGNORE_CASE)

    private var burned = 0
    private var status = "Starting"
    private val xp = SkillTracker(Skill.FIREMAKING)

    override fun begin() {
        burned = 0
        status = "Starting"
        xp.reset()
    }

    override fun report(settings: HollowSettings, hours: Double): HollowReport {
        val carried = inventory.count(logsFor(settings))
        return HollowReport(
            status,
            HudMeter("Logs in the backpack", "$carried", carried / BACKPACK.toFloat()),
            listOf(
                HudTile("Burned", compact(burned), HollowIcon.LOGS),
                HudTile("Logs / hr", compact(perHour(burned, hours)), HollowIcon.FLAME),
                HudTile("XP gained", compact(xp.xpGained(Skill.FIREMAKING)), HollowIcon.CANDLE),
                HudTile("XP / hr", compact(xp.xpPerHour(Skill.FIREMAKING)), HollowIcon.SKULL),
            ),
        )
    }

    private fun logsFor(settings: HollowSettings): Regex = settings.logs.value.trim().takeIf { it.isNotEmpty() }
        ?.let { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) } ?: ANY_LOGS

    override suspend fun step(script: Script, settings: HollowSettings): Boolean {
        val logs = logsFor(settings)
        val carrying = inventory.hasItem(logs)
        val carried = if (carrying) burn(script, logs) else restock(script, logs)
        settings.burnedInfo.value = burned.toString()
        return carried
    }

    private suspend fun burn(script: Script, logs: Regex): Boolean {
        val bonfire = findClosestObject(BONFIRE, RANGE) ?: return missing(script, "bonfire")
        var left = inventory.count(logs)
        status = "Feeding the bonfire"
        if (!bonfire.interact("Add logs to")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(9000L, 1500L)) { inventory.count(logs) < left || findSerenSpirit() != null }
        while (!script.stopped && findSerenSpirit() == null) {
            val now = inventory.count(logs)
            if (now >= left) break
            burned += left - now
            left = now
            if (now == 0) break
            script.delayUntil(gaussian(5200L, 700L)) { inventory.count(logs) < left || findSerenSpirit() != null }
        }
        script.delay(gaussian(240, 90))
        return true
    }

    private suspend fun restock(script: Script, logs: Regex): Boolean {
        status = "Fetching logs from the bank chest"
        if (!bankOpen) {
            val chest = findClosestObject(BANK_CHEST, RANGE) ?: return missing(script, "bank chest")
            if (!chest.interact("Use")) {
                script.delay(gaussian(1200, 350))
                return true
            }
            script.delayUntil(gaussian(9000L, 1500L)) { bankOpen }
            if (!bankOpen) return true
            script.delay(gaussian(320, 110))
        }

        val stack = bank.filter { it.amount > 0 && logs.matches(it.name) }.maxByOrNull { it.amount }
        if (stack == null) {
            status = "Out of logs"
            script.log("No logs matching ${logs.pattern} left in the bank.")
            return false
        }
        if (inventory.freeSlots < KEEP_EXTRAS_FREE_SLOTS && inventory.any { !logs.matches(it.name) }) {
            if (depositBankInventory()) script.delayUntil(gaussian(2600L, 500L)) { inventory.isEmpty }
            script.delay(gaussian(260, 90))
        }
        if (withdrawBankItem(stack.name)) script.delayUntil(gaussian(2600L, 500L)) { inventory.hasItem(logs) }
        script.delay(gaussian(280, 100))
        return true
    }

    private fun missing(script: Script, what: String): Boolean {
        script.log("No $what within $RANGE tiles; start the script by the Harvest Hollow bonfire.")
        return false
    }
}

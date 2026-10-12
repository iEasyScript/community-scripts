package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.game.interfaces.IFSlot
import com.projectx.script.Script
import com.projectx.script.api.findClosestNPC
import com.projectx.script.api.interfaces
import com.projectx.script.api.inventory
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.util.gaussian

/**
 * Smashes the Smashing Pumpkin towards the Rotten pumpkin mask's restyle. Each Smash is one swing, so the next click
 * waits for the swing's animation to finish. The running count lives only on the server, shown when the mask's
 * "Change style" is used, so that is read at the start, every so often, and before stopping at the target; a pumpkin
 * that stops swinging, or swings the count never shows, ends the run rather than clicking on forever.
 */
object SmashingPumpkin : HollowActivity {
    private const val PUMPKIN = 31320
    private const val SMASH_ANIMATION = 35627
    private const val MASK = 55971
    private const val OBJBOX = 1189
    private const val OBJBOX_TEXT = 3
    private const val OBJBOX_CONTINUE = 16
    private const val RANGE = 30
    private const val RECOUNT_EVERY = 40
    private const val MAX_MISSES = 3

    private val PROGRESS = Regex("smashed (\\d+) / (\\d+)")

    private var counted = -1
    private var required = 0
    private var sinceCount = 0
    private var maskMissing = false
    private var misses = 0
    private var thisRun = 0
    private var status = "Starting"

    override fun begin() {
        counted = -1
        required = 0
        sinceCount = 0
        maskMissing = false
        misses = 0
        thisRun = 0
        status = "Starting"
    }

    override fun report(settings: HollowSettings, hours: Double): HollowReport {
        val total = counted + sinceCount
        val goal = settings.smashTarget.value.takeIf { it > 0 } ?: required
        val rate = perHour(thisRun, hours)
        val toGo = if (counted < 0 || goal <= 0) null else (goal - total).coerceAtLeast(0)
        val meter = when {
            counted >= 0 && required > 0 -> HudMeter("Pumpkins smashed", "$total / $required", total / required.toFloat())
            maskMissing -> HudMeter("Pumpkins smashed", "no mask in the backpack", 0f)
            else -> HudMeter("Pumpkins smashed", "reading the mask", 0f)
        }
        return HollowReport(
            status,
            meter,
            listOf(
                HudTile("This run", compact(thisRun), HollowIcon.PUMPKIN),
                HudTile("Smashes / hr", compact(rate), HollowIcon.SKULL),
                HudTile("To go", toGo?.let(::compact) ?: "?", HollowIcon.CANDLE),
                HudTile("Time left", if (toGo != null && rate > 0) clock(toGo * 3600L / rate) else "--:--:--", HollowIcon.HOURGLASS),
            ),
        )
    }

    override suspend fun step(script: Script, settings: HollowSettings): Boolean {
        val target = settings.smashTarget.value
        if (!maskMissing && (counted < 0 || sinceCount >= RECOUNT_EVERY || (target > 0 && counted + sinceCount >= target))) {
            if (!recount(script)) return false
        }
        settings.smashedInfo.value = if (counted < 0) "$sinceCount this run" else "${counted + sinceCount} / $required"
        if (target > 0 && counted >= target && sinceCount == 0) {
            status = "Target reached - the mask can change style"
            script.log("Smashed $counted pumpkins; the target of $target is reached.")
            return false
        }

        val pumpkin = findClosestNPC(PUMPKIN, RANGE)
        if (pumpkin == null) {
            script.log("No Smashing Pumpkin within $RANGE tiles; start the script beside it.")
            return false
        }
        status = "Smashing the pumpkin"
        if (!pumpkin.interact("Smash")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(4500L, 900L)) { localPlayer.animationId == SMASH_ANIMATION }
        if (localPlayer.animationId != SMASH_ANIMATION) {
            if (++misses < MAX_MISSES) return true
            if (!maskMissing) recount(script)
            status = "The pumpkin stopped taking smashes"
            script.log("The pumpkin took no swing from $MAX_MISSES Smash clicks in a row; stopping at ${progressText()}.")
            return false
        }
        misses = 0
        sinceCount++
        thisRun++
        script.delayUntil(gaussian(5000L, 900L)) { localPlayer.animationId != SMASH_ANIMATION }
        script.delay(gaussian(320, 130))
        return true
    }

    /** Reads the mask's count; false when swings since the last read never reached it, so smashing has stopped counting. */
    private suspend fun recount(script: Script): Boolean {
        val mask = inventory.getItem(MASK)
        if (mask == null) {
            maskMissing = true
            script.log("Keep the Rotten pumpkin mask in the backpack to count smashes; smashing without a count.")
            return true
        }
        status = "Checking the mask's count"
        if (!mask.click("Change style")) return true
        script.delayUntil(gaussian(3500L, 700L)) { progress() != null }
        val match = progress() ?: return true
        val now = match.groupValues[1].toInt()
        val stalled = counted >= 0 && sinceCount > 0 && now <= counted
        if (stalled) script.log("$sinceCount swings since the last count but the mask still says $now; stopping.")
        counted = now
        required = match.groupValues[2].toInt()
        sinceCount = 0
        script.delay(gaussian(450, 160))
        if (IFSlot(OBJBOX, OBJBOX_CONTINUE, -1).dialogueContinue()) {
            script.delayUntil(gaussian(2200L, 400L)) { !interfaces.isOpen(OBJBOX) }
        }
        script.delay(gaussian(260, 90))
        return !stalled
    }

    private fun progressText() = if (counted < 0) "an unknown count" else "$counted / $required"

    private fun progress(): MatchResult? =
        if (!interfaces.isOpen(OBJBOX)) null
        else interfaces.getComponent(OBJBOX, OBJBOX_TEXT)?.text?.let(PROGRESS::find)
}

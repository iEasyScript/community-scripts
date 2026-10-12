package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.script.Script
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.api.varps
import com.projectx.util.gaussian
import org.projectx.core.game.skill.Skill

/**
 * Summons at the hub's circle. A summon keeps going on its own for as long as one of the four candles stays lit, so
 * a session is only ever interrupted by every candle going out: then each unlit candle is lit and the circle used
 * again. Progress is read from the game's own counter rather than inferred from the animation, which pauses between
 * pulses.
 */
object Summoning : HollowActivity {
    private const val CIRCLE = 131360
    private const val SUMMON_ANIMATION = 6298
    private const val PROGRESS_VARBIT = 56090
    private const val RANGE = 20
    private const val MAX_FAILED_SUMMONS = 3
    private const val PULSE_GAP_MS = 5_000L

    private val CANDLE_VARBITS = intArrayOf(56083, 56084, 56085, 56086)

    private var startProgress = -1
    private var lastSeen = -1
    private var lastChangeAt = 0L
    private var failed = 0
    private var status = "Starting"
    private val xp = SkillTracker(Skill.SUMMONING)

    private val progress get() = varps.getVarBit(PROGRESS_VARBIT)
    private val litCandles get() = CANDLE_VARBITS.count { varps.getVarBit(it) == 1 }

    override fun begin() {
        startProgress = -1
        lastSeen = -1
        failed = 0
        status = "Starting"
        xp.reset()
    }

    override fun report(settings: HollowSettings, hours: Double): HollowReport {
        val lit = litCandles
        val gained = if (startProgress < 0) 0 else progress - startProgress
        return HollowReport(
            status,
            HudMeter("Candles lit", "$lit / ${CANDLE_VARBITS.size}", lit / CANDLE_VARBITS.size.toFloat()),
            listOf(
                HudTile("Progress", compact(progress), HollowIcon.SKULL),
                HudTile("This run", compact(gained), HollowIcon.CANDLE),
                HudTile("XP gained", compact(xp.xpGained(Skill.SUMMONING)), HollowIcon.FLAME),
                HudTile("XP / hr", compact(xp.xpPerHour(Skill.SUMMONING)), HollowIcon.HOURGLASS),
            ),
        )
    }

    override suspend fun step(script: Script, settings: HollowSettings): Boolean {
        if (startProgress < 0) {
            startProgress = progress
            lastSeen = startProgress
        }
        if (summoning()) return keepSummoning(script)
        if (litCandles < CANDLE_VARBITS.size && lightCandle(script)) return true
        if (litCandles == 0) {
            status = "Waiting for a candle to light"
            script.delay(gaussian(1500, 400))
            return true
        }
        return startSummon(script)
    }

    /** The animation pauses between pulses, so a counter that moved within one pulse also counts as summoning. */
    private fun summoning(): Boolean {
        val now = progress
        if (now != lastSeen) {
            lastSeen = now
            lastChangeAt = System.currentTimeMillis()
        }
        return localPlayer.animationId == SUMMON_ANIMATION || System.currentTimeMillis() - lastChangeAt < PULSE_GAP_MS
    }

    /** Follows the counter while it climbs; a pause long enough for two pulses means the summon has ended. */
    private suspend fun keepSummoning(script: Script): Boolean {
        status = "Summoning ($litCandles lit)"
        var seen = progress
        while (!script.stopped && litCandles > 0) {
            script.delayUntil(gaussian(6500L, 900L)) { progress != seen || litCandles == 0 }
            if (progress == seen) break
            seen = progress
        }
        script.delay(gaussian(420, 160))
        return true
    }

    private suspend fun lightCandle(script: Script): Boolean {
        val candle = findClosestObject(RANGE) { it.name() == "Candle" && it.hasOption("Light") } ?: return false
        val before = litCandles
        status = "Lighting a candle"
        if (!candle.interact("Light")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(5000L, 900L)) { litCandles > before }
        script.delayUntil(gaussian(2500L, 500L)) { !localPlayer.isAnimating }
        script.delay(gaussian(300, 110))
        return true
    }

    private suspend fun startSummon(script: Script): Boolean {
        val circle = findClosestObject(CIRCLE, RANGE)
        if (circle == null) {
            script.log("No summoning circle within $RANGE tiles; start the script beside it.")
            return false
        }
        val before = progress
        status = "Starting a summon"
        if (!circle.interact("Summon")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(6000L, 900L)) { summoning() || progress != before }
        if (summoning() || progress != before) {
            failed = 0
            return true
        }
        if (++failed >= MAX_FAILED_SUMMONS) {
            status = "The circle stopped answering"
            script.log("The summoning circle did nothing for $MAX_FAILED_SUMMONS Summon clicks in a row; stopping.")
            return false
        }
        return true
    }
}

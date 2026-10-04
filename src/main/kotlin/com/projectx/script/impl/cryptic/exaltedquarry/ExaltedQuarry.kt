package com.projectx.script.impl.cryptic.exaltedquarry

import com.projectx.game.interfaces.IFSlot
import com.projectx.game.nxt.HeightMap
import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.ConfigurableScript
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.MakeX
import com.projectx.script.api.captureSerenSpirit
import com.projectx.script.api.findSerenSpirit
import com.projectx.script.api.getAllObjectsWithinRange
import com.projectx.script.api.inventory
import com.projectx.script.api.isLoggedIn
import com.projectx.script.api.isType
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.api.componentSlotExists
import com.projectx.script.api.makeXConfirm
import com.projectx.util.gaussian
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mines the Exalted Quarry.
 *
 * The quarry's deposits are spread over several floors, so the bot works the richest one standing on the
 * floor it is on and takes a ladder when that floor has nothing left. A full pack goes to the colossus,
 * which turns the essence exalted, then to the processor, which makes it into essence proper.
 */
@ScriptDescription(
    name = "Exalted Quarry",
    version = "2.7.0",
    author = "Cryptic",
    description = "Mines the Exalted Quarry: works the richest deposit on its own floor, climbs between " +
        "floors, exalts a full pack at the colossus and processes it at the essence processor.",
    category = ScriptCategory.MINING,
)
class ExaltedQuarry : Script(), ConfigurableScript {

    private val settings = QuarrySettings()

    private var trips = 0
    private var spirits = 0
    private var refreshes = 0
    private var lastMine = 0L
    private var lastClimb = 0L
    private var workedTile: Pair<Int, Int>? = null
    private var stage = "starting"
    private var rockLabel = "-"
    private var warnedFull = false
    private var reportedTarget = ""
    private var lastFreeSlots = -1
    private var lastGainMs = 0L
    private var lastStaminaLog = 0L

    fun settings() = settings

    /** First match wins, so adding content is an entry here rather than a branch in the loop. */
    private val steps: List<Pair<String, suspend () -> Boolean>> = listOf(
        "processing" to ::awaitProcessing,
        "banking essence" to ::bankEssence,
        "processing" to ::processPack,
        "exalting at the colossus" to ::exaltPack,
        "going down to the colossus" to ::descend,
        "mining" to ::mineRock,
        "changing floor" to ::changeFloor,
    )

    override fun onStart() {
        log("Exalted Quarry ready.")
        surveyFloor()
    }

    /** Every rock in range against our own elevation, so the floor boundaries can be read off the log. */
    private fun surveyFloor() {
        log("Standing at ${localPlayer.tileX},${localPlayer.tileY} drawn at ${localPlayer.graphNode.scene.z.toInt()}, ground ${HeightMap.fineHeight(localPlayer.tile)} - floor ${ourFloor()}")
        samePlane()
            .filter { obj -> QuarryContent.rocks.any { obj.isType(it.id) } || obj.hasOption("Mine") }
            .sortedBy { distanceTo(it) }
            .forEach { obj ->
                log("  ${obj.name()} ${obj.tileX},${obj.tileY} height ${HeightMap.fineHeight(obj.tile)} floor ${floorOf(obj)} dist ${distanceTo(obj).toInt()}")
            }
    }

    override suspend fun loop() {
        if (!isLoggedIn()) return delay(gaussian(1200, 300))
        publish()
        if (captureSerenSpirit()) {
            spirits++
            return
        }
        for ((label, step) in steps) {
            if (step()) {
                stage = label
                return
            }
        }
        stage = "idle"
        delay(gaussian(POLL_MS, POLL_VARIANCE))
    }

    /** A make-all runs itself once started, so this only keeps the loop out of its way. */
    private suspend fun awaitProcessing(): Boolean {
        if (!MakeX.inProgress) return false
        delayUntil(gaussian(20000L, 4000L)) { !MakeX.inProgress || findSerenSpirit() != null }
        return true
    }

    private suspend fun bankEssence(): Boolean {
        val product = productCount()
        if (product <= 0) return false
        if (inventory.freeSlots > 0 && rawCount() + exaltedCount() > 0) return false
        if (!settings.depositProduct.value) {
            if (!warnedFull) {
                warnedFull = true
                log("Pack is full of finished essence and banking is off - nothing left to do.")
            }
            return false
        }
        val box = nearest { it.isType(QuarryContent.DEPOSIT_BOX) } ?: return false
        if (!box.interact("Deposit all")) return false
        delayUntil(gaussian(5000L, 1200L)) { productCount() < product }
        delay(gaussian(420, 150))
        if (productCount() >= product) return false
        trips++
        warnedFull = false
        return true
    }

    private suspend fun processPack(): Boolean {
        if (!settings.processEssence.value) return false
        val exalted = exaltedCount()
        val raw = if (settings.exaltFirst.value) 0 else rawCount()
        val waiting = exalted + raw
        if (waiting <= 0) return false
        if (MakeX.isOpen) return makeEverything(waiting, exalted > 0)
        val processor = nearest { it.isType(QuarryContent.PROCESSOR) } ?: return false
        if (!processor.interact("Process")) return false
        delayUntil(gaussian(6000L, 1500L)) { MakeX.isOpen }
        return MakeX.isOpen
    }

    /**
     * Every tier processes into an essence of the same name, so the recipes cannot be told apart by what
     * they produce - only by whether the pack can feed them. Each is selected in turn until one reports
     * something to make, which also means a pack holding two tiers is simply processed twice.
     */
    private suspend fun makeEverything(waiting: Int, exalted: Boolean): Boolean {
        val target = if (exalted) QuarryContent.EXALTED_ESSENCE else QuarryContent.PURE_ESSENCE
        if (!selectFeedable(target)) return false
        val making = MakeX.maxQuantity
        if (!makeXConfirm()) return false
        log("Processing $making into $target.")
        delayUntil(gaussian(25000L, 5000L)) {
            exaltedCount() + rawCount() < waiting || findSerenSpirit() != null
        }
        return true
    }

    /**
     * The recipe the pack can actually feed.
     *
     * The quantity the panel offers lands a tick after the selection itself does, so it is waited for
     * rather than read straight after the click - reading it immediately sees the old value, judges every
     * recipe empty and walks the list forever. Whatever is already selected is tried first, since that
     * costs no click at all.
     */
    /**
     * Puts the panel on a recipe the pack can actually feed.
     *
     * Every tier of an essence shares one grid icon, so the id a row displays is not the id selecting it
     * produces - all three Exalted rows read 63938 while they select 63935, 63936 and 63937. A row is
     * therefore identified by its slot and judged on the selection changing and a quantity appearing,
     * never on matching that icon. The quantity lands a tick after the selection, so it is waited for.
     */
    private suspend fun selectFeedable(target: String): Boolean {
        if (MakeX.maxQuantity > 0) return true
        for (recipe in MakeX.craftables().filter { it.name == target }) {
            if (!componentSlotExists(MakeX.PANEL, MakeX.GRID, recipe.selectSlot)) continue
            val before = MakeX.selectedItemId
            delay(gaussian(880, 320))
            IFSlot(MakeX.PANEL, MakeX.GRID, recipe.selectSlot).click(1)
            delayUntil(gaussian(2500L, 700L)) { MakeX.selectedItemId != before }
            delayUntil(gaussian(2500L, 700L)) { MakeX.maxQuantity > 0 }
            if (MakeX.maxQuantity > 0) return true
        }
        return false
    }

    /** The colossus rewrites whatever raw essence the pack holds, so it only wants visiting once it is full. */
    private suspend fun exaltPack(): Boolean {
        if (!settings.exaltFirst.value || inventory.freeSlots > 0) return false
        val raw = rawCount()
        if (raw <= 0) return false
        val colossus = nearest { it.isType(QuarryContent.COLOSSUS) } ?: return false
        if (!readyToWork()) return true
        if (!colossus.interact("Mine")) return false
        lastMine = System.currentTimeMillis()
        delayUntil(gaussian(9000L, 2000L)) { rawCount() < raw || findSerenSpirit() != null }
        delay(gaussian(420, 150))
        return true
    }

    /**
     * Mines the best rock on this floor, and re-clicks the one already being mined to carry its stamina
     * back up. Both paths go through [readyToWork], so a rock that never starts cannot be clicked faster
     * than about once a second.
     */
    private suspend fun mineRock(): Boolean {
        if (inventory.freeSlots <= 0) return false
        val rock = bestRock() ?: return false
        rockLabel = QuarryContent.rocks.firstOrNull { rock.isType(it.id) }?.label ?: rock.name()
        reportFloor(rock, rockLabel)
        val tile = rock.tileX to rock.tileY
        val working = tile == workedTile && recentlyGained()
        if (working && !staminaLow()) return true
        if (!readyToWork()) return true
        if (!rock.interact("Mine")) return false
        lastMine = System.currentTimeMillis()
        workedTile = tile
        if (working) {
            refreshes++
        } else {
            delayUntil(gaussian(5000L, 1200L)) { recentlyGained() || findSerenSpirit() != null }
        }
        delay(gaussian(260, 90))
        return true
    }

    /**
     * A full pack is no use up a terrace, because the colossus and the processor are both on the quarry
     * floor. The ladder is the only way between them, and it offers one Climb either way, so the bot
     * takes it and looks again rather than trying to know in advance which way it goes.
     */
    private suspend fun descend(): Boolean {
        if (inventory.freeSlots > 0 && exaltedCount() == 0) return false
        if (nearest { it.isType(QuarryContent.COLOSSUS) } != null) return false
        if (nearest { it.isType(QuarryContent.PROCESSOR) } != null) return false
        log("Pack is full on floor ${ourFloor()} - heading down to the colossus.")
        return climb()
    }

    /** Only ever climbs to look for rocks, so a pack with no room to put them stays where it is. */
    private suspend fun changeFloor(): Boolean {
        if (inventory.freeSlots <= 0) return false
        return climb()
    }

    private suspend fun climb(): Boolean {
        if (System.currentTimeMillis() - lastClimb < gaussian(CLIMB_GAP, CLIMB_GAP_VARIANCE)) return false
        val ladder = nearestLadder() ?: return false
        val broken = QuarryContent.brokenLadders.any { ladder.isType(it) }
        val plane = localPlayer.plane
        val floor = ourFloor()
        reportFloor(ladder, if (broken) "Broken ladder" else "Ladder")
        lastClimb = System.currentTimeMillis()
        if (!ladder.interact(if (broken) "Traverse" else "Climb")) return false
        delayUntil(gaussian(7000L, 1800L)) { leftFloor(plane, floor) }
        delay(gaussian(520, 180))
        return leftFloor(plane, floor)
    }

    /** A ledge is left by changing floor, not plane, so the climb is judged on both. */
    private fun leftFloor(fromPlane: Int, fromFloor: Int?): Boolean {
        if (localPlayer.plane != fromPlane) return true
        if (fromFloor == null) return false
        return ourFloor()?.let { it != fromFloor } ?: false
    }

    /**
     * A ladder joins two floors and stands on the lower one, so it is never filtered to our own floor -
     * doing that hides the very ladder we need the moment we are at the top of it. Only the floors it
     * could plausibly join are considered, nearest first.
     */
    private fun nearestLadder(): SceneObject? {
        val ids = if (settings.useBrokenLadders.value) {
            QuarryContent.ladders + QuarryContent.brokenLadders
        } else {
            QuarryContent.ladders
        }
        val here = ourFloor()
        return samePlane()
            .filter { obj -> ids.any { obj.isType(it) } }
            .filter { obj -> here == null || floorOf(obj)?.let { abs(it - here) <= 1 } ?: true }
            .minByOrNull { distanceTo(it) }
    }

    /**
     * Tier leads, because that is the choice the player made; a cracked deposit only wins the tie against
     * the plain one at its own tier, and distance settles the rest.
     */
    private fun bestRock(): SceneObject? {
        val wanted = QuarryContent.rocks.filter { wanted(it) }
        val crackedFirst = if (settings.preferCracked.value) 1 else 0
        return onThisFloor()
            .filter { it.hasOption("Mine") }
            .mapNotNull { obj -> wanted.firstOrNull { obj.isType(it.id) }?.let { it to obj } }
            .minWithOrNull(
                compareByDescending<Pair<Rock, SceneObject>> { it.first.tier }
                    .thenByDescending { if (it.first.cracked) crackedFirst else 0 }
                    .thenBy { distanceTo(it.second) },
            )
            ?.second
    }

    private fun wanted(rock: Rock): Boolean = when (settings.deposits.value) {
        DepositChoice.EVERYTHING -> true
        DepositChoice.LARGE_ONLY -> rock.large
        DepositChoice.REGULAR_ONLY -> !rock.large
    }

    private fun nearest(predicate: (SceneObject) -> Boolean): SceneObject? =
        onThisFloor().filter(predicate).minByOrNull { distanceTo(it) }

    /**
     * The quarry's floors are terraces of one plane rather than separate planes, and the collision map
     * does not divide them - every rock routes as reachable from every other. Terrain height is what
     * actually tells them apart, so a loc counts as ours only when the ground under it sits at about our
     * own elevation. With no height to read nothing is excluded, which keeps a missing height map from
     * stalling the bot rather than quietly narrowing it to nothing.
     */
    private fun onThisFloor(): List<SceneObject> {
        val here = ourFloor() ?: return samePlane()
        return samePlane().filter { floorOf(it)?.let { floor -> floor == here } ?: true }
    }

    /** Which ledge a height sits on, counting the quarry floor as 0. */
    private fun floorOf(height: Int): Int {
        val step = settings.floorStep.value.coerceAtLeast(1)
        return ((height - QuarryContent.FLOOR_BASE).toDouble() / step).roundToInt()
    }

    private fun floorOf(obj: SceneObject): Int? = HeightMap.fineHeight(obj.tile)?.let { floorOf(it) }

    /**
     * Our floor comes from where the player is actually drawn, not from the ground under the tile.
     *
     * A ladder stands on the same tile on both floors, so the terrain there has one height - the foot of
     * it - and reading that puts us downstairs the instant we arrive at the top. Climbing then looks like
     * it failed, and the bot climbs again and comes straight back down. Scenery still uses the terrain,
     * because scenery really does sit on the ground it stands on.
     */
    private fun ourFloor(): Int? =
        runCatching { floorOf(localPlayer.graphNode.scene.z.toInt()) }.getOrNull()

    private fun samePlane(): List<SceneObject> =
        getAllObjectsWithinRange(SCAN_RANGE).filter { it.tile.plane == localPlayer.plane }

    /** Logged once per target so the elevations behind a choice are visible without filling the log. */
    private fun reportFloor(obj: SceneObject, label: String) {
        if (label == reportedTarget) return
        reportedTarget = label
        log("$label at ${obj.tileX},${obj.tileY} - floor ${floorOf(obj)}, we are on ${ourFloor()}.")
    }

    private fun distanceTo(obj: SceneObject): Double =
        obj.distanceTo(localPlayer.tileX.toDouble(), localPlayer.tileY.toDouble())

    private fun readyToWork(): Boolean =
        System.currentTimeMillis() - lastMine >= gaussian(WORK_GAP, WORK_GAP_VARIANCE)

    private fun rawCount(): Int = inventory.count(*QuarryContent.rawEssence)
    private fun exaltedCount(): Int = inventory.count(*QuarryContent.exaltedEssence)
    private fun productCount(): Int =
        inventory.count(QuarryContent.EXALTED_ESSENCE, QuarryContent.PURE_ESSENCE)

    /**
     * Stamina remaining, on the bar's own 0-255 scale, or -1 while no rock is being worked.
     *
     * The var called mining_stamina counts the opposite way - it rises as stamina is spent - and its
     * ceiling varies, so the bar itself is the only honest reading. No bar means nothing to keep up.
     */
    private fun staminaOnBar(): Int = localPlayer.headbarFill(HEADBAR_STAMINA)

    private fun staminaLow(): Boolean {
        if (settings.staminaBelow.value <= 0) return false
        val stamina = staminaOnBar()
        return stamina >= 0 && stamina < settings.staminaBelow.value + gaussian(0, STAMINA_JITTER)
    }

    /**
     * Essence arriving is the only honest sign the rock is being worked. The mining animation plays in
     * bursts and reads idle between swings, so gating on it re-issues the whole interaction every couple
     * of seconds at the same rock - which is clicking, not mining.
     */
    private fun recentlyGained(): Boolean =
        lastGainMs != 0L && System.currentTimeMillis() - lastGainMs < gaussian(MINING_PATIENCE, MINING_PATIENCE_VARIANCE)

    private fun trackProgress() {
        val free = inventory.freeSlots
        if (free != lastFreeSlots) {
            lastFreeSlots = free
            lastGainMs = System.currentTimeMillis()
        }
    }

    private fun publish() {
        trackProgress()
        val now = System.currentTimeMillis()
        if (now - lastStaminaLog > 6000) {
            lastStaminaLog = now
            log("stamina ${staminaOnBar()}/255, low=${staminaLow()}, refreshes=$refreshes")
        }
        settings.stageInfo.value = stage
        settings.floorInfo.value = localPlayer.plane.toString()
        settings.rockInfo.value = rockLabel
        settings.staminaInfo.value = staminaOnBar().takeIf { it >= 0 }?.let { "$it / 255" } ?: "?"
        settings.packInfo.value = "${rawCount()} / ${exaltedCount()} (${inventory.freeSlots} free)"
        settings.tripInfo.value = "$trips / $spirits"
    }

    private companion object {
        const val SCAN_RANGE = 40
        const val HEADBAR_STAMINA = 5
        const val POLL_MS = 600
        const val POLL_VARIANCE = 180
        const val MINING_PATIENCE = 9000
        const val MINING_PATIENCE_VARIANCE = 1800
        const val WORK_GAP = 2600
        const val WORK_GAP_VARIANCE = 700
        const val CLIMB_GAP = 3200
        const val CLIMB_GAP_VARIANCE = 900
        const val STAMINA_JITTER = 12
    }
}

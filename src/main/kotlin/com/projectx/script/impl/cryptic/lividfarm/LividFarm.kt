package com.projectx.script.impl.cryptic.lividfarm

import com.projectx.game.interfaces.IFSlot
import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.game.nxt.entity.npc.NPC
import com.projectx.script.ConfigurableScript
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.captureSerenSpirit
import com.projectx.script.api.continueDialogueContaining
import com.projectx.script.api.dialogueOptionVisible
import com.projectx.script.api.findClosestNPC
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.getAllObjectsWithinRange
import com.projectx.script.api.inventory
import com.projectx.script.api.interfaces
import com.projectx.script.api.isLoggedIn
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Chat
import com.projectx.util.gaussian

/**
 * Livid Farm, converted from the Lua script of the same name.
 *
 * Each pass picks the one job worth doing and does it: Murky Pat's event first because it is timed and
 * the routine jobs keep, then produce in hand, then the farm's upkeep.
 */
@ScriptDescription(
    name = "Livid Farm",
    version = "1.3.1",
    author = "Cryptic",
    description = "Runs Livid Farm: cures plants, fertilises patches, fixes fences, encourages Pauline, " +
        "answers Murky Pat's event, and bunches and trades the produce.",
    category = ScriptCategory.RUNECRAFTING,
)
class LividFarm : Script(), ConfigurableScript {

    private val settings = LividSettings()

    private var traded = 0
    private var cured = 0
    private var fertilised = 0
    private var fenced = 0
    private var stage = "starting"
    private var warnedRunes = false

    private var paulineFineUntil = 0L
    private var murkyPatUntil = 0L
    private var lastAction = 0L

    fun settings() = settings

    /** First match wins, so the order here is the priority the farm is worked in. */
    private val tasks: List<Pair<String, suspend () -> Boolean>> = listOf(
        "helping Murky Pat" to ::helpMurkyPat,
        "trading produce" to ::deliverProduce,
        "fertilising patches" to ::fertilisePatches,
        "curing plants" to ::curePlants,
        "encouraging Pauline" to ::encouragePauline,
        "fixing fences" to ::fixFences,
        "collecting produce" to ::collectProduce,
    )

    override fun onStart() {
        log("Livid Farm ready.")
    }

    /**
     * Her "currently fine" arrives as chat rather than as anything on her.
     *
     * Reading it here means the back-off starts the moment she says it, including while a wait for her
     * dialogue is still running - which is what stops the script standing there re-clicking her.
     */
    override fun onEvent(event: Event) {
        if (event is Chat && event.message.contains(LividIds.PAULINE_FINE, ignoreCase = true)) {
            paulineFineUntil = System.currentTimeMillis() + PAULINE_BACKOFF_MILLIS
        }
    }

    override suspend fun loop() {
        if (!isLoggedIn()) return delay(gaussian(1200, 300))
        publish()

        if (outOfRunes()) {
            if (!warnedRunes) {
                warnedRunes = true
                log("Out of runes - stopping.")
            }
            stop()
            return
        }
        if (captureSerenSpirit()) return

        // Being out of range of the farm is a pass worth skipping, not a reason to stop: walking to the
        // wagon or over to Pauline takes the log pile out of scan range for a couple of seconds.
        if (!atLividFarm()) {
            setStage("waiting to be back at the farm")
            return delay(gaussian(900, 250))
        }

        for ((label, task) in tasks) {
            if (task()) {
                setStage(label)
                return
            }
        }
        setStage("idle")
        delay(gaussian(POLL_MS, POLL_VARIANCE))
    }

    /** Logged only when it changes, so a run reads as what it did rather than a line per tick. */
    private fun setStage(label: String) {
        if (label == stage) return
        stage = label
        log(label.replaceFirstChar { it.uppercase() })
    }

    private fun atLividFarm(): Boolean = findClosestObject(LividIds.LOG_PILE, range = 50) != null

    /**
     * Whether the run has run dry.
     *
     * Only the loose runes can be counted today: a rune pouch keeps its contents on the item rather than
     * in the backpack, so a pouch is taken as proof the runes it holds are there. Stopping a farm that is
     * carrying thousands of runes because they happen to be in a pouch is the worse mistake of the two.
     */
    private fun outOfRunes(): Boolean {
        if (settings.minimumRunes.value <= 0) return false
        if (inventory.hasItem(LividIds.RUNE_POUCH)) return false
        return inventory.count(LividIds.NATURE_RUNE) < settings.minimumRunes.value ||
            inventory.count(LividIds.ASTRAL_RUNE) < settings.minimumRunes.value
    }

    /** Spaces one job from the next so a job that never completes cannot become a click every tick. */
    private fun ready(): Boolean {
        if (System.currentTimeMillis() - lastAction < gaussian(ACTION_GAP, ACTION_GAP_VARIANCE)) return false
        lastAction = System.currentTimeMillis()
        return true
    }

    /**
     * Murky Pat's event, matched on the option he offers rather than on him standing there.
     *
     * He is in the farm the whole time, so presence alone would have the bot walk over and cast at him
     * every pass; only the option appearing means the event is actually running.
     */
    private suspend fun helpMurkyPat(): Boolean {
        if (!settings.helpMurkyPat.value) return false
        if (System.currentTimeMillis() < murkyPatUntil) return false
        val pat = murkyPat() ?: return false
        if (!ready()) return false
        murkyPatUntil = System.currentTimeMillis() + MURKY_PAT_BACKOFF_MILLIS
        val option = pat.getDef().options.firstOrNull { it?.contains(LividIds.VENGEANCE, true) == true }
            ?: return false
        if (!pat.interactOrFirst(option)) return false
        delayUntil(gaussian(4000L, 900L)) { murkyPat() == null }
        log("Vengeance cast on Murky Pat.")
        return true
    }

    private fun murkyPat(): NPC? = findClosestNPC(maxRange = 30) { npc ->
        npc.id == LividIds.MURKY_PAT &&
            npc.getDef().options.any { it?.contains(LividIds.VENGEANCE, true) == true }
    }

    /**
     * Produce already in hand, which outranks everything else so the bot never wanders off holding it.
     *
     * Bunches go to the wagon and loose plants get bunched; five plants make a bunch, so ten plants is
     * two passes through here rather than one.
     */
    private suspend fun deliverProduce(): Boolean {
        if (!settings.handleProduce.value) return false

        val bunches = inventory.count(LividIds.LIVID_PLANT_BUNCH)
        if (bunches > 0) {
            val wagon = findClosestObject(LividIds.TRADE_WAGON, range = 30) ?: return false
            if (!ready() || !wagon.interact(LividIds.DEPOSIT_PRODUCE)) return false
            delayUntil(gaussian(12000L, 2500L)) { inventory.count(LividIds.LIVID_PLANT_BUNCH) < bunches }
            val delivered = bunches - inventory.count(LividIds.LIVID_PLANT_BUNCH)
            if (delivered > 0) {
                traded += delivered
                log("Traded $delivered bunch(es).")
            }
            return true
        }

        val plants = inventory.count(LividIds.LIVID_PLANT)
        if (plants > 0) {
            if (!ready() || !inventory.clickItem(LividIds.LIVID_PLANT, 1)) return false
            delayUntil(gaussian(4000L, 900L)) { inventory.count(LividIds.LIVID_PLANT) < plants }
            return true
        }
        return false
    }

    /**
     * Empty handed: take a full pile's worth.
     *
     * The only job here the Lua does not drive with the primary option - it asks for a different one - so
     * this is the one name that cannot fall back to "whatever is first" without taking the wrong amount.
     */
    private suspend fun collectProduce(): Boolean {
        if (!settings.handleProduce.value) return false
        val pile = namedObject(LividIds.FULL_PRODUCE_PILE) ?: return false
        if (!ready() || !pile.interact(LividIds.TAKE_PRODUCE)) return false
        delayUntil(gaussian(9000L, 2000L)) { inventory.count(LividIds.LIVID_PLANT) > 0 }
        return true
    }

    private suspend fun fertilisePatches(): Boolean {
        if (!settings.fertilisePatches.value) return false
        val patch = namedObject(LividIds.EMPTY_PATCH_NAME) ?: return false
        if (!ready() || !patch.interact(LividIds.FERTILISE)) return false
        delayUntil(gaussian(6000L, 1400L)) { done(patch, LividIds.FERTILISE) }
        fertilised++
        return true
    }

    /**
     * Curing a plant is two steps: the plant opens the cure interface, then the row its disease wants.
     *
     * Which row is right is decided by the plant's id, because the interface itself gives nothing away.
     */
    private suspend fun curePlants(): Boolean {
        if (!settings.curePlants.value) return false
        val plant = getAllObjectsWithinRange(SCAN_RANGE)
            .firstOrNull { it.name().contains(LividIds.DISEASED_PLANT_NAME, ignoreCase = true) }
            ?: return false
        val cure = LividIds.CURES[plant.visibleTypeId] ?: LividIds.CURES[plant.id] ?: return false
        if (!ready() || !plant.interact(LividIds.CURE_PLANT)) return false
        delayUntil(gaussian(5000L, 1200L)) { interfaces.isOpen(LividIds.CURE_INTERFACE) }
        if (!interfaces.isOpen(LividIds.CURE_INTERFACE)) return false
        delay(gaussian(CURE_SETTLE, CURE_SETTLE_VARIANCE))
        IFSlot(LividIds.CURE_INTERFACE, cure, -1).click(1)
        delayUntil(gaussian(5000L, 1200L)) { done(plant, LividIds.CURE_PLANT) }
        cured++
        return true
    }

    /**
     * Fences need a fencepost, and a fencepost is lumber from the pile that has been converted.
     *
     * The three are one job rather than three tasks, because a bot that fetches lumber while no fence is
     * broken is a bot carrying logs it will never use.
     */
    private suspend fun fixFences(): Boolean {
        if (!settings.fixFences.value) return false
        val fence = namedObject(LividIds.BROKEN_FENCE_NAME) ?: return false

        if (inventory.count(LividIds.LUNAR_FENCEPOST) <= 0) {
            if (inventory.count(LividIds.LUNAR_LUMBER) > 0) return convertLumber()
            return takeLumber()
        }

        if (!ready() || !fence.interact(LividIds.FIX_FENCE)) return false
        delayUntil(gaussian(8000L, 1800L)) { done(fence, LividIds.FIX_FENCE) }
        if (done(fence, LividIds.FIX_FENCE)) fenced++
        return true
    }

    private suspend fun convertLumber(): Boolean {
        val lumber = inventory.count(LividIds.LUNAR_LUMBER)
        if (!ready() || !inventory.clickItem(LividIds.LUNAR_LUMBER, 1)) return false
        delayUntil(gaussian(5000L, 1200L)) { inventory.count(LividIds.LUNAR_FENCEPOST) > 0 || inventory.count(LividIds.LUNAR_LUMBER) < lumber }
        return true
    }

    private suspend fun takeLumber(): Boolean {
        val pile = findClosestObject(LividIds.LOG_PILE, range = 30) ?: return false
        if (!ready() || !pile.interact(LividIds.TAKE_LUMBER)) return false
        delayUntil(gaussian(9000L, 2000L)) { inventory.count(LividIds.LUNAR_LUMBER) >= LUMBER_WANTED }
        return true
    }

    /**
     * Encouraging her, which she only sometimes wants.
     *
     * She is found by the option on her rather than by her id: the npc stands in the farm whether or not
     * she needs anything, so matching the id alone had the Lua walking over to spam her every pass. When
     * she answers that she is currently fine the chat handler backs off for a while.
     */
    private suspend fun encouragePauline(): Boolean {
        if (!settings.encouragePauline.value) return false
        if (System.currentTimeMillis() < paulineFineUntil) return false

        if (chooseEncouragement()) return true

        // Her drained form is a different npc type from the one standing here the rest of the time, and
        // only that form carries the name and the Encourage option - so the name is what identifies her.
        val pauline = findClosestNPC(maxRange = 30) {
            it.name().contains(LividIds.PAULINE_NAME, ignoreCase = true) &&
                it.hasOption(LividIds.ENCOURAGE)
        } ?: return false
        if (!ready() || !pauline.interact(LividIds.ENCOURAGE)) return false
        delayUntil(gaussian(6000L, 1400L)) {
            encouragementOffered() || System.currentTimeMillis() < paulineFineUntil
        }
        return chooseEncouragement()
    }

    private fun encouragementOffered(): Boolean =
        LividIds.ENCOURAGEMENTS.any { dialogueOptionVisible(it) }

    /** Answered by wording, because which lines are offered and their order change every round. */
    private suspend fun chooseEncouragement(): Boolean {
        val line = LividIds.ENCOURAGEMENTS.firstOrNull { dialogueOptionVisible(it) } ?: return false
        if (!continueDialogueContaining(line)) return false
        delay(gaussian(640, 180))
        log("Encouraged Pauline: $line")
        return true
    }

    /**
     * Whether a job on [obj] has landed.
     *
     * These never disappear when they are done - a cured plant turns back into a Livid and a fertilised
     * patch into a Fertilised patch, keeping the same instance - so waiting for the object to vanish
     * waits for something that never happens and burns the whole timeout every time. What does change is
     * the option: once the work is done the game stops offering it.
     */
    private fun done(obj: SceneObject, option: String): Boolean = !obj.exists || !obj.hasOption(option)

    private fun namedObject(name: String) = getAllObjectsWithinRange(SCAN_RANGE)
        .filter { it.name().contains(name, ignoreCase = true) }
        .minByOrNull { it.distanceTo(localPlayer.tileX.toDouble(), localPlayer.tileY.toDouble()) }

    private fun publish() {
        settings.stageInfo.value = stage
        settings.runesInfo.value =
            "${inventory.count(LividIds.NATURE_RUNE)} / ${inventory.count(LividIds.ASTRAL_RUNE)}"
        settings.carryingInfo.value =
            "${inventory.count(LividIds.LIVID_PLANT)} / ${inventory.count(LividIds.LIVID_PLANT_BUNCH)}"
        settings.tradedInfo.value = traded.toString()
        settings.jobsInfo.value = "$cured / $fertilised / $fenced"
    }

    private companion object {
        const val SCAN_RANGE = 30
        const val LUMBER_WANTED = 5
        const val POLL_MS = 600
        const val POLL_VARIANCE = 180
        // The window stays up between plants, so it reads as open before this plant's one is built.
        // This is the pause that lets the right rows arrive before one of them is pressed.
        const val CURE_SETTLE = 800
        const val CURE_SETTLE_VARIANCE = 200
        const val ACTION_GAP = 800
        const val ACTION_GAP_VARIANCE = 260
        const val PAULINE_BACKOFF_MILLIS = 20_000L
        const val MURKY_PAT_BACKOFF_MILLIS = 10_000L
    }
}

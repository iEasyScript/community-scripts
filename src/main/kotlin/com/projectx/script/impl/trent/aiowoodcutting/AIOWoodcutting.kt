package com.projectx.script.impl.trent.aiowoodcutting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.projectx.game.interfaces.IFSlot
import com.projectx.game.items.Item
import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigurableScript
import com.projectx.script.Script
import com.projectx.script.ScriptDescription
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.SkillTrackerReadout
import com.projectx.script.api.SkillTrackerSnapshot
import com.projectx.script.api.bankInventoryOption
import com.projectx.script.api.bankOpen
import com.projectx.script.api.captureSerenSpirit
import com.projectx.script.api.castAbility
import com.projectx.script.api.closeBank
import com.projectx.script.api.depositBankItem
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.findClosestReachableObject
import com.projectx.script.api.findSerenSpirit
import com.projectx.script.api.interfaces
import com.projectx.script.api.inventory
import com.projectx.script.api.isLoggedIn
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.api.openNearestBank
import com.projectx.script.api.spotAnims
import com.projectx.script.api.timeSinceLastAnim
import com.projectx.script.api.walkTo
import com.projectx.script.api.webWalk
import com.projectx.script.event.Event
import com.projectx.script.event.impl.ManualDoAction
import com.projectx.script.event.impl.XPDrop
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.Readout
import com.projectx.util.gaussian
import org.projectx.core.game.combat.Ability
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.gameval.Gameval
import world.gregs.voidps.type.Tile

@ScriptDescription(
    name = "AIO Woodcutting",
    version = "1.2.3",
    author = "Trent",
    description = "Cuts the tree you click and, with a full backpack, banks at the nearest bank, teleports to War's Retreat " +
        "to bank, drops the logs or burns them on a bonfire, then carries on where you were.",
)
class AIOWoodcutting : Script(), ConfigurableScript, ComposePanel {
    val settings = AIOWoodcuttingSettings()

    @Volatile private var treeName: String? = null
    @Volatile private var chopOption = CHOP
    @Volatile private var home: Tile? = null
    @Volatile private var homeIsTree = false
    private var boxFull = false
    private var trips = 0
    private var failedLights = 0
    private var burning = false
    private var failedDeposits = 0
    private var addedAt = 0L
    private var addSettle = 0L
    private var lastDoing = ""

    private val tracker = SkillTracker()
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }
    private val hud by live(WoodcutHud()) { WoodcutHud(treeName, settings.whenFull.value.toString(), settings.doing.value, trips) }

    private val bankPin get() = settings.bankPin.value.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }

    @Composable
    override fun Panel() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val state = hud
            val tree = state.tree
            if (tree == null) {
                Hint("Click a tree to start.")
            } else {
                Readout(listOf("Tree" to tree, "Full backpack" to state.mode, "Doing" to state.doing, "Trips" to state.trips.toString()))
            }
            SkillTrackerReadout(progress)
        }
    }

    override fun onEvent(event: Event) {
        when (event) {
            is XPDrop -> if (event.skill == Skill.WOODCUTTING && event.gainedXp >= LOG_XP_FLOOR) tracker.add("Logs")
            is ManualDoAction -> (event.target as? SceneObject)?.let { choose(it) }
            else -> Unit
        }
    }

    private fun choose(tree: SceneObject) {
        val option = when {
            tree.hasOption(CHOP) -> CHOP
            tree.hasOption(CUT) -> CUT
            else -> return
        }
        treeName = tree.name()
        chopOption = option
        home = tree.tile
        homeIsTree = true
    }

    private fun publish(text: String) {
        settings.doing.value = text
        if (text != lastDoing) {
            lastDoing = text
            log(text)
        }
    }

    override suspend fun loop() {
        if (!isLoggedIn()) return
        if (captureSerenSpirit()) return tracker.add("Seren spirits")
        if (treeName == null) {
            publish("Click a tree to start")
            delay(gaussian(1200, 90_000))
            return
        }
        if (inventory.isFull && fillWoodBox()) return
        if (burning || inventory.isFull) return unload()
        chop()
    }

    private fun logs(): List<Item> = inventory.filter { LOG_NAME.matches(it.name) }

    private fun woodBox(): Item? = inventory.firstOrNull { WOOD_BOX.matches(it.name) }

    /** Fills the wood box when the backpack is full; a fill that frees nothing means the box is full until it is emptied. */
    private suspend fun fillWoodBox(): Boolean {
        if (!settings.useWoodBox.value || boxFull || logs().isEmpty()) return false
        val box = woodBox() ?: return false
        publish("Filling the wood box")
        val free = inventory.freeSlots
        if (box.click(FILL)) waitThenDelayUntil(REACTION_FLOOR, gaussian(2400L, 90_000L)) { inventory.freeSlots > free }
        if (inventory.freeSlots <= free) boxFull = true
        return true
    }

    private suspend fun chop() {
        val name = treeName ?: return
        val tree = findClosestReachableObject(TREE_RANGE) { it.name() == name && it.hasOption(chopOption) }
        if (tree == null) {
            val spot = home
            if (spot != null && awayFrom(spot)) return goHome(spot)
            publish("Waiting for a $name to grow back")
            delay(gaussian(1500, 160_000))
            return
        }
        if (name == ETERNAL_MAGIC) followEternalSpot()
        publish("Cutting ${name.lowercase()}")
        if (!tree.interact(chopOption)) {
            delay(gaussian(900, 60_000))
            return
        }
        waitThenDelayUntil(REACTION_FLOOR, gaussian(1200L, 90_000L)) { !localPlayer.isMoving }
        // A tree's own tile can't be stood on, so the way back is to where the first cut was made from.
        if (homeIsTree && localPlayer.isAnimating) {
            home = localPlayer.tile
            homeIsTree = false
        }
        delayUntil(gaussian(CHOP_TIMEOUT, 15_592L)) {
            !tree.exists || inventory.isFull || timeSinceLastAnim > gaussian(IDLE_AFTER_ANIM, 1150) ||
                findSerenSpirit() != null || (name == ETERNAL_MAGIC && eternalSpotMoved())
        }
    }

    private fun eternalSpot() = spotAnims.firstOrNull { it.id == ETERNAL_SPOT_ANIM }

    private fun eternalSpotMoved() = eternalSpot()?.let { it.tile != localPlayer.tile } == true

    private suspend fun followEternalSpot() {
        val spot = eternalSpot() ?: return
        if (spot.tile != localPlayer.tile && walkTo(spot.tile, false)) delayUntil(5000) { spot.tile == localPlayer.tile }
    }

    private suspend fun goHome(spot: Tile) {
        publish("Walking back to the trees")
        val walk = webWalk(spot, HOME_ARRIVE)
        if (!walk.isSuccess && awayFrom(spot)) {
            log("Couldn't walk back to ${spot.x}, ${spot.y}: $walk")
            delay(gaussian(2000, 250_000))
        }
    }

    private fun awayFrom(spot: Tile) = localPlayer.tile.distanceTo(spot).let { it < 0 || it > HOME_RADIUS }

    private suspend fun unload() {
        when (settings.whenFull.value) {
            WhenFull.DROP -> drop()
            WhenFull.BONFIRE -> burn()
            WhenFull.BANK -> bank(teleport = false)
            WhenFull.TELEPORT_BANK -> bank(teleport = true)
        }
    }

    private suspend fun drop() {
        publish("Dropping the logs")
        for (item in logs()) {
            if (!item.click(DROP)) continue
            delay(gaussian(DROP_GAP, 3_600))
        }
        waitThenDelayUntil(REACTION_FLOOR, gaussian(1800L, 90_000L)) { logs().isEmpty() }
    }

    private suspend fun bank(teleport: Boolean) {
        if (!bankOpen) {
            if (teleport && !atWarsRetreat()) {
                publish("Teleporting to War's Retreat")
                if (castAbility(Ability.WARS_RETREAT_TELEPORT)) {
                    waitThenDelayUntil(REACTION_FLOOR, gaussian(WARS_ARRIVAL, 1_000_000L)) { atWarsRetreat() }
                    if (atWarsRetreat()) return
                }
                log("The War's Retreat teleport didn't work; banking at the nearest bank instead")
            }
            publish("Banking")
            if (!openNearestBank(bankPin)) delay(gaussian(1500, 250_000))
            // Deposits wait for the next pass: a bank reported open mid-click can still be redrawing and drop them.
            else delay(gaussian(450, 20_000))
            return
        }
        if (logs().isNotEmpty()) {
            publish("Depositing logs")
            for (name in logs().map { it.name }.distinct()) {
                if (depositBankItem(name)) delay(gaussian(320, 10_000))
            }
            waitThenDelayUntil(REACTION_FLOOR, gaussian(2400L, 90_000L)) { logs().isEmpty() }
            if (logs().isNotEmpty() && ++failedDeposits < MAX_FAILED_DEPOSITS) return
            if (logs().isNotEmpty()) log("The logs wouldn't deposit after $failedDeposits tries")
        }
        failedDeposits = 0
        val box = woodBox()
        if (settings.useWoodBox.value && box != null && bankInventoryOption(box.id, EMPTY_BOX)) delay(gaussian(600, 40_000))
        boxFull = false
        trips++
        closeBank()
        delay(gaussian(350, 10_000))
        home?.let { goHome(it) }
    }

    private fun atWarsRetreat() = localPlayer.tile.distanceTo(WARS_RETREAT).let { it in 0..WARS_RADIUS }

    private fun burningUnderway() = localPlayer.isAnimating && localPlayer.animationId == BONFIRE_ANIM

    /** Lights a fire with one log when none burns nearby, then adds the rest through the fire's Use, Add to bonfire. */
    private suspend fun burn() {
        val logs = logs()
        burning = logs.isNotEmpty()
        if (!burning) return
        if (burningUnderway()) {
            publish("Burning logs on the bonfire")
            delayUntil(gaussian(BONFIRE_TIMEOUT, 2_000_000L)) {
                logs().isEmpty() || timeSinceLastAnim > gaussian(IDLE_AFTER_ANIM, 1150) || findSerenSpirit() != null
            }
            return
        }
        // After Add to bonfire the player walks to the fire before burning starts; clicking again meanwhile repeats the add.
        if (System.currentTimeMillis() - addedAt < addSettle) {
            delay(gaussian(200, 2_500))
            return
        }
        val fire = findClosestObject(FIRE_RANGE) { it.name() == FIRE && it.hasOption(USE) }
        if (fire == null) return light(logs.first())
        failedLights = 0
        if (!choiceShowing()) {
            publish("Using the fire")
            if (!fire.interact(USE)) {
                delay(gaussian(900, 60_000))
                return
            }
            waitThenDelayUntil(REACTION_FLOOR, gaussian(4000L, 640_000L)) { choiceShowing() }
        }
        val add = BONFIRE_CHOICES.firstOrNull { (title, _) -> componentText(title).equals(ADD_TO_BONFIRE, ignoreCase = true) }
        if (add == null) {
            delay(gaussian(900, 60_000))
            return
        }
        publish("Adding logs to the bonfire")
        if (!add.second.dialogueContinue()) {
            delay(gaussian(900, 60_000))
            return
        }
        val held = logs.size
        addedAt = System.currentTimeMillis()
        addSettle = gaussian(ADD_SETTLE, 640_000L)
        waitThenDelayUntil(REACTION_FLOOR, addSettle) { burningUnderway() || logs().size < held }
    }

    private suspend fun light(first: Item) {
        if (failedLights >= MAX_FAILED_LIGHTS) {
            log("Couldn't light a fire here after $failedLights tries; dropping the logs instead")
            failedLights = 0
            burning = false
            return drop()
        }
        publish("Lighting a fire")
        if (first.click(LIGHT)) {
            waitThenDelayUntil(REACTION_FLOOR, gaussian(LIGHT_TIMEOUT, 1_000_000L)) {
                findClosestObject(FIRE_RANGE) { it.name() == FIRE && it.hasOption(USE) } != null
            }
        }
        if (findClosestObject(FIRE_RANGE) { it.name() == FIRE } == null) failedLights++
    }

    private fun choiceShowing() = interfaces.getComponentRaw(CHOICE_TITLE.interfaceId, CHOICE_TITLE.componentId)?.visible == true

    private fun componentText(slot: IFSlot) = interfaces.getComponentRaw(slot.interfaceId, slot.componentId)
        ?.takeIf { it.visible }?.text ?: ""

    companion object {
        private const val CHOP = "Chop down"
        private const val CUT = "Cut down"
        private const val FILL = "Fill"
        private const val DROP = "Drop"
        private const val LIGHT = "Light"
        private const val USE = "Use"
        private const val FIRE = "Fire"
        private const val ADD_TO_BONFIRE = "Add to bonfire"
        private const val EMPTY_BOX = "Empty - logs and bird's nests"
        private const val ETERNAL_MAGIC = "Eternal magic tree"
        private const val ETERNAL_SPOT_ANIM = 8447
        private const val BONFIRE_ANIM = 16703
        private const val LOG_XP_FLOOR = 15

        private val LOG_NAME = Regex("(?i)^(logs|.* logs)$")
        private val WOOD_BOX = Regex("(?i).*wood box$")

        private const val TREE_RANGE = 24
        private const val HOME_RADIUS = 20
        private const val HOME_ARRIVE = 6
        private const val FIRE_RANGE = 6
        private const val WARS_RADIUS = 30
        private val WARS_RETREAT = Tile.of(3299, 10131, 0)

        private const val REACTION_FLOOR = 250L
        private const val CHOP_TIMEOUT = 125_920L
        private const val IDLE_AFTER_ANIM = 5_299
        private const val BONFIRE_TIMEOUT = 90_000L
        private const val LIGHT_TIMEOUT = 6_000L
        private const val WARS_ARRIVAL = 6_000L
        private const val DROP_GAP = 140
        private const val MAX_FAILED_LIGHTS = 3
        private const val MAX_FAILED_DEPOSITS = 3
        private const val ADD_SETTLE = 5_000L

        private fun slot(name: String) = Gameval.requireComponentHash(name).let { IFSlot(it ushr 16, it and 0xFFFF) }

        private val CHOICE_TITLE = slot("object_choice:title")
        private val BONFIRE_CHOICES = (1..4).map { n -> slot("object_choice:button_title_$n") to slot("object_choice:button_graphics_$n") }
    }
}

data class WoodcutHud(val tree: String? = null, val mode: String = "", val doing: String = "", val trips: Int = 0)

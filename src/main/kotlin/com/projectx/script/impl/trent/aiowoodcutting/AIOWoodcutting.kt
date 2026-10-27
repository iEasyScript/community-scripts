package com.projectx.script.impl.trent.aiowoodcutting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.type.Tile
import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.ComposePanel
import com.projectx.script.ScriptDescription
import com.projectx.script.State
import com.projectx.script.StateMachineScript
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.ManualDoAction
import com.projectx.script.event.impl.XPDrop
import com.projectx.traversal.Traversal
import com.projectx.traversal.Traversal.Companion.traversal
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.Readout
import com.projectx.util.gaussian

@ScriptDescription(
    name = "AIO Woodcutting",
    version = "1.1.0",
    author = "Trent",
    description = "Woodcuts at various locations."
)
class AIOWoodcutting : StateMachineScript<AIOWoodcutting>(), ComposePanel {
    lateinit var wcSpot: WoodcuttingSpot
    fun hasWcSpot() = ::wcSpot.isInitialized

    lateinit var otherName: String
    fun hasOtherName() = ::otherName.isInitialized
    lateinit var otherOp: String
    fun hasOtherOp() = ::otherOp.isInitialized

    private val tracker = SkillTracker(Skill.WOODCUTTING)
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }
    private val location by live<String?>(null) {
        if (hasWcSpot()) wcSpot.toString() else if (hasOtherName()) otherName else null
    }

    @Composable
    override fun Panel() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val spot = location
            if (spot != null) Readout(listOf("Location" to spot)) else Hint("No location yet. Click a tree.")
            SkillTrackerReadout(progress)
        }
    }

    override fun getStartState() = Init

    override fun onEvent(event: Event) {
        super.onEvent(event)
        if (event !is XPDrop || event.skill != Skill.WOODCUTTING || event.gainedXp < 15) return
        tracker.add("Logs")
    }
}

object Init: State<AIOWoodcutting>() {
    override suspend fun AIOWoodcutting.checkNext() = if (hasWcSpot() || hasOtherName()) Chop() else null

    override suspend fun AIOWoodcutting.stateLoop() { }

    override fun AIOWoodcutting.onStateEvent(event: Event) {
        if (event !is ManualDoAction || event.target !is SceneObject) return
        val tree = event.target as SceneObject
        if (!hasWcSpot() && tree.hasOption("Chop down") || tree.hasOption("Cut down")) {
            val spot = WoodcuttingSpot.find(tree.name(), tree.tile)
            if (spot != null)
                wcSpot = spot
            else {
                otherName = tree.name()
                otherOp = tree.defs.getOp(0)
            }
        }
    }
}

private val woodboxes = """.*wood box$""".toRegex()

class Chop: State<AIOWoodcutting>() {
    var currentTree: SceneObject? = null
    var treesNotFound = false

    override suspend fun AIOWoodcutting.checkNext() = if (treesNotFound && hasWcSpot() && wcSpot.returnTraversal != null) wcSpot.returnTraversal!!() else if (inventory.isFull && isWoodboxFull && hasWcSpot()) Bank() else null

    override suspend fun AIOWoodcutting.stateLoop() {
        val name = if (hasWcSpot()) wcSpot.treeName else otherName
        val option = if (hasWcSpot()) wcSpot.option else otherOp
        currentTree = null
        if (inventory.freeSlots <= 2 && inventory.hasItem(woodboxes) && !isWoodboxFull) {
            inventory.clickItem(woodboxes, "Fill")
            delayUntil(2500) { inventory.freeSlots > 2 }
            return
        }
        if (getRealLevel(Skill.WOODCUTTING) >= 90)
            currentTree = findClosestReachableObject(24) { it.typeId == 87508 && it.hasOption(option) }
        if (currentTree == null)
            currentTree = findClosestReachableObject(24) { it.name() == name && it.hasOption(option) }
        if (currentTree == null) {
            treesNotFound = true
            return
        }
        if (currentTree?.name() == "Eternal magic tree") {
            spotAnims.firstOrNull { it.id == 8447 }?.let {
                if (it.tile != localPlayer.tile && walkTo(it.tile, false))
                    delayUntil(5000) { it.tile == localPlayer.tile }
            }
        }
        if (currentTree?.interact(if (hasWcSpot()) wcSpot.option else otherOp) == true) {
            waitThenDelayUntil(1200) { !localPlayer.isMoving }
            delayUntil(gaussian(125920L, 15592L)) {
                currentTree?.exists == false ||
                (inventory.isFull && isWoodboxFull) ||
                timeSinceLastAnim > gaussian(5299, 1150) ||
                (!isWoodboxFull && inventory.freeSlots <= 2) ||
                (currentTree?.name() == "Eternal magic tree" && spotAnims.any { it.id == 8447 && it.tile != localPlayer.tile })
            }
        }
    }
}

class Bank: State<AIOWoodcutting>() {
    var noBank = false

    override suspend fun AIOWoodcutting.checkNext() = if (noBank && hasWcSpot() && wcSpot.bankTraversal != null) wcSpot.bankTraversal!!() else if (!inventory.isFull) Chop() else null

    override suspend fun AIOWoodcutting.stateLoop() {
        val bank = findClosestReachableObject { it.hasOption("Load Last Preset from") }
        if (bank == null) {
            noBank = true
            return
        }
        val woodbox = inventory.getItem(woodboxes)
        if (woodbox != null && isWoodboxFull && woodbox.useOn(bank)) {
            delayUntil(gaussian(5692L, 1105L)) { !isWoodboxFull }
            return
        }
        if (bank.interact("Load Last Preset from"))
            delayUntil(gaussian(5692L, 1105L)) { !inventory.isFull }
    }
}

enum class WoodcuttingSpot(val treeName: String, val option: String, val areaTile: Tile, val bankTraversal: (() -> Traversal<AIOWoodcutting>)? = null, val returnTraversal: (() -> Traversal<AIOWoodcutting>)? = null) {
    TREE_DRAYNOR("Tree", "Chop down", Tile.of(3122, 3255, 0)),
    OAK_DRAYNOR("Oak", "Chop down", Tile.of(3122, 3255, 0)),
    WILLOW_DRAYNOR("Willow", "Chop down", Tile.of(3090, 3230, 0)),
    MAPLE_SEERS("Maple Tree", "Chop down", Tile.of(2727, 3496, 0)),
    YEW_CATHERBY("Yew", "Chop down", Tile.of(2768, 3433, 0), ::bankCatherbyYews, ::returnCatherbyYews),
    MAGIC_SEERS("Magic tree", "Chop down", Tile.of(2222, 3326, 1)),
    ACADIA_MENAPHOS("Acadia tree", "Cut down", Tile.of(3037, 9738, 0)),
    ETERNAL_MAGICS("Eternal magic tree", "Chop down", Tile.of(2328, 3589, 0), ::bankEternalMagics, ::returnEternalMagics),
    ;

    companion object {
        fun find(treeName: String, tile: Tile) =
            entries.find { spot -> spot.treeName.equals(treeName, true) && spot.areaTile.withinDistance(tile, 25) }
    }
}

private val bankCatherbyYews
    get() = traversal(Bank(), { inventory.freeSlots > 1 }) {
        path(localPlayer.tile, Tile.of(2787, 3439, 0)) { Tile.of(2787, 3439, 0).withinDistance(localPlayer.tile, 7) }
    }

private val returnCatherbyYews
    get() = traversal(Chop(), { Tile.of(2767, 3433, 0).withinDistance(localPlayer.tile, 7) }) {
        path(Tile.of(2796, 3438, 0), Tile.of(2767, 3433, 0))
    }

private val bankEternalMagics
    get() = traversal(Bank(), { inventory.freeSlots > 1 }) {
        path(localPlayer.tile, Tile.of(2282, 3554, 0)) { Tile.of(2282, 3554, 0).withinDistance(localPlayer.tile, 7) }
    }

private val returnEternalMagics
    get() = traversal(Chop(), { Tile.of(2328, 3589, 0).withinDistance(localPlayer.tile, 7) }) {
        path(Tile.of(2280, 3558, 0), Tile.of(2328, 3589, 0))
    }

private val baseCapacity = when (inventory.getItem(woodboxes)?.name ?: "nothin") {
    "Wood box" -> 70
    "Oak wood box" -> 80
    "Willow wood box" -> 90
    "Teak wood box" -> 100
    "Maple wood box" -> 110
    "Acadia wood box" -> 120
    "Mahogany wood box" -> 130
    "Yew wood box" -> 140
    "Magic wood box" -> 150
    "Elder wood box" -> 160
    "Eternal magic wood box" -> 170
    else -> 70
}

private val levelCapacity get() = ((getRealLevel(Skill.WOODCUTTING) - 5) / 10 * 10 + 10).coerceIn(10, 110)

private val logNames = setOf("Logs", "Oak logs", "Willow logs", "Teak logs", "Maple logs", "Acadia logs", "Mahogany logs", "Yew logs", "Magic logs", "Elder logs", "Eternal magic logs")

private val isWoodboxFull get() =
    if (inventory.getItem(woodboxes)?.name == null)
        true
    else
        logNames.any { (woodbox.getItem(it)?.amount ?: 0) >= baseCapacity + levelCapacity }
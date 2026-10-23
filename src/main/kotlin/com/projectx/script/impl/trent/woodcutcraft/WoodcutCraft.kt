package com.projectx.script.impl.trent.woodcutcraft

import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.ConfigurableScript
import com.projectx.script.EnumConfigItem
import com.projectx.script.ScriptDescription
import com.projectx.script.StringConfigItem
import com.projectx.script.State
import com.projectx.script.StateMachineScript
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.ManualDoAction
import com.projectx.game.interfaces.IFSlot
import com.projectx.util.gaussian
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.gameval.Gameval
import world.gregs.voidps.type.Tile

@ScriptDescription(
    name = "WoodcutCraft",
    version = "1.2.0",
    author = "Trent",
    description = "Cuts the tree you click (or the nearest one), then fletches the logs into the best-experience item " +
        "you can make, disassembles it for Invention or banks it, and goes again."
)
class WoodcutCraft : StateMachineScript<WoodcutCraft>(), ConfigurableScript {
    val fullPack = EnumConfigItem(
        "When the pack is full",
        "What to do with the fletched items. Auto disassembles them once you have unlocked Invention and banks them before that.",
        FullPack.entries.toTypedArray(),
        FullPack.AUTO,
    )
    val bankPin = StringConfigItem("Bank PIN", "Typed for you when the bank asks for it. Leave it empty to type it yourself.", "")

    lateinit var treeName: String
    var treeSpot: Tile? = null
    fun selectedTreeName() = ::treeName.isInitialized

    fun selectTree(tree: SceneObject) {
        treeName = tree.name()
        treeSpot = tree.tile
    }

    val banksProducts: Boolean
        get() = when (fullPack.value) {
            FullPack.AUTO -> getXp(Skill.INVENTION) == 0
            FullPack.BANK -> true
            FullPack.DISASSEMBLE -> false
        }

    val productsState: State<WoodcutCraft> get() = if (banksProducts) BankProducts else Disassemble

    val validBankPin: String?
        get() = bankPin.value.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }

    override fun getStartState() = Init
}

enum class FullPack(private val label: String) {
    AUTO("Auto"),
    DISASSEMBLE("Disassemble"),
    BANK("Bank");

    override fun toString() = label
}

object Init: State<WoodcutCraft>() {
    override suspend fun WoodcutCraft.checkNext(): State<WoodcutCraft>? = when {
        inventory.count(logs) >= 2 && inventory.isFull -> Craft
        inventory.hasItem(unf) && inventory.isFull -> productsState
        selectedTreeName() -> Woodcut
        else -> null
    }

    override suspend fun WoodcutCraft.stateLoop() {
        val nearest = findClosestReachableObject(TREE_RANGE) { it.chopOption() != null }
        if (nearest == null) {
            delay(600, 200)
            return
        }
        selectTree(nearest)
        log("Cutting ${nearest.name()}; click another tree to change it.")
    }

    override fun WoodcutCraft.onStateEvent(event: Event) {
        if (event !is ManualDoAction || event.target !is SceneObject) return
        val obj = event.target as SceneObject
        if (!selectedTreeName() && obj.chopOption() != null)
            selectTree(obj)
    }
}

private val logs = Regex(".*\\slogs$")
private val unf = Regex(".*(\\s\\(unstrung\\)| stock)$")

private val CHOP_OPTIONS = listOf("Chop down", "Cut down")
private fun SceneObject.chopOption() = CHOP_OPTIONS.firstOrNull { hasOption(it) }

private const val TREE_RANGE = 20
private const val TREE_ARRIVE_DISTANCE = 5

/** The tool that fletches, as the "What do you want to do with the logs?" window names it. */
private const val FLETCH_WITH = "Knife"

/** The "What do you want to do with ...?" window. It takes its choice from the number keys, not from clicks. */
private object ObjectChoice {
    private const val MAX_HOTKEY = 9
    private val titles: List<IFSlot> by lazy {
        (1..MAX_HOTKEY).mapNotNull { n -> Gameval.componentHash("object_choice:button_title_$n")?.let(::slot) }
    }
    private val window by lazy { Gameval.requireComponentHash("object_choice:title") ushr 16 }

    val isOpen get() = interfaces.isOpen(window)

    /** The number key that picks the button titled [title], or null when no button has that title. */
    fun hotkey(title: String): Char? {
        val index = titles.indexOfFirst {
            interfaces.getComponent(it.interfaceId, it.componentId)?.text.equals(title, ignoreCase = true)
        }
        return if (index < 0) null else '1' + index
    }

    private fun slot(hash: Int) = IFSlot(hash ushr 16, hash and 0xFFFF)
}

/** Fletching products from a log, most experience first: each takes one action, so this is also experience per hour. */
private val BEST_PRODUCTS = listOf("longbow (unstrung)", "shortbow (unstrung)", " stock", "arrow shaft")

object Woodcut: State<WoodcutCraft>() {
    override suspend fun WoodcutCraft.checkNext() = if (inventory.isFull) if (inventory.hasItem(unf)) productsState else Craft else null

    override suspend fun WoodcutCraft.stateLoop() {
        captureSerenSpirit()
        if (localPlayer.isAniMoving) return
        val tree = findClosestReachableObject(TREE_RANGE) { it.name() == treeName && it.chopOption() != null }
        if (tree == null) {
            treeSpot?.let { webWalk(it, TREE_ARRIVE_DISTANCE) }
            delay(600, 200)
            return
        }
        val option = tree.chopOption() ?: return
        if (!tree.interact(option)) return
        delayUntil(15592) { localPlayer.isAnimating || !tree.exists }
        // The chop animation flickers and stops as a tree falls, so wait on the tree itself.
        delayUntil(gaussian(120000L, 15000L)) {
            !tree.exists || inventory.isFull || timeSinceLastAnim > gaussian(4200, 900) || findSerenSpirit() != null
        }
    }
}

object Craft: State<WoodcutCraft>() {
    override suspend fun WoodcutCraft.checkNext() = if (inventory.count(logs) < 2) Init else null

    override suspend fun WoodcutCraft.stateLoop() {
        if (localPlayer.isAnimating || MakeX.inProgress) return
        if (!MakeX.isOpen) {
            if (ObjectChoice.isOpen) {
                val key = ObjectChoice.hotkey(FLETCH_WITH)
                if (key != null && typeText(key.toString())) delayUntil(gaussian(4000L, 800L)) { MakeX.isOpen }
                else delay(600, 200)
                return
            }
            if (inventory.clickItem(logs, "Craft")) delayUntil(gaussian(4000L, 800L)) { MakeX.isOpen || ObjectChoice.isOpen }
            else delay(600, 200)
            return
        }
        if (!selectBestProduct()) {
            log("None of the fletching products can be made from these logs; stopping.")
            stop()
            return
        }
        if (makeXConfirm()) waitThenDelayUntil(gaussian(1200L, 300L), gaussian(60000L, 8000L)) { !MakeX.inProgress }
    }

    private suspend fun WoodcutCraft.selectBestProduct(): Boolean {
        for (product in BEST_PRODUCTS) {
            if (MakeX.craftables().none { it.name.endsWith(product, ignoreCase = true) }) continue
            if (!makeXSelect({ it.endsWith(product, ignoreCase = true) })) continue
            delayUntil(gaussian(2000L, 500L)) { MakeX.maxQuantity > 0 }
            if (MakeX.maxQuantity > 0) return true
        }
        return false
    }
}

object Disassemble: State<WoodcutCraft>() {
    override suspend fun WoodcutCraft.checkNext() = if (!inventory.hasItem(unf)) Init else null

    override suspend fun WoodcutCraft.stateLoop() {
        if (localPlayer.isAnimating) return
        if (inventory.getItem(unf)?.disassemble() == true)
            delayUntil(timeoutMillis = 50000) { !inventory.hasItem(unf) }
        else delay(600, 200)
    }
}

object BankProducts: State<WoodcutCraft>() {
    override suspend fun WoodcutCraft.checkNext() = if (!inventory.hasItem(unf)) Init else null

    override suspend fun WoodcutCraft.stateLoop() {
        if (localPlayer.isMoving) return
        if (!bankOpen) {
            if (!openNearestBank(validBankPin)) {
                log("Couldn't reach or open a bank; stopping.")
                stop()
            }
            return
        }
        val before = inventory.count(unf)
        if (depositBankItem(unf)) delayUntil(gaussian(2500L, 500L)) { inventory.count(unf) < before }
        else delay(600, 200)
    }
}

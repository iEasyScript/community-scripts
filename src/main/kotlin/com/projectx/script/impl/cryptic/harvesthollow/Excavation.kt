package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.game.interfaces.IFSlot
import com.projectx.game.nxt.entity.location.SceneObject
import com.projectx.script.Script
import com.projectx.script.api.BankPin
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.findClosestNPC
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.interfaces
import com.projectx.script.api.inventory
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.api.timeSpriteTile
import com.projectx.util.gaussian
import org.projectx.core.game.skill.Skill
import world.gregs.voidps.gameval.Gameval
import world.gregs.voidps.type.Tile

/**
 * Digs the graveyard's Mystery remains. A dig runs until something interrupts it - an artefact turning up, the
 * remains closing - so the next dig starts whenever the digging stops, aimed at the remains a time sprite sits on.
 * Everything found stacks, so the backpack does not fill. Once enough Ancient remains are carried they are identified
 * and the finds go to Eep. A contribution takes the pieces the collection's current round still needs, and finishing a
 * round starts another, so each collection is contributed to until a contribution takes nothing.
 */
object Excavation : HollowActivity {
    private const val REMAINS = 131355
    private const val EEP = 31287
    private const val ANCIENT_REMAINS = 57577
    private const val CONTRIBUTE_BUTTON = 0
    private const val RANGE = 30
    private const val SPRITE_PATCH = 2
    private const val MIN_FREE_SLOTS = 2
    private const val TICK_MS = 600

    private val DIG_ANIMATIONS = setOf(35906, 35907)

    private enum class Collection(val listEntry: Int, val pieces: IntArray) {
        ARTEFACTS(0, intArrayOf(57586, 57587, 57588, 57589, 57590)),
        BOUNTY_OF_BONES(1, intArrayOf(57578, 57579, 57580, 57581, 57582, 57583));

        fun held() = pieces.sumOf { inventory.count(it) }

        fun kinds() = pieces.count { inventory.hasItem(it) }
    }

    private val HOARD = Gameval.requireId(Gameval.INTERFACE, "collection_log")
    private val CONTRIBUTE = hoardComponent("info_footer_control_layer")
    private val COLLECTION_LIST = hoardComponent("collection_list_control_layer")
    private val CLOSE = hoardComponent("mainmodal_window_close_button")

    private fun hoardComponent(name: String) = Gameval.requireComponentHash("collection_log:$name") and 0xFFFF

    private var handInDue = false
    private var trip: ArrayDeque<Collection>? = null
    private var digging: Tile? = null
    private var pendingSprite: Tile? = null
    private var followAt = 0L
    private var dug = 0
    private var given = 0
    private var status = "Starting"
    private val xp = SkillTracker(Skill.ARCHAEOLOGY)

    override fun begin() {
        handInDue = false
        trip = null
        digging = null
        pendingSprite = null
        dug = 0
        given = 0
        status = "Starting"
        xp.reset()
    }

    override fun report(settings: HollowSettings, hours: Double): HollowReport {
        val bones = Collection.BOUNTY_OF_BONES
        val artefacts = Collection.ARTEFACTS
        return HollowReport(
            status,
            HudMeter("Bounty of Bones", "${bones.kinds()} / ${bones.pieces.size}", bones.kinds() / bones.pieces.size.toFloat()),
            listOf(
                HudTile("Artefacts", "${artefacts.kinds()} / ${artefacts.pieces.size}", HollowIcon.CANDLE),
                HudTile("Given to Eep", "$given", HollowIcon.SKULL),
                HudTile("XP gained", compact(xp.xpGained(Skill.ARCHAEOLOGY)), HollowIcon.FLAME),
                HudTile("XP / hr", compact(xp.xpPerHour(Skill.ARCHAEOLOGY)), HollowIcon.HOURGLASS),
            ),
        )
    }

    override suspend fun step(script: Script, settings: HollowSettings): Boolean {
        if (handInDue) return handIn(script, settings)
        if (inventory.freeSlots < MIN_FREE_SLOTS) {
            status = "The backpack is full"
            script.log("The backpack is full of things that are not Harvest Hollow finds; stopping.")
            return false
        }

        val sprite = if (settings.followSprite.value) timeSpriteTile(RANGE) else null
        val current = digging
        if (sprite != null && (current == null || !sprite.withinDistance(current, SPRITE_PATCH)) && !followDue(sprite, settings)) {
            val ticks = ((followAt - System.currentTimeMillis()) / TICK_MS + 1).coerceAtLeast(1)
            status = "Sprite moved: following in $ticks tick${if (ticks == 1L) "" else "s"}"
            script.delayUntil((followAt - System.currentTimeMillis()).coerceAtLeast(1L)) {
                timeSpriteTile(RANGE)?.withinDistance(sprite, SPRITE_PATCH) != true
            }
            return true
        }
        if (isDigging() && current != null && (sprite == null || sprite.withinDistance(current, SPRITE_PATCH))) {
            status = if (sprite != null) "Digging where the sprite sits" else "Digging the remains"
            script.delayUntil(gaussian(4000L, 700L)) {
                !isDigging() || (settings.followSprite.value && timeSpriteTile(RANGE)?.withinDistance(current, SPRITE_PATCH) == false)
            }
            return true
        }

        if (!isDigging() && inventory.count(ANCIENT_REMAINS) >= settings.identifyAt.value) return identify(script)

        val target = remainsAt(sprite) ?: findClosestObject(REMAINS, RANGE)
        if (target == null) {
            status = "Waiting for remains to dig"
            script.delay(gaussian(2200, 600))
            return true
        }
        return dig(script, target)
    }

    private fun isDigging() = localPlayer.animationId in DIG_ANIMATIONS

    /**
     * Whether the wait before following a sprite to [sprite] is over. A sprite seen somewhere new starts a wait of a
     * random number of ticks between the two settings, so the move reads as a reaction rather than a reflex.
     */
    private fun followDue(sprite: Tile, settings: HollowSettings): Boolean {
        val now = System.currentTimeMillis()
        val waitingFor = pendingSprite
        if (waitingFor == null || !waitingFor.withinDistance(sprite, SPRITE_PATCH)) {
            val low = minOf(settings.spriteDelayMin.value, settings.spriteDelayMax.value)
            val high = maxOf(settings.spriteDelayMin.value, settings.spriteDelayMax.value)
            val ticks = (low..high).random()
            pendingSprite = sprite
            followAt = if (ticks == 0) now else now + ticks * TICK_MS + gaussian(0, 90).coerceIn(-150, 150)
        }
        return now >= followAt
    }

    private fun remainsAt(sprite: Tile?): SceneObject? =
        sprite?.let { at -> findClosestObject(RANGE) { it.id == REMAINS && it.tile.withinDistance(at, SPRITE_PATCH) } }

    private suspend fun dig(script: Script, remains: SceneObject): Boolean {
        status = "Walking to the remains"
        script.delay(gaussian(380, 160))
        if (!remains.interact("Excavate")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(9000L, 1500L)) { isDigging() }
        if (isDigging()) {
            digging = remains.tile
            pendingSprite = null
            dug++
        }
        return true
    }

    private suspend fun identify(script: Script): Boolean {
        val remains = inventory.getItem(ANCIENT_REMAINS) ?: return true
        val before = remains.amount
        status = "Identifying the remains"
        if (!remains.click("Identify all")) {
            script.delay(gaussian(900, 250))
            return true
        }
        script.delayUntil(gaussian(5000L, 900L)) { inventory.count(ANCIENT_REMAINS) < before }
        if (inventory.count(ANCIENT_REMAINS) < before) handInDue = true
        script.delay(gaussian(380, 140))
        return true
    }

    private suspend fun handIn(script: Script, settings: HollowSettings): Boolean {
        if (inventory.count(ANCIENT_REMAINS) > 0) return identify(script)
        val pending = trip ?: ArrayDeque(Collection.entries.filter { it.held() > 0 }).also { trip = it }
        val collection = pending.firstOrNull() ?: return closeHoard(script)
        if (!interfaces.isOpen(HOARD)) return openHoard(script)

        status = "Giving finds to Eep"
        if (!IFSlot(HOARD, COLLECTION_LIST, collection.listEntry).click(1)) {
            script.delay(gaussian(700, 200))
            return true
        }
        script.delay(gaussian(480, 160))
        val before = collection.held()
        if (IFSlot(HOARD, CONTRIBUTE, CONTRIBUTE_BUTTON).click(1)) {
            script.delayUntil(gaussian(3000L, 600L)) { BankPin.isOpen || collection.held() < before }
            if (BankPin.isOpen) {
                status = if (settings.bankPinOrNull() != null) "Entering the bank PIN" else "Enter your bank PIN"
                if (!script.passBankPin(settings)) {
                    status = "The bank PIN was refused"
                    return false
                }
                script.delayUntil(gaussian(3000L, 600L)) { collection.held() < before }
            }
        }
        val taken = before - collection.held()
        if (taken > 0) given += taken else pending.removeFirst()
        script.delay(gaussian(360, 130))
        return true
    }

    private suspend fun openHoard(script: Script): Boolean {
        val eep = findClosestNPC(EEP, RANGE)
        if (eep == null) {
            status = "Eep is out of reach"
            script.log("Finds are ready for Eep but Eep is not within $RANGE tiles; stopping.")
            return false
        }
        status = "Opening Eep's collections"
        if (!eep.interact("Collections")) {
            script.delay(gaussian(1200, 350))
            return true
        }
        script.delayUntil(gaussian(9000L, 1500L)) { interfaces.isOpen(HOARD) }
        if (interfaces.isOpen(HOARD)) script.delay(gaussian(420, 150))
        return true
    }

    private suspend fun closeHoard(script: Script): Boolean {
        if (interfaces.isOpen(HOARD)) {
            IFSlot(HOARD, CLOSE).click(1)
            script.delayUntil(gaussian(2000L, 400L)) { !interfaces.isOpen(HOARD) }
            if (interfaces.isOpen(HOARD)) return true
        }
        trip = null
        handInDue = false
        return true
    }
}

package com.projectx.script.impl.qb.Temporary

import com.projectx.game.chat.MessageType
import com.projectx.script.ConfigurableScript
import com.projectx.script.ScriptDescription
import com.projectx.script.StringConfigItem
import com.projectx.script.State
import com.projectx.script.StateMachineScript
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Chat
import com.projectx.util.gaussian
import world.gregs.voidps.type.Tile

private val flax = Regex(".*flax$", RegexOption.IGNORE_CASE)
private val bowstring = Regex(".*bowstring$", RegexOption.IGNORE_CASE)

private val FLAX_FIELD = Tile.of(2744, 3445, 0)
private val WHEEL_ROOM = Tile.of(2715, 3471, 1)
private val SEERS_BANK = Tile.of(2725, 3489, 0)
private const val NEARBY = 15

@ScriptDescription(
    name = "Flax Stringer",
    version = "1.2.0",
    author = "Billy",
    description = "Picks flax in the Seers' Village field, spins it into bowstrings at the wheel upstairs in the house " +
        "nearby, and banks them at Seers' Village bank."
)
class FlaxString : StateMachineScript<FlaxString>(), ConfigurableScript {
    val bankPin = StringConfigItem("Bank PIN", "Typed for you when the bank asks for it. Leave it empty to type it yourself.", "")

    override fun getStartState() = PickFlax

    /** False when the bank refused the PIN; trying a wrong PIN again locks the bank. */
    suspend fun passBankPin(): Boolean {
        val pin = bankPin.value.trim()
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            if (pin.isNotEmpty()) log("The Bank PIN setting must be four digits; waiting for you to type it.")
            delayUntil(gaussian(90_000L, 10_000L)) { !BankPin.isOpen }
            return true
        }
        if (enterBankPin(pin)) return true
        log("The bank did not accept the Bank PIN setting; stopping.")
        return false
    }
}

object PickFlax: State<FlaxString>() {
    override suspend fun FlaxString.checkNext() = when {
        inventory.isFull -> SpinFlax
        else -> null
    }

    override suspend fun FlaxString.stateLoop() {
        captureSerenSpirit()
        if (localPlayer.isAnimating || localPlayer.isMoving) return
        if (findClosestObject("Flax", NEARBY) == null) {
            webWalk(FLAX_FIELD, 3)
            return
        }
        val before = inventory.count(flax)
        if (interactClosestObject("Flax", "Pick", NEARBY)) {
            waitThenDelayUntil(gaussian(600L, 150L), gaussian(4000L, 800L)) { inventory.count(flax) > before }
        } else {
            delay(600, 200)
        }
    }
}

object SpinFlax: State<FlaxString>() {
    override suspend fun FlaxString.checkNext() = when {
        !inventory.hasItem(flax) && inventory.hasItem(bowstring) -> BankStrings
        !inventory.hasItem(flax) && !inventory.hasItem(bowstring) -> PickFlax
        else -> null
    }

    override suspend fun FlaxString.stateLoop() {
        captureSerenSpirit()
        if (localPlayer.isAnimating || localPlayer.isMoving || MakeX.inProgress) return
        if (!MakeX.isOpen) {
            if (localPlayer.plane != WHEEL_ROOM.plane || findClosestObject("Spinning wheel", NEARBY) == null) {
                webWalk(WHEEL_ROOM, 2)
                return
            }
            if (interactClosestObject("Spinning wheel", "Spin", NEARBY)) delayUntil(gaussian(6000L, 1200L)) { MakeX.isOpen }
            else delay(600, 200)
            return
        }
        if (!makeX({ it.equals("Bowstring", ignoreCase = true) })) {
            println("[Flax Stringer] The spinning wheel offers no Bowstring to make; stopping")
            stop()
        }
    }
}

object BankStrings: State<FlaxString>() {
    override suspend fun FlaxString.checkNext() = when {
        !inventory.hasItem(bowstring) -> PickFlax
        else -> null
    }

    override suspend fun FlaxString.stateLoop() {
        if (localPlayer.isMoving) return
        if (BankPin.isOpen) {
            if (!passBankPin()) stop()
            return
        }
        if (!bankOpen) {
            if (localPlayer.plane != SEERS_BANK.plane || !interactClosestObject("Bank", NEARBY)) {
                webWalk(SEERS_BANK, 4)
                return
            }
            delayUntil(gaussian(6000L, 1200L)) { bankOpen || BankPin.isOpen }
            return
        }

        if (depositBankItem("Bowstring", 0)) delayUntil(gaussian(2500L, 500L)) { !inventory.hasItem(bowstring) }
        else delay(600, 200)
    }

    override fun FlaxString.onStateEvent(event: Event) {
        if (event !is Chat) return
        if (event.messageType == MessageType.FILTERABLE && event.message.contains("Item could not be found"))
            stop()
    }
}

package com.projectx.script.impl.trent.presetbankstandmakex

import com.projectx.game.chat.MessageType
import com.projectx.game.interfaces.actionbarSlots
import com.projectx.script.ScriptDescription
import com.projectx.script.State
import com.projectx.script.StateMachineScript
import com.projectx.script.api.*
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Chat
import com.projectx.util.gaussian

private const val OPEN_TRIES = 4
private const val MAIN_BAR = 1
private const val MAKE_SLOT = 1

@ScriptDescription(
    name = "Preset Bankstand Make-X",
    version = "1.0.2",
    author = "Trent",
    description = "Makes X via bankstanding and presets for whatever using a defined key on actionbar"
)
class PresetBankStandMakeX : StateMachineScript<PresetBankStandMakeX>() {
    override fun getStartState() = Make()
}

class Make: State<PresetBankStandMakeX>() {
    var making = false
    private var tries = 0

    override suspend fun PresetBankStandMakeX.checkNext() = if (making && !hasActiveMakeXProgress) Bank() else null

    override suspend fun PresetBankStandMakeX.stateLoop() {
        if (hasActiveMakeXProgress) return
        if (!makeXOpen) {
            if (tries++ == OPEN_TRIES) {
                log("Make-X won't open, so the backpack has nothing left to make. Stopping.")
                stop()
                return
            }
            // A keybind press can miss, so alternate it with clicking the slot it is bound to.
            if (tries % 2 == 1) actionbarSlots[MAIN_BAR]?.get(MAKE_SLOT)?.click(1) else clickKey('1')
            delayUntil(gaussian(4500, 250_000).toLong()) { makeXOpen }
            return
        }
        continueMakeX()
        delayUntil { interfaces.isOpen(1251) }
        making = true
    }
}

class Bank: State<PresetBankStandMakeX>() {
    var withdrawn = false

    override suspend fun PresetBankStandMakeX.checkNext() = if (withdrawn) Make() else null

    override suspend fun PresetBankStandMakeX.stateLoop() {
        loadLastPresetClosestBank()
        delay(1520, 852)
    }

    // A preset missing only worn gear still withdraws the materials, so a missing item is no reason to stop.
    override fun PresetBankStandMakeX.onStateEvent(event: Event) {
        if (event !is Chat || event.messageType != MessageType.FILTERABLE) return
        if (event.message.contains("Your preset is being withdrawn")) withdrawn = true
        if (event.message.contains("Item could not be found")) log(event.message.substringAfter('>'))
    }
}

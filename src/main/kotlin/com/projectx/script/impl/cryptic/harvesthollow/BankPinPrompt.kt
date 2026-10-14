package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.script.Script
import com.projectx.script.api.BankPin
import com.projectx.script.api.enterBankPin
import com.projectx.script.api.log
import com.projectx.util.gaussian

/** False when the bank refused the PIN from the settings; retrying a wrong PIN locks the bank. */
suspend fun Script.passBankPin(settings: HollowSettings): Boolean {
    val pin = settings.bankPinOrNull()
    if (pin == null) {
        if (settings.bankPin.value.isNotBlank()) log("The Bank PIN setting must be four digits; waiting for you to type it.")
        delayUntil(gaussian(90_000L, 10_000L)) { !BankPin.isOpen }
        return true
    }
    if (enterBankPin(pin)) return true
    log("The bank did not accept the Bank PIN setting; stopping.")
    return false
}

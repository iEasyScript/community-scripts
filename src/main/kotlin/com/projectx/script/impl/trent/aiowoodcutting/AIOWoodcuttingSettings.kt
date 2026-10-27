package com.projectx.script.impl.trent.aiowoodcutting

import com.projectx.script.BooleanConfigItem
import com.projectx.script.ConfigHolder
import com.projectx.script.ConfigSection
import com.projectx.script.EnumConfigItem
import com.projectx.script.InfoDisplayConfigItem
import com.projectx.script.StringConfigItem

enum class WhenFull(private val label: String) {
    BANK("Bank at the nearest bank"),
    TELEPORT_BANK("Teleport to War's Retreat to bank"),
    DROP("Drop the logs"),
    BONFIRE("Burn them on a bonfire");

    override fun toString() = label
}

class AIOWoodcuttingSettings : ConfigHolder {
    val woodcuttingSection = ConfigSection(
        "Woodcutting",
        "Click a tree to choose it: the script cuts that kind of tree around where you clicked, and walks back there after every trip.",
    )
    val whenFull = EnumConfigItem(
        "Full backpack",
        "Bank walks to the nearest bank. Teleport uses the War's Retreat teleport and its bank chest. Both walk back to your " +
            "trees afterwards. Drop drops the logs and carries on. Bonfire lights a fire with one log and adds the rest to it.",
        WhenFull.entries.toTypedArray(),
        WhenFull.BANK,
    )
    val useWoodBox = BooleanConfigItem("Use wood box", "Fills the wood box in your backpack before the backpack counts as full, and empties it into the bank.", true)
    val bankPin = StringConfigItem("Bank PIN", "Typed for you when the bank asks. Leave it empty to type it yourself.", "")

    val statusSection = ConfigSection("Status", "Live readout.")
    val doing = InfoDisplayConfigItem("Doing", "", "Not started")
}

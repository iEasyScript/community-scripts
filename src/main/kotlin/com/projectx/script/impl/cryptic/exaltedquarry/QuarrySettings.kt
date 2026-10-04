package com.projectx.script.impl.cryptic.exaltedquarry

import com.projectx.script.BooleanConfigItem
import com.projectx.script.ConfigHolder
import com.projectx.script.ConfigSection
import com.projectx.script.InfoDisplayConfigItem
import com.projectx.script.EnumConfigItem
import com.projectx.script.IntConfigItem

/** Large deposits want more Mining and Crafting than the rest, so which to work is the player's call. */
enum class DepositChoice {
    EVERYTHING,
    LARGE_ONLY,
    REGULAR_ONLY,
}

class QuarrySettings : ConfigHolder {

    val miningSection = ConfigSection("Mining", "Which rocks to work and how hard to keep them going.")
    val deposits = EnumConfigItem(
        "Deposits to mine",
        "Large deposits need a higher Mining and Crafting level than the rest. Regular covers both " +
            "Essence deposits and Essence veins.",
        DepositChoice.entries.toTypedArray(),
        DepositChoice.EVERYTHING,
    )
    val preferCracked = BooleanConfigItem(
        "Prefer cracked deposits",
        "A cracked deposit yields more per hour, so it is worked ahead of the plain one beside it.",
        true,
    )
    val staminaBelow = IntConfigItem(
        "Refresh stamina below",
        "Re-clicks the rock to top the mining stamina bar back up, on the bar's own 0-255 scale. 0 turns it off.",
        110, 0, 255,
    )
    val floorStep = IntConfigItem(
        "Height between floors",
        "The quarry is one plane of stacked ledges whose collision map has no cliffs, so ground height " +
            "decides which floor a rock is on. The ledges measure 1600 apart. The log names the floor of " +
            "every rock on start, so change this only if those numbers look wrong.",
        1600, 200, 6000,
    )
    val useBrokenLadders = BooleanConfigItem(
        "Use broken ladders",
        "Broken ladders are traversed rather than climbed and may want an Agility level.",
        false,
    )

    val processingSection = ConfigSection("Processing", "What happens once the pack is full.")
    val exaltFirst = BooleanConfigItem(
        "Exalt at the colossus",
        "Mines the Exalted colossus to turn a full pack into its exalted form before processing it.",
        true,
    )
    val processEssence = BooleanConfigItem("Process at the processor", "", true)
    val depositProduct = BooleanConfigItem(
        "Bank the essence",
        "Deposits the finished essence so the next trip has room. The box's own option empties the whole " +
            "pack, so leave this off until you are happy for everything in it to be banked.",
        false,
    )

    val statusSection = ConfigSection("Status", "")
    val stageInfo = InfoDisplayConfigItem("Doing", "", "-")
    val floorInfo = InfoDisplayConfigItem("Floor", "", "-")
    val rockInfo = InfoDisplayConfigItem("Rock", "", "-")
    val staminaInfo = InfoDisplayConfigItem("Stamina", "", "-")
    val packInfo = InfoDisplayConfigItem("Pack (raw / exalted)", "", "-")
    val tripInfo = InfoDisplayConfigItem("Trips / spirits", "", "0 / 0")
}

package com.projectx.script.impl.cryptic.harvesthollow

import com.projectx.script.BooleanConfigItem
import com.projectx.script.ConfigHolder
import com.projectx.script.ConfigItem
import com.projectx.script.EnumConfigItem
import com.projectx.script.InfoDisplayConfigItem
import com.projectx.script.IntConfigItem
import com.projectx.script.StringConfigItem

class HollowSettings : ConfigHolder {
    val activity = EnumConfigItem("Activity", "Which part of Harvest Hollow to run.", Activity.entries.toTypedArray(), Activity.BONFIRE)
    val logs = StringConfigItem(
        "Logs",
        "The logs to burn, named as in the bank. Leave empty to burn whichever logs you have most of.",
        "",
    )
    val burnedInfo = InfoDisplayConfigItem("Logs burned", "", "0")
    val smashTarget = IntConfigItem(
        "Stop at smashes",
        "Stops once the mask says this many pumpkins are smashed; 0 keeps smashing. The mask needs 250 to change style.",
        250, 0, 100000,
    )
    val smashedInfo = InfoDisplayConfigItem("Pumpkins smashed", "Read from the Rotten pumpkin mask.", "-")
    val handInLoot = BooleanConfigItem("Hand loot to Peckish", "Trades the stacked valuables and heirlooms to Peckish for the event's rewards.", true)
    val handInAt = IntConfigItem("Hand in at", "How much loot to carry before taking it to Peckish.", 100, 1, 100000)
    val followSprite = BooleanConfigItem("Follow the time sprite", "Digs the remains a time sprite sits on, moving when it does.", true)
    val identifyAt = IntConfigItem("Identify remains at", "Identifies the Ancient remains once this many are carried.", 10, 1, 100000)

    private val ownedBy: Map<Activity, Set<ConfigItem<*>>> = mapOf(
        Activity.BONFIRE to setOf(logs, burnedInfo),
        Activity.SMASHING_PUMPKIN to setOf(smashTarget, smashedInfo),
        Activity.THIEVING to setOf(handInLoot, handInAt),
        Activity.ARCHAEOLOGY to setOf(followSprite, identifyAt),
    )

    /** An activity's own settings show only while it is the one selected; shared ones always show. */
    fun isVisible(item: ConfigItem<*>): Boolean {
        val owner = ownedBy.entries.firstOrNull { item in it.value }?.key ?: return true
        return owner == activity.value
    }
}

package com.projectx.script.impl.cryptic.lividfarm

import com.projectx.script.BooleanConfigItem
import com.projectx.script.ConfigHolder
import com.projectx.script.ConfigSection
import com.projectx.script.InfoDisplayConfigItem
import com.projectx.script.IntConfigItem

class LividSettings : ConfigHolder {

    val tasksSection = ConfigSection("Tasks", "Which of the farm's jobs to do.")
    val curePlants = BooleanConfigItem("Cure diseased plants", "", true)
    val fertilisePatches = BooleanConfigItem("Fertilise empty patches", "", true)
    val fixFences = BooleanConfigItem(
        "Fix broken fences",
        "Takes lumber from the log pile and turns it into fenceposts when there are none left.",
        true,
    )
    val encouragePauline = BooleanConfigItem(
        "Encourage Pauline",
        "She is left alone for a while whenever she says she is currently fine.",
        true,
    )
    val handleProduce = BooleanConfigItem(
        "Bunch and trade produce",
        "Takes plants from a full pile, bunches them in fives and trades the bunches at the wagon.",
        true,
    )
    val helpMurkyPat = BooleanConfigItem(
        "Vengeance Murky Pat",
        "His event is worth more than the routine jobs, so it is answered first whenever it is running.",
        true,
    )

    val suppliesSection = ConfigSection("Supplies", "What the run needs to keep going.")
    val minimumRunes = IntConfigItem(
        "Stop below this many runes",
        "Counted for nature and astral separately. Vengeance Other costs both.",
        100, 0, 1000,
    )

    val statusSection = ConfigSection("Status", "")
    val stageInfo = InfoDisplayConfigItem("Doing", "", "-")
    val runesInfo = InfoDisplayConfigItem("Nature / astral", "", "-")
    val carryingInfo = InfoDisplayConfigItem("Plants / bunches", "", "-")
    val tradedInfo = InfoDisplayConfigItem("Bunches traded", "", "0")
    val jobsInfo = InfoDisplayConfigItem("Cured / fertilised / fenced", "", "0 / 0 / 0")
}

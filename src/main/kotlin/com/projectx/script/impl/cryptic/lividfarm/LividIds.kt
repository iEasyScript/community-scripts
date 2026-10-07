package com.projectx.script.impl.cryptic.lividfarm

/** The farm's contents, carried over from the Lua script this was converted from. */
object LividIds {

    const val LOG_PILE = 40444
    const val BROKEN_FENCE = 40431
    const val PRODUCE_PILE = 40438
    const val TRADE_WAGON = 40443
    const val EMPTY_PATCH = 40446

    const val LUNAR_LUMBER = 20702
    const val LUNAR_FENCEPOST = 20703
    const val LIVID_PLANT = 20704
    const val LIVID_PLANT_BUNCH = 20705

    const val NATURE_RUNE = 561
    const val ASTRAL_RUNE = 9075

    /** Any rune pouch, which holds runes the backpack cannot see. */
    val RUNE_POUCH = Regex("(?i).*rune pouch.*")

    const val PAULINE = 13621
    const val MURKY_PAT = 13625

    /**
     * The four diseased plants and the cure each one wants.
     *
     * Every plant opens the same interface and the cure is a different row of it, so the plant's id is
     * what decides which row to press - there is nothing on screen that says which is right.
     */
    const val CURE_INTERFACE = 1081
    val CURES: Map<Int, Int> = mapOf(
        40452 to 0,
        40453 to 3,
        40454 to 6,
        40455 to 9,
    )

    /** Scanned by name rather than id, because only the name tells a full pile from an empty one. */
    const val FULL_PRODUCE_PILE = "Produce pile (full)"
    const val EMPTY_PATCH_NAME = "Empty patch"
    const val BROKEN_FENCE_NAME = "Broken fence"
    const val DISEASED_PLANT_NAME = "Diseased livid"
    const val PAULINE_NAME = "Drained Pauline Polaris"

    /**
     * Every option this script clicks, read off the cache rather than guessed.
     *
     * The log pile offering both Take-1 and Take-5 is why this matters: falling back to "whatever is
     * first" there collects a single log and makes a single fencepost.
     */
    const val TAKE_LUMBER = "Take-5"
    const val CURE_PLANT = "Cure-plant"
    const val FERTILISE = "Fertilise"
    const val FIX_FENCE = "Fix"
    const val TAKE_PRODUCE = "Take"
    const val DEPOSIT_PRODUCE = "Deposit"
    const val ENCOURAGE = "Encourage"

    /** Murky Pat's option while his event is running. Matched loosely, as the Lua matches his action. */
    const val VENGEANCE = "vengeance"

    /** She says this when she does not want encouraging; it is the only reliable "not now" she gives. */
    const val PAULINE_FINE = "currently fine"

    /**
     * The lines she accepts. Which of them is offered, and in what order, changes every round, so the
     * dialogue is answered by looking the wording up rather than by pressing a fixed row.
     */
    val ENCOURAGEMENTS = listOf(
        "Come on, you're doing so well.",
        "Keep going! We can do this.",
        "Look at all the produce being made.",
        "Lokar will really appreciate this.",
        "You're doing a fantastic job.",
        "Extraordinary!",
    )
}

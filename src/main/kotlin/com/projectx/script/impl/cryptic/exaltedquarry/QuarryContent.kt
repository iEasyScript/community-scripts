package com.projectx.script.impl.cryptic.exaltedquarry

/**
 * What the quarry is made of.
 *
 * Every id here was read off the live scene rather than the cache: the gamevals carry nothing for this
 * release, so a name lookup would have come back empty.
 */
class Rock(val id: Int, val label: String, val tier: Int, val cracked: Boolean = false) {
    val large: Boolean get() = tier == LARGE_TIER

    companion object {
        const val LARGE_TIER = 3
    }
}

object QuarryContent {

    /**
     * The quarry is one plane of stacked ledges, and its collision map has no cliff data, so every rock
     * routes as reachable from every other. Ground height is what separates them, and the ledges sit on a
     * regular step: the colossus, processor and bottom ladder all stand at [FLOOR_BASE], and each ledge
     * above is a further [FLOOR_STEP]. Quantising to that step gives a floor number that holds wherever
     * on a sloped ledge the player happens to stand.
     */
    const val FLOOR_BASE = 11205
    const val FLOOR_STEP = 1600

    const val COLOSSUS = 140913
    const val PROCESSOR = 140911
    const val DEPOSIT_BOX = 140912

    val ladders = intArrayOf(140909, 140910)
    val brokenLadders = intArrayOf(140907, 140908)

    /** Smallest first. Mining the colossus turns each of these into its exalted twin below. */
    val rawEssence = intArrayOf(63926, 63927, 63928)
    val exaltedEssence = intArrayOf(63929, 63930, 63931)

    /** The processor's output. Each input tier has its own recipe id, so these are matched by name. */
    const val PURE_ESSENCE = "Pure essence"
    const val EXALTED_ESSENCE = "Exalted essence"

    /**
     * Each deposit is one transforming loc that moves between three states - plain, cracked and depleted.
     * Cracked yields more per hour, so it is worked first; depleted is simply absent from this list, and
     * carries no options at all in any case, so it can never be clicked.
     */
    val rocks: List<Rock> = listOf(
        Rock(140932, "Large essence deposit (cracked)", 3, cracked = true),
        Rock(140931, "Large essence deposit", 3),
        Rock(140924, "Essence deposit (cracked)", 2, cracked = true),
        Rock(140923, "Essence deposit", 2),
        Rock(140916, "Essence vein (cracked)", 1, cracked = true),
        Rock(140915, "Essence vein", 1),
    )

    fun rockOf(id: Int): Rock? = rocks.firstOrNull { it.id == id }
}

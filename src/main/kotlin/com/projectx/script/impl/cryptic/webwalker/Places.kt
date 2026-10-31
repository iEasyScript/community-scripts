package com.projectx.script.impl.cryptic.webwalker

import com.projectx.script.api.Banks
import com.projectx.script.api.Lodestone
import com.projectx.webwalker.WebLink
import com.projectx.webwalker.WebLinks
import world.gregs.voidps.type.Tile
import java.io.File

enum class PlaceGroup(val label: String) {
    EVERYWHERE("Everywhere"),
    LODESTONES("Lodestones"),
    BANKS("Banks"),
    LINKS("Transport"),
    SAVED("Saved"),
    TILE("Tile"),
}

data class Place(val group: PlaceGroup, val name: String, val kind: String, val tile: Tile) {
    val coords: String get() = "${tile.x}, ${tile.y}, ${tile.plane}"
}

object Places {
    private val SPOTS = File(System.getProperty("user.home"), ".projectx/webwalk-spots.txt")

    fun all(): List<Place> = lodestones() + banks() + links() + saved()

    fun tile(x: Int, y: Int, plane: Int) = Place(PlaceGroup.TILE, "Tile $x, $y, $plane", "Tile", Tile.of(x, y, plane))

    private fun lodestones() = Lodestone.entries.map { Place(PlaceGroup.LODESTONES, title(it.name), "Lodestone", it.tile) }

    private fun banks() = Banks.entries.map { Place(PlaceGroup.BANKS, it.name, "Bank", it.tile) }

    // Walking to the far side of a link is what makes a route take it.
    private fun links() = WebLinks.all.filter { !it.isGlobal }.map(::farSide).distinctBy { it.tile }.sortedBy { it.name }

    private fun farSide(link: WebLink) = Place(
        PlaceGroup.LINKS,
        "${link.action.ifBlank { title(link.kind.name) }} from ${link.from.centreX}, ${link.from.centreY}, ${link.from.plane}",
        title(link.kind.name),
        Tile.of(link.to.centreX, link.to.centreY, link.to.plane),
    )

    fun saved(): List<Place> = runCatching { SPOTS.readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 4)
        val (x, y, plane) = parts.take(3).map { it.toIntOrNull() ?: return@mapNotNull null }.takeIf { it.size == 3 } ?: return@mapNotNull null
        Place(PlaceGroup.SAVED, parts.getOrNull(3) ?: "Spot $x, $y, $plane", "Saved", Tile.of(x, y, plane))
    }

    fun writeSaved(spots: List<Place>) {
        SPOTS.parentFile.mkdirs()
        SPOTS.writeText(spots.joinToString("") { "${it.tile.x} ${it.tile.y} ${it.tile.plane} ${it.name}\n" })
    }

    private fun title(name: String) = name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
}

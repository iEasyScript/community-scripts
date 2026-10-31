package com.projectx.script.impl.cryptic.webwalker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.projectx.script.BooleanConfigItem
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigurableScript
import com.projectx.script.IntConfigItem
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.findWebPath
import com.projectx.script.api.localPlayer
import com.projectx.script.api.log
import com.projectx.script.api.varps
import com.projectx.script.api.webWalk
import com.projectx.script.event.Event
import com.projectx.script.event.impl.Varp
import com.projectx.util.gaussian
import com.projectx.webwalker.WebPath
import com.projectx.webwalker.WebWalkResult
import com.projectx.webwalker.WebWalker as Walker
import world.gregs.voidps.gameval.Gameval
import world.gregs.voidps.type.Tile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.max

@ScriptDescription(
    name = "Web Walker",
    version = "1.0.0",
    author = "Cryptic",
    description = "Takes you anywhere: pick a lodestone, bank, staircase, shortcut, boat, saved spot, any tile or your world " +
        "map marker and it walks the quickest way there, teleports, doors and ladders included. It can also follow your " +
        "map marker, plan a route without walking it, and tour places one after another.",
    category = ScriptCategory.OTHER,
)
class WebWalker : Script(), ConfigurableScript, ComposePanel {
    private val teleports = BooleanConfigItem("Teleports", "Let a route begin with a lodestone, spell or teleport item.", true)
    private val itemTeleports = BooleanConfigItem("Item teleports", "Let teleports spend carried items and the Passage of the Abyss.", true)
    private val movementAbilities = BooleanConfigItem("Dive and Surge", "Use Dive and Surge along straight stretches.", true)
    private val accelerationPotion = BooleanConfigItem("Powerburst of acceleration", "Drink one where a run of Dives and Surges saves time.", true)
    private val hostedPlanner = BooleanConfigItem("Hosted planner", "Plan on the hosted planner first; off plans with the local planner only.", true)
    private val arriveDistance = IntConfigItem("Arrive within", "Tiles from the destination that count as arrived.", 2, 0, 10)
    private val stopTourOnFailure = BooleanConfigItem("Stop tour on failure", "End a tour at the first walk that doesn't arrive.", false)
    private val followMarker = BooleanConfigItem("Follow map marker", "Walk to the world map marker each time you place a new one.", false)

    val view = TesterView()
    private val commands = ConcurrentLinkedQueue<Command>()

    @Volatile
    private var tour: Tour? = null
    private var leg = 0
    private val defaults = Defaults()

    override val panelTitle = "Web Walker"

    @Composable
    override fun Panel() = WebWalkPanel(view, commands::add, ::startTour, ::endTour)

    override fun onEvent(event: Event) {
        if (event !is Varp || event.id != MARKER || event.newValue == event.oldValue) return
        val tile = unpack(event.newValue) ?: return
        if (followMarker.value) commands.add(Command.Go(markerPlace(tile), planOnly = false))
    }

    override fun onStart() {
        super.onStart()
        view.places = Places.all()
    }

    override fun onStop() {
        super.onStop()
        defaults.restore()
    }

    override suspend fun loop() {
        val me = localPlayer.tile
        view.here = "${me.x}, ${me.y}, ${me.plane}"
        val marker = marker()
        view.marker = marker?.let { "${it.x}, ${it.y}, ${it.plane}" }
        when (val command = commands.poll()) {
            is Command.Go -> go(command.place, command.planOnly)
            is Command.GoToMarker -> marker?.let { go(markerPlace(it), command.planOnly) } ?: run { view.status = "No marker on the world map" }
            Command.SaveSpot -> saveSpot(me)
            is Command.Forget -> forget(command.place)
            null -> if (!takeTourLeg(me)) delay(gaussian(250, 2_500))
        }
    }

    private fun startTour(places: List<Place>, random: Boolean, title: String) {
        if (places.isEmpty()) return
        leg = 0
        tour = Tour(places, random, title)
        view.tour = "$title: starting"
    }

    private fun endTour() {
        tour = null
        view.tour = null
    }

    private suspend fun takeTourLeg(me: Tile): Boolean {
        val current = tour ?: return false
        val place = if (current.random) {
            current.places.filter { flatDistance(it.tile, me) > NEAR }.randomOrNull() ?: current.places.random()
        } else {
            current.places.getOrNull(leg) ?: run { endTour(); return false }
        }
        leg++
        view.tour = if (current.random) "${current.title}: leg $leg" else "${current.title}: $leg of ${current.places.size}"
        val result = go(place, planOnly = false)
        if (!result.isSuccess && stopTourOnFailure.value) {
            endTour()
            return true
        }
        if (tour != null) delay(gaussian(2500, 400_000))
        return true
    }

    private suspend fun go(place: Place, planOnly: Boolean): WebWalkResult {
        Walker.useItemTeleports = itemTeleports.value
        Walker.useMovementAbilities = movementAbilities.value
        Walker.useAccelerationPotion = accelerationPotion.value
        Walker.useHostedPlanner = hostedPlanner.value
        val from = localPlayer.tile
        val verb = if (planOnly) "Planning a route to" else "Walking to"
        view.status = "$verb ${place.name}"
        view.target = place
        log("$verb ${place.name}: ${from.x},${from.y},${from.plane} -> ${place.coords}, ${settingsLine()}")

        val started = System.currentTimeMillis()
        val result = if (planOnly) findWebPath(place.tile, arriveDistance.value) else webWalk(place.tile, arriveDistance.value, teleports.value)
        val seconds = (System.currentTimeMillis() - started) / 1000.0

        val route = describe(result.path)
        val now = localPlayer.tile
        log("$result after ${"%.1f".format(seconds)}s, now at ${now.x},${now.y},${now.plane}")
        route.forEach { log("  $it") }
        view.status = "$result"
        view.route = route
        view.history = (listOf(WalkRecord(place.name, result.status.name, seconds, result.path?.linkCount ?: 0, result.isSuccess, planOnly)) + view.history).take(HISTORY)
        record(place, planOnly, from, now, result, seconds)
        return result
    }

    private fun describe(path: WebPath?): List<String> {
        if (path == null) return emptyList()
        val links = (0..path.lastIndex).mapNotNull { index -> path.linkAt(index)?.let { "step $index: $it" } }
        return listOf("${path.size} steps, ${path.doorCount} doors, ${links.size} links") + links
    }

    private fun saveSpot(me: Tile) {
        val saved = Places.saved()
        if (saved.any { it.tile == me }) return
        Places.writeSaved(saved + Place(PlaceGroup.SAVED, "Spot ${me.x}, ${me.y}, ${me.plane}", "Saved", me))
        view.places = Places.all()
        view.status = "Saved ${me.x}, ${me.y}, ${me.plane}"
    }

    private fun forget(place: Place) {
        Places.writeSaved(Places.saved().filter { it.tile != place.tile })
        view.places = Places.all()
    }

    private fun settingsLine() =
        "teleports ${teleports.value}, item teleports ${itemTeleports.value}, Dive/Surge ${movementAbilities.value}, " +
            "acceleration ${accelerationPotion.value}, hosted ${hostedPlanner.value}, arrive ${arriveDistance.value}"

    private fun record(place: Place, planOnly: Boolean, from: Tile, to: Tile, result: WebWalkResult, seconds: Double) {
        val fresh = !RESULTS.isFile
        RESULTS.parentFile.mkdirs()
        val row = listOf(
            LocalDateTime.now().format(STAMP), if (planOnly) "plan" else "walk", place.group.label, place.name, place.coords,
            "${from.x} ${from.y} ${from.plane}", "${to.x} ${to.y} ${to.plane}", result.status.name, result.message,
            "%.1f".format(seconds), "${result.path?.size ?: 0}", "${result.path?.doorCount ?: 0}", "${result.path?.linkCount ?: 0}",
            result.lodestone?.name ?: "", settingsLine(),
        ).joinToString(",") { "\"" + it.replace("\"", "'") + "\"" }
        RESULTS.appendText((if (fresh) HEADER + "\n" else "") + row + "\n")
    }


    private fun marker(): Tile? = unpack(varps.getVar(MARKER))

    private fun markerPlace(tile: Tile) = Place(PlaceGroup.TILE, "Map marker ${tile.x}, ${tile.y}, ${tile.plane}", "Marker", tile)

    private fun unpack(coord: Int): Tile? =
        if (coord <= 0) null else Tile.of((coord ushr 14) and 0x3FFF, coord and 0x3FFF, (coord ushr 28) and 0x3)

    private fun flatDistance(a: Tile, b: Tile) = if (a.plane != b.plane) Int.MAX_VALUE else max(abs(a.x - b.x), abs(a.y - b.y))

    private class Tour(val places: List<Place>, val random: Boolean, val title: String)

    private class Defaults(
        val itemTeleports: Boolean = Walker.useItemTeleports,
        val movementAbilities: Boolean = Walker.useMovementAbilities,
        val accelerationPotion: Boolean = Walker.useAccelerationPotion,
        val hostedPlanner: Boolean = Walker.useHostedPlanner,
    ) {
        fun restore() {
            Walker.useItemTeleports = itemTeleports
            Walker.useMovementAbilities = movementAbilities
            Walker.useAccelerationPotion = accelerationPotion
            Walker.useHostedPlanner = hostedPlanner
        }
    }

    private companion object {
        const val NEAR = 20
        const val HISTORY = 30
        val RESULTS = File(System.getProperty("user.home"), ".projectx/webwalk-results.csv")
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        // The world map marker's coord, kept on the server under an old name that no longer says what it holds.
        val MARKER = Gameval.requireId(Gameval.VAR_PLAYER, "playerdesign2_col")
        const val HEADER = "time,mode,group,place,destination,from,ended at,status,message,seconds,steps,doors,links,lodestone,settings"
    }
}

sealed interface Command {
    data class Go(val place: Place, val planOnly: Boolean) : Command
    data class GoToMarker(val planOnly: Boolean) : Command
    data object SaveSpot : Command
    data class Forget(val place: Place) : Command
}

data class WalkRecord(val place: String, val status: String, val seconds: Double, val links: Int, val ok: Boolean, val planOnly: Boolean)

class TesterView {
    var places by mutableStateOf(emptyList<Place>())
    var status by mutableStateOf("Pick a place to walk to")
    var target by mutableStateOf<Place?>(null)
    var tour by mutableStateOf<String?>(null)
    var here by mutableStateOf("")
    var marker by mutableStateOf<String?>(null)
    var route by mutableStateOf(emptyList<String>())
    var history by mutableStateOf(emptyList<WalkRecord>())
}

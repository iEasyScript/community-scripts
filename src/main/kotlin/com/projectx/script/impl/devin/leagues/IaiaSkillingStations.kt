package com.projectx.script.impl.devin.leagues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.projectx.game.interfaces.IFSlot
import com.projectx.game.interfaces.actionbarSlots
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigurableScript
import com.projectx.script.EnumConfigItem
import com.projectx.script.IntConfigItem
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.SkillTracker
import com.projectx.script.api.SkillTrackerReadout
import com.projectx.script.api.SkillTrackerSnapshot
import com.projectx.script.api.continueDialogueContaining
import com.projectx.script.api.dialogueOptionVisible
import com.projectx.script.api.findClosestNPC
import com.projectx.script.api.findClosestObject
import com.projectx.script.api.interfaces
import com.projectx.script.api.varps
import com.projectx.script.api.waitUntilNotMoving
import com.projectx.ui.compose.components.DataTable
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.Readout
import com.projectx.ui.compose.components.TableColumn
import com.projectx.ui.compose.theme.Palette
import com.projectx.util.gaussian
import org.projectx.core.game.combat.ActionBarModel
import world.gregs.voidps.gameval.Gameval

private const val CHOICE_DIALOG = 1188
private const val CHOICE_BORDER = 3
private const val MODAL_CHOICE = 720
private const val ANACHRONIA_OPTION = "Anachronia base camp"
private const val STATION_RANGE = 24
private const val MAX_UNPRODUCTIVE_CASTS = 2
private const val MAX_UNREADABLE_OFFERS = 3
private const val DRAIN_SAMPLE_LIMIT = 8
private const val MIN_DRAIN_SAMPLES = 3
private const val RECLICK_DRAIN_TICKS = 2

/** Bootstrap pace only: the first [MIN_DRAIN_SAMPLES] timed drain ticks replace it with the station's real cadence. */
private const val UNCALIBRATED_DRAIN_MS = 4_500L

private val OFFER = Regex("""([\d,]+)\s*x\s*(.+?)\s+and store\s+([\d,]+)\s*xp""", RegexOption.IGNORE_CASE)
private val TAGS = Regex("<[^>]*>")

private data class ContributeOffer(val resource: String, val amount: Int, val xp: Int)

private data class StationRow(val name: String, val workers: Int, val resource: String, val storedXp: Int)

private data class IaiaBoard(
    val station: String,
    val stocked: Boolean,
    val advanceTimeCharges: String,
    val stations: List<StationRow>,
)

private val STATION_COLUMNS = listOf(
    TableColumn("Station", 1.3f),
    TableColumn("Workers", 0.7f, mono = true),
    TableColumn("Resource", 1.2f),
    TableColumn("Stored XP", 1f, mono = true),
)

@ScriptDescription(
    name = "Dream of Iaia Stations",
    version = "1.1.0",
    author = "Devin",
    description = "Runs a Dream of Iaia station at the Anachronia base camp: keeps its stored XP draining, contributes resources, and casts Advance Time to replenish them.",
    category = ScriptCategory.OTHER
)
class IaiaSkillingStations : Script(), ConfigurableScript, ComposePanel {

    private val selectedStation = EnumConfigItem(
        name = "Station",
        description = "Which Iaia station to train at. Workers must already be gathering that station's resource.",
        enumValues = IaiaStation.entries.toTypedArray(),
        initialValue = IaiaStation.APOTHECARY
    )

    private val topUpBelow = IntConfigItem(
        name = "Top up below",
        description = "Contribute resources once the station's stored XP drops below this.",
        initialValue = 275_000,
        min = 0
    )

    @Volatile
    private var trackedStation: IaiaStation? = null
    @Volatile
    private var tracker = SkillTracker(IaiaStation.APOTHECARY.skill)
    private val progress by live(SkillTrackerSnapshot()) { tracker.snapshot() }
    private val board by live<IaiaBoard?>(null, 500) { trackedStation?.let(::readBoard) }
    private var contributions by mutableStateOf(0)
    private var advanceTimeCasts by mutableStateOf(0)
    private var unproductiveCasts = 0
    private var unreadableOffers = 0
    private var lastStoredXp = -1
    private var lastDrainAt = 0L
    private var lastChangeWasDrain = false
    private val drainIntervals = ArrayDeque<Long>()
    private var stallWindowMs = 0L
    private var stopReason by mutableStateOf<String?>(null)
    private var lastReport = ""

    override fun onStart() = resetStats(selectedStation.value)

    override suspend fun loop() {
        if (stopReason != null) return
        val station = selectedStation.value
        if (station != trackedStation) resetStats(station)

        val unresolved = station.unresolvedGamevals
        if (unresolved.isNotEmpty())
            return abort("Cache lookup failed for ${station.stationName}: ${unresolved.joinToString()}")

        val storedXp = varps.getVarBit(station.storedXpVarbit)
        if (storedXp != lastStoredXp) onStoredXpChanged(storedXp)

        if (storedXp <= topUpBelow.value) restock(station) else work(station, storedXp)
    }

    private fun onStoredXpChanged(storedXp: Int) {
        val now = System.currentTimeMillis()
        val drained = lastStoredXp >= 0 && storedXp < lastStoredXp
        if (drained) {
            if (lastChangeWasDrain) {
                drainIntervals.addLast(now - lastDrainAt)
                if (drainIntervals.size > DRAIN_SAMPLE_LIMIT) drainIntervals.removeFirst()
            }
            stallWindowMs = nextStallWindow()
        } else stallWindowMs = 0L
        lastChangeWasDrain = drained
        lastDrainAt = now
        lastStoredXp = storedXp
    }

    private fun drainIntervalMs(): Long? =
        if (drainIntervals.size < MIN_DRAIN_SAMPLES) null else drainIntervals.sorted()[drainIntervals.size / 2]

    private fun nextStallWindow(): Long {
        val drain = drainIntervalMs() ?: return gaussian(UNCALIBRATED_DRAIN_MS, UNCALIBRATED_DRAIN_MS / 5)
        val mean = drain * RECLICK_DRAIN_TICKS
        return gaussian(mean, mean / 5)
    }

    private suspend fun work(station: IaiaStation, storedXp: Int) {
        val idleLeftMs = stallWindowMs - (System.currentTimeMillis() - lastDrainAt)
        if (idleLeftMs > 0) {
            lastReport = ""
            return delay(idleLeftMs.coerceAtMost(2400L).toInt(), 300)
        }
        val loc = findClosestObject(STATION_RANGE) { it.id == station.locId }
            ?: return idle("No ${station.stationName} within $STATION_RANGE tiles")
        if (!loc.hasOption(station.skillOption))
            return idle("${station.stationName} is not stocked - waiting on a contribution")
        waitUntilNotMoving()
        if (!loc.interact(station.skillOption))
            return idle("${station.stationName} would not accept \"${station.skillOption}\"")
        val drainWait = (drainIntervalMs() ?: UNCALIBRATED_DRAIN_MS) * (RECLICK_DRAIN_TICKS + 1)
        delayUntil(gaussian(drainWait, drainWait / 5), pollingDelayMillis = 400) {
            varps.getVarBit(station.storedXpVarbit) != storedXp
        }
        delay(740, 260)
    }

    private suspend fun restock(station: IaiaStation) {
        if (!interfaces.isOpen(CHOICE_DIALOG)) {
            if (varps.getVar(station.resourceVarp) <= 0) {
                report("No ${station.resourceLabel} stockpiled - replenishing before contributing")
                return advanceTime(station)
            }
            val worker = findClosestNPC(STATION_RANGE) { it.id == station.workerNpcId }
                ?: return idle("No ${station.stationName} worker within $STATION_RANGE tiles")
            waitUntilNotMoving()
            if (!worker.interact("Contribute all resources"))
                return idle("${worker.name()} would not accept \"Contribute all resources\"")
            delayUntil(gaussian(6400L, 1700L)) { interfaces.isOpen(CHOICE_DIALOG) }
            if (!interfaces.isOpen(CHOICE_DIALOG)) return advanceTime(station)
        }

        val dialog = choiceDialogText()
        val offer = OFFER.find(dialog)?.let {
            ContributeOffer(it.groupValues[2].trim(), amount(it.groupValues[1]), amount(it.groupValues[3]))
        } ?: run {
            decline()
            if (++unreadableOffers >= MAX_UNREADABLE_OFFERS)
                return abort("Contribute dialog unreadable $unreadableOffers times running: \"$dialog\"")
            return idle("Could not read the ${station.stationName} contribute offer - retrying")
        }
        unreadableOffers = 0
        if (!offer.matches(station)) {
            decline()
            return abort(
                "${station.stationName} offered \"${offer.resource}\" but expects ${station.resourceLabel}" +
                    " - the workers are gathering the wrong resource"
            )
        }
        if (offer.xp <= 0) {
            decline()
            return advanceTime(station)
        }

        val before = varps.getVarBit(station.storedXpVarbit)
        if (!continueDialogueContaining("Yes")) return idle("Contribute dialog has no confirm option")
        delayUntil(gaussian(5600L, 1500L)) {
            !interfaces.isOpen(CHOICE_DIALOG) && varps.getVarBit(station.storedXpVarbit) > before
        }
        if (varps.getVarBit(station.storedXpVarbit) > before) {
            contributions++
            unproductiveCasts = 0
        }
        delay(780, 290)
    }

    private suspend fun advanceTime(station: IaiaStation) {
        val before = resourceStockpile()
        if (!interfaces.isOpen(MODAL_CHOICE)) {
            val slot = advanceTimeSlot()
                ?: return abort("Advance Time is not on an active action bar - put the spell on a bar and restart")
            if (!slot.click(1)) return idle("Could not activate Advance Time")
            advanceTimeCasts++
            delayUntil(gaussian(7200L, 1900L)) { dialogueOptionVisible(ANACHRONIA_OPTION) }
        }
        if (!dialogueOptionVisible(ANACHRONIA_OPTION))
            return unproductive(station, "Advance Time did not offer \"$ANACHRONIA_OPTION\"")
        if (!continueDialogueContaining(ANACHRONIA_OPTION))
            return unproductive(station, "Could not pick \"$ANACHRONIA_OPTION\"")
        delayUntil(gaussian(8200L, 2100L)) { !interfaces.isOpen(MODAL_CHOICE) }
        delayUntil(gaussian(5200L, 1400L)) { resourceStockpile() != before }

        val after = resourceStockpile()
        val gained = after.getValue(station) - before.getValue(station)
        val wrongGains = IaiaStation.entries.filter { it != station && after.getValue(it) > before.getValue(it) }
        if (gained <= 0 && wrongGains.isNotEmpty())
            return abort(
                "Advance Time restored ${wrongGains.joinToString { it.resourceLabel }} but no" +
                    " ${station.resourceLabel} - the workers are gathering the wrong resource"
            )
        if (gained <= 0)
            return unproductive(station, "Advance Time gained no ${station.resourceLabel}")
        unproductiveCasts = 0
        delay(920, 340)
    }

    private suspend fun unproductive(station: IaiaStation, reason: String) {
        if (++unproductiveCasts >= MAX_UNPRODUCTIVE_CASTS)
            return abort("$reason ($unproductiveCasts casts, no ${station.resourceLabel} contributed)")
        idle(reason)
    }

    private fun ContributeOffer.matches(station: IaiaStation): Boolean {
        val named = resource.lowercase()
        if (station.resourceNames.none { named.contains(it) }) return false
        return IaiaStation.entries.none { other ->
            other != station && other.resourceNames.any { named.contains(it) }
        }
    }

    private fun decline() {
        if (!continueDialogueContaining("No")) println("[Dream of Iaia] Contribute dialog has no decline option")
    }

    private fun amount(raw: String) = raw.replace(",", "").toIntOrNull() ?: 0

    /** The confirm question is a slot child of the dialog's border layer, not a top-level component. */
    private fun choiceDialogText(): String {
        val parent = interfaces[CHOICE_DIALOG] ?: return ""
        val nested = parent[CHOICE_BORDER]?.slotChildren?.map { it.text }.orEmpty()
        return (parent.map { it.text } + nested)
            .filter { it.isNotBlank() }
            .joinToString(" ") { TAGS.replace(it, "") }
    }

    private fun resourceStockpile() = IaiaStation.entries.associateWith { varps.getVar(it.resourceVarp) }

    private fun advanceTimeSlot(): IFSlot? {
        val advanceTime = Gameval.id(Gameval.STRUCT, "magic_advance_time") ?: return null
        for ((barLocation, barNumber) in ActionBarModel.activeBars(varps))
            for (slot in 1..ActionBarModel.SLOTS)
                if (ActionBarModel.resolveSlot(barNumber, slot, varps).structId == advanceTime)
                    return actionbarSlots[barLocation]?.get(slot)
        return null
    }

    private fun resetStats(station: IaiaStation) {
        trackedStation = station
        tracker = SkillTracker(station.skill)
        lastStoredXp = -1
        lastDrainAt = 0L
        lastChangeWasDrain = false
        drainIntervals.clear()
        stallWindowMs = 0L
        contributions = 0
        advanceTimeCasts = 0
        unproductiveCasts = 0
        unreadableOffers = 0
    }

    private fun abort(reason: String) {
        stopReason = reason
        println("[Dream of Iaia] STOPPING: $reason")
        stop()
    }

    private fun report(reason: String) {
        if (reason == lastReport) return
        lastReport = reason
        println("[Dream of Iaia] $reason")
    }

    private suspend fun idle(reason: String) {
        report(reason)
        delay(1100, 450)
    }

    private fun readBoard(station: IaiaStation) = IaiaBoard(
        station = "${station.stationName} - ${station.skill.name.lowercase()}",
        stocked = varps.getVarBit(station.stockedVarbit) == 1,
        advanceTimeCharges = advanceTimeCharges(),
        stations = IaiaStation.entries.map {
            StationRow(
                it.stationName,
                varps.getVarBit(it.assignedWorkerVarbit),
                "${it.resourceLabel}: ${varps.getVar(it.resourceVarp)}",
                varps.getVarBit(it.storedXpVarbit)
            )
        }
    )

    @Composable
    override fun Panel() {
        val shown = board ?: return Hint("Starting")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Readout(listOf("Station" to shown.station))
            SkillTrackerReadout(progress)
            Readout(
                listOf(
                    "Stocked" to if (shown.stocked) "Yes" else "No",
                    "Advance Time charges" to shown.advanceTimeCharges,
                )
            )
            DataTable(STATION_COLUMNS, shown.stations) { row ->
                text(row.name)
                text("${row.workers}")
                text(row.resource)
                text("${row.storedXp}")
            }
            Readout(listOf("Contributions" to "$contributions", "Advance Time casts" to "$advanceTimeCasts"))
            stopReason?.let { Hint("STOPPED: $it", color = Palette.stop) }
        }
    }

    private fun advanceTimeCharges(): String {
        val unlimited = Gameval.id(Gameval.VARBIT, "league_relic_advance_time_unlimited")
            ?.let { varps.getVarBit(it) } == 1
        if (unlimited) return "unlimited"
        return Gameval.id(Gameval.VARBIT, "magic_advance_time_charges_remaining")
            ?.let { varps.getVarBit(it).toString() } ?: "unknown"
    }
}

package com.projectx.script.impl.cryptic.webwalker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectx.ui.compose.OverlayText
import com.projectx.ui.compose.components.ActionButton
import com.projectx.ui.compose.components.ButtonTone
import com.projectx.ui.compose.components.DataTable
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.NumberInput
import com.projectx.ui.compose.components.PillTabs
import com.projectx.ui.compose.components.Readout
import com.projectx.ui.compose.components.ScreenScroll
import com.projectx.ui.compose.components.SearchField
import com.projectx.ui.compose.components.TableColumn
import com.projectx.ui.compose.theme.LocalType
import com.projectx.ui.compose.theme.Palette

private val TABS = PlaceGroup.entries.filter { it != PlaceGroup.TILE }
private val ROW = 26.dp

@Composable
fun WebWalkPanel(
    view: TesterView,
    send: (Command) -> Unit,
    startTour: (List<Place>, Boolean, String) -> Unit,
    endTour: () -> Unit,
) {
    val search = remember { OverlayText("", 40) }
    var group by remember { mutableStateOf(PlaceGroup.EVERYWHERE) }
    var x by remember { mutableIntStateOf(3222) }
    var y by remember { mutableIntStateOf(3218) }
    var plane by remember { mutableIntStateOf(0) }
    val type = LocalType.current

    val inGroup = view.places.filter { group == PlaceGroup.EVERYWHERE || it.group == group }
    val words = search.text.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    val shown = inGroup.filter { place -> words.all { "${place.name} ${place.kind} ${place.coords}".lowercase().contains(it) } }
    val listName = if (words.isEmpty()) group.label else "${group.label} matching \"${search.text.trim()}\""

    ScreenScroll {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Readout(
                listOf(
                    "You" to view.here,
                    "Map marker" to (view.marker ?: "None placed"),
                    "Target" to (view.target?.let { "${it.name} (${it.coords})" } ?: "-"),
                    "Status" to view.status,
                    "Tour" to (view.tour ?: "-"),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Walk to map marker", { send(Command.GoToMarker(planOnly = false)) }, tone = ButtonTone.Primary)
                ActionButton("Plan to map marker", { send(Command.GoToMarker(planOnly = true)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Save this spot", { send(Command.SaveSpot) })
                if (view.tour == null) {
                    ActionButton("Random tour: everywhere", { startTour(view.places, true, "Random tour") }, tone = ButtonTone.Primary)
                } else {
                    ActionButton("End tour after this walk", endTour, tone = ButtonTone.Danger)
                }
            }

            PillTabs(TABS, group, { group = it }, { it.label }, { tab -> view.places.count { tab == PlaceGroup.EVERYWHERE || it.group == tab } })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(search, "Search name, kind or x, y", 240.dp)
                Hint("${shown.size} places")
            }
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                DataTable(
                    listOf(TableColumn("Place", 2.2f), TableColumn("Kind", 0.8f), TableColumn("Tile", width = 120.dp, mono = true), TableColumn("", width = 170.dp)),
                    shown,
                    fill = true,
                    emptyText = "No places match.",
                    key = { "${it.group}${it.coords}${it.name}" },
                ) { place ->
                    text(place.name)
                    text(place.kind, Palette.muted)
                    text(place.coords)
                    cell {
                        ActionButton("Go", { send(Command.Go(place, planOnly = false)) }, tone = ButtonTone.Primary, height = ROW)
                        ActionButton("Plan", { send(Command.Go(place, planOnly = true)) }, height = ROW)
                        if (place.group == PlaceGroup.SAVED) ActionButton("Forget", { send(Command.Forget(place)) }, tone = ButtonTone.Danger, height = ROW)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Tour this list in order", { startTour(shown, false, "Tour of $listName") })
                ActionButton("Random from this list", { startTour(shown, true, "Random $listName") })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Tile", style = type.label)
                NumberInput(x, { x = it }, 0..16383, width = 110.dp)
                NumberInput(y, { y = it }, 0..16383, width = 110.dp)
                NumberInput(plane, { plane = it }, 0..3, width = 90.dp)
                ActionButton("Go", { send(Command.Go(Places.tile(x, y, plane), planOnly = false)) }, tone = ButtonTone.Primary, height = ROW)
                ActionButton("Plan", { send(Command.Go(Places.tile(x, y, plane), planOnly = true)) }, height = ROW)
            }
            Hint("Plan finds a route without walking it, and without teleports. Every walk is added to ~/.projectx/webwalk-results.csv.")

            BasicText("Last route", style = type.heading)
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (view.route.isEmpty()) Hint("No route yet.")
                    view.route.forEach { BasicText(it, style = type.dataSmall.copy(color = Palette.muted)) }
                }
            }

            BasicText("Results", style = type.heading)
            DataTable(
                listOf(TableColumn("Place", 2f), TableColumn("Result", 1.1f), TableColumn("Time", width = 70.dp, mono = true), TableColumn("Links", width = 56.dp, mono = true)),
                view.history,
                emptyText = "Nothing walked yet.",
            ) { record ->
                text(if (record.planOnly) "${record.place} (plan)" else record.place)
                text(record.status, if (record.ok) Palette.running else Palette.stop)
                text("%.1fs".format(record.seconds))
                text("${record.links}")
            }
        }
    }
}

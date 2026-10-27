package com.projectx.script.impl.trent.leagues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectx.script.ComposePanel
import com.projectx.script.Script
import com.projectx.script.ScriptDescription
import com.projectx.script.api.localPlayer
import com.projectx.script.api.walkTo
import com.projectx.ui.backend.dsl.ImGuiDsl.backgroundDrawList
import com.projectx.ui.backend.dsl.utils.ImGuiColors
import com.projectx.ui.compose.OverlayText
import com.projectx.ui.compose.components.ActionButton
import com.projectx.ui.compose.components.ButtonTone
import com.projectx.ui.compose.components.DataTable
import com.projectx.ui.compose.components.Divider
import com.projectx.ui.compose.components.Dropdown
import com.projectx.ui.compose.components.Glyph
import com.projectx.ui.compose.components.Hint
import com.projectx.ui.compose.components.PillTabs
import com.projectx.ui.compose.components.ProgressBar
import com.projectx.ui.compose.components.SettingRow
import com.projectx.ui.compose.components.TableColumn
import com.projectx.ui.compose.components.TextInput
import com.projectx.ui.compose.components.Toggle
import com.projectx.ui.compose.components.press
import com.projectx.ui.compose.components.rememberHover
import com.projectx.ui.compose.theme.LocalType
import com.projectx.ui.compose.theme.Palette
import world.gregs.voidps.type.Tile

private const val ANY = 0
private const val ROW_LIMIT = 200

private data class TaskRow(
    val row: Int,
    val tier: Int,
    val title: String,
    val where: String,
    val distance: String,
    val progress: Int,
    val target: Int,
)

private data class BlockedRow(val row: Int, val tier: Int, val title: String, val blocker: String)

private data class TaskDetail(
    val title: String,
    val summary: String,
    val effort: String,
    val progress: Int,
    val target: Int,
    val lines: List<String>,
    val destination: Tile?,
)

private data class BoardHud(
    val league: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val actionable: Int = 0,
    val points: Int = 0,
    val regions: String = "",
    val regionNames: List<String> = emptyList(),
    val shown: Int = 0,
    val rows: List<TaskRow> = emptyList(),
    val blocked: List<BlockedRow> = emptyList(),
    val blockedTotal: Int = 0,
    val selected: TaskDetail? = null,
)

private enum class BoardTab(val label: String) { NEXT("Next up"), BLOCKED("Blocked"), SELECTED("Selected") }

@ScriptDescription(
    name = "League Task Board",
    version = "2.1.0",
    author = "Trent",
    description = "Ranks every league task by how cheaply it can be finished and how far away it is",
)
class LeagueTaskBoard : Script(), ComposePanel {

    private var hideCompleted by mutableStateOf(true)
    private var hideAggregates by mutableStateOf(true)
    private var onlyUnlocked by mutableStateOf(true)
    private var onlyActionable by mutableStateOf(true)
    private var regionFilter by mutableStateOf(ANY)
    private var tierFilter by mutableStateOf(ANY)
    private var maxRemaining by mutableStateOf(0)
    private val search = OverlayText()
    private var showFilters by mutableStateOf(false)
    private var highlightTargets by mutableStateOf(true)
    private var tab by mutableStateOf(BoardTab.NEXT)
    private var selectedRow by mutableStateOf<Int?>(null)

    @Volatile
    private var selected: LeagueTaskRoute? = null

    @Volatile
    private var walkRequest: Tile? = null

    @Volatile
    private var cachedAt = 0L
    private var cached: List<LeagueTaskRoute> = emptyList()

    private val hud by live(BoardHud(), 500) { report() }

    override suspend fun loop() {
        walkRequest?.let {
            walkRequest = null
            walkTo(it, minimap = true)
        }
        delay(600)
    }

    private fun routes(): List<LeagueTaskRoute> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < REFRESH_MS && cached.isNotEmpty()) {
            return cached
        }
        cachedAt = now
        cached = LeagueRouting.rank(candidates(), localPlayer.tile)
        return cached
    }

    private fun candidates(): List<LeagueTask> {
        val unlockedAreas = LeagueTasks.unlockedAreas()
        val region = LeagueTasks.regions.getOrNull(regionFilter - 1)
        val query = search.text.trim().lowercase()
        val cap = maxRemaining
        return LeagueTasks.all.filter { task ->
            if (hideCompleted && task.completed) return@filter false
            if (hideAggregates && task.aggregate) return@filter false
            if (onlyUnlocked && !task.unlocked(unlockedAreas)) return@filter false
            if (onlyActionable && !task.actionable(unlockedAreas)) return@filter false
            if (region != null && task.region?.id != region.id) return@filter false
            if (tierFilter != ANY && task.tier != tierFilter) return@filter false
            if (cap > 0 && task.remaining > cap) return@filter false
            query.isEmpty() || task.description.lowercase().contains(query) || task.name.contains(query)
        }
    }

    private fun report(): BoardHud {
        val league = LeagueTasks.activeLeague
        if (league <= 0) return BoardHud()
        val all = LeagueTasks.all
        val unlocked = LeagueTasks.unlockedAreas()
        val routes = routes()
        val wanted = selectedRow
        selected = wanted?.let { row -> routes.find { it.task.row == row } ?: selected?.takeIf { it.task.row == row } }
        val blocked = all
            .filter { !it.completed && !it.aggregate && it.unlocked(unlocked) && (!it.levelsMet || !it.chainMet) }
            .sortedBy { it.tier }
        return BoardHud(
            league = league,
            done = all.count { it.completed },
            total = all.size,
            actionable = all.count { it.actionable(unlocked) },
            points = all.filter { it.completed }.sumOf { it.points },
            regions = LeagueTasks.unlockedRegions().joinToString { it.name }.ifEmpty { "none" },
            regionNames = LeagueTasks.regions.map { it.name },
            shown = routes.size,
            rows = routes.take(ROW_LIMIT).map(::taskRow),
            blocked = blocked.take(ROW_LIMIT).map { BlockedRow(it.row, it.tier, it.description.ifBlank { it.name }, blocker(it)) },
            blockedTotal = blocked.size,
            selected = selected?.let(::detail),
        )
    }

    private fun taskRow(route: LeagueTaskRoute): TaskRow {
        val task = route.task
        return TaskRow(
            row = task.row,
            tier = task.tier,
            title = task.description.ifBlank { task.name },
            where = task.region?.name ?: if (task.global) "Anywhere" else "Area ${task.area}",
            distance = distanceLabel(route),
            progress = task.progress,
            target = task.target,
        )
    }

    private fun detail(route: LeagueTaskRoute): TaskDetail {
        val task = route.task
        val lines = buildList {
            if (task.targets.matchedNames.isNotEmpty()) {
                add("Targets: " + task.targets.matchedNames.joinToString())
                add("${task.targets.locIds.size} scenery, ${task.targets.npcIds.size} npc, ${task.targets.objIds.size} item variants")
            }
            val missing = task.missingLevels()
            if (missing.isNotEmpty()) add("Needs " + missing.joinToString { "${it.first.name.lowercase()} ${it.second}" })
            if (!route.located) add("No known location for this task")
        }
        return TaskDetail(
            title = task.description.ifBlank { task.name },
            summary = "Tier ${task.tier}  •  ${task.points} pts  •  ${task.region?.name ?: "Anywhere"}",
            effort = "Effort score ${route.effort}  •  ${distanceLabel(route)}",
            progress = task.progress,
            target = task.target,
            lines = lines,
            destination = route.destination,
        )
    }

    @Composable
    override fun Panel() {
        val board = hud
        if (board.league <= 0) return Hint("Not on a league world.")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Summary(board)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (showFilters) "Hide filters" else "Filters", { showFilters = !showFilters }, height = 30.dp)
                ActionButton("Refresh", { cachedAt = 0L }, icon = Glyph.Refresh, height = 30.dp)
                Hint("${board.shown} shown")
            }
            if (showFilters) Filters(board.regionNames)
            Divider()
            PillTabs(BoardTab.entries, tab, { tab = it }, { it.label })
            when (tab) {
                BoardTab.NEXT -> TaskTable(board)
                BoardTab.BLOCKED -> BlockedTable(board)
                BoardTab.SELECTED -> SelectedDetail(board.selected)
            }
        }
    }

    @Composable
    private fun Summary(board: BoardHud) {
        BasicText(
            "League ${board.league}: ${board.done}/${board.total} done  •  ${board.actionable} actionable  •  ${board.points} pts",
            style = LocalType.current.bodyStrong,
        )
        ProgressBar(if (board.total == 0) 0f else board.done.toFloat() / board.total, "${board.done} / ${board.total}")
        Hint("Regions: ${board.regions}")
    }

    @Composable
    private fun Filters(regionNames: List<String>) {
        SettingRow("Hide completed") { Toggle(hideCompleted) { hideCompleted = it } }
        SettingRow("Hide task sets", "Task sets are completed by finishing many other tasks, never directly.") {
            Toggle(hideAggregates) { hideAggregates = it }
        }
        SettingRow("Unlocked regions only") { Toggle(onlyUnlocked) { onlyUnlocked = it } }
        SettingRow("Actionable only", "Hides tasks whose levels or prerequisite tiers are not met yet.") {
            Toggle(onlyActionable) { onlyActionable = it }
        }
        SettingRow("Region") {
            Dropdown(regionFilter, (0..regionNames.size).toList(), { regionFilter = it }, width = 180.dp) {
                if (it == ANY) "Any" else regionNames.getOrElse(it - 1) { "Region $it" }
            }
        }
        SettingRow("Tier") {
            Dropdown(tierFilter, (0..5).toList(), { tierFilter = it }, width = 180.dp) { if (it == ANY) "Any" else "Tier $it" }
        }
        SettingRow("Search") { TextInput(search, "Task text", 180.dp, leading = Glyph.Search, clearable = true) }
        SettingRow("Max repeats") {
            Dropdown(maxRemaining, REPEAT_CAPS, { maxRemaining = it }, width = 180.dp) { if (it == 0) "Any" else "<= $it" }
        }
        SettingRow("Highlight target in world") { Toggle(highlightTargets) { highlightTargets = it } }
    }

    @Composable
    private fun TaskTable(board: BoardHud) {
        if (board.rows.isEmpty()) return Hint("Nothing matches. Loosen the filters.")
        DataTable(TASK_COLUMNS, board.rows, Modifier.height(TABLE_HEIGHT.dp), fill = true, key = { it.row }) { row ->
            text("${row.tier}")
            cell { TaskLink(row.title, row.row == selectedRow) { selectedRow = row.row } }
            text(row.where)
            text(row.distance)
            if (row.target > 1) {
                cell { ProgressBar(row.progress.toFloat() / row.target, "${row.progress}/${row.target}", height = 14.dp) }
            } else {
                text("-")
            }
        }
        if (board.shown > ROW_LIMIT) Hint("… ${board.shown - ROW_LIMIT} more, narrow the filters")
    }

    @Composable
    private fun TaskLink(title: String, active: Boolean, onClick: () -> Unit) {
        val hover = rememberHover()
        val color = when {
            active -> Palette.amber
            hover.hovered -> Palette.amberHover
            else -> Palette.text
        }
        BasicText(
            title,
            style = LocalType.current.label.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.press(hover, onClick),
        )
    }

    @Composable
    private fun BlockedTable(board: BoardHud) {
        if (board.blocked.isEmpty()) return Hint("Nothing is blocked in your unlocked regions.")
        Hint("${board.blockedTotal} tasks blocked by levels or earlier tiers")
        DataTable(BLOCKED_COLUMNS, board.blocked, Modifier.height(TABLE_HEIGHT.dp), fill = true, key = { it.row }) { row ->
            text("${row.tier}")
            text(row.title)
            text(row.blocker)
        }
    }

    @Composable
    private fun SelectedDetail(detail: TaskDetail?) {
        if (detail == null) return Hint("Pick a task from Next up.")
        BasicText(detail.title, style = LocalType.current.bodyStrong)
        Divider()
        Hint(detail.summary)
        Hint(detail.effort)
        if (detail.target > 1) ProgressBar(detail.progress.toFloat() / detail.target, "${detail.progress} / ${detail.target}")
        detail.lines.forEach { Hint(it) }
        val destination = detail.destination ?: return
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Walk here", { walkRequest = destination }, tone = ButtonTone.Primary, height = 30.dp)
            Hint("(${destination.x}, ${destination.y}, ${destination.level})")
        }
    }

    override fun render() {
        val route = selected ?: return
        val destination = route.destination ?: return
        if (!highlightTargets) return
        backgroundDrawList {
            tileArea(destination, 1, ImGuiColors.ACCENT_SUCCESS_SEMI)
            textOnTile(destination, ImGuiColors.TEXT_ACCENT, route.task.name.take(28))
        }
    }

    private fun distanceLabel(route: LeagueTaskRoute): String = when {
        !route.located -> "?"
        route.precise -> "${route.distance}"
        else -> "~${route.distance}"
    }

    private fun blocker(task: LeagueTask): String {
        val missing = task.missingLevels()
        if (missing.isNotEmpty()) {
            return missing.joinToString { "${it.first.name.lowercase()} ${it.second}" }
        }
        return "earlier tier"
    }

    private companion object {
        const val REFRESH_MS = 3_000L
        const val TABLE_HEIGHT = 320
        val REPEAT_CAPS = listOf(0, 5, 25, 100, 500)
        val TASK_COLUMNS = listOf(
            TableColumn("T", width = 24.dp, mono = true),
            TableColumn("Task"),
            TableColumn("Where", width = 84.dp),
            TableColumn("Dist", width = 48.dp, mono = true),
            TableColumn("Left", width = 76.dp),
        )
        val BLOCKED_COLUMNS = listOf(
            TableColumn("T", width = 24.dp, mono = true),
            TableColumn("Task", weight = 2f),
            TableColumn("Blocked by"),
        )
    }
}

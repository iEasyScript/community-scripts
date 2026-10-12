package com.projectx.script.impl.cryptic.harvesthollow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.projectx.script.ComposePanel
import com.projectx.script.ConfigItem
import com.projectx.script.ConfigVisibilityProvider
import com.projectx.script.ConfigurableScript
import com.projectx.script.Script
import com.projectx.script.ScriptCategory
import com.projectx.script.ScriptDescription
import com.projectx.script.api.captureSerenSpirit
import com.projectx.script.api.isLoggedIn
import com.projectx.util.gaussian

@ScriptDescription(
    name = "Harvest Hollow",
    version = "1.6.0",
    author = "Cryptic",
    description = "Runs the Halloween Harvest Hollow activities: the bonfire, the Smashing Pumpkin, the summoning " +
        "circle, looting the graveyard and digging its Mystery remains. Based on Spade's Harvest Hollow Skiller.",
    category = ScriptCategory.OTHER,
)
class HarvestHollow : Script(), ConfigurableScript, ConfigVisibilityProvider, ComposePanel {

    private val settings = HollowSettings()
    private var startedAt = System.currentTimeMillis()

    private val hud by live(HollowHud(), 500) { report() }

    fun settings() = settings

    override fun isConfigItemVisible(fieldName: String, item: ConfigItem<*>) = settings.isVisible(item)

    override fun onStart() {
        startedAt = System.currentTimeMillis()
        Activity.entries.forEach { it.activity.begin() }
        super.onStart()
    }

    override suspend fun loop() {
        if (!isLoggedIn()) return delay(gaussian(1200, 300))
        if (captureSerenSpirit()) return
        if (!settings.activity.value.activity.step(this, settings)) stop()
    }

    @Composable
    override fun Panel() = HollowPanel(hud)

    private fun report(): HollowHud {
        val selected = settings.activity.value
        val elapsed = System.currentTimeMillis() - startedAt
        val report = selected.activity.report(settings, elapsed / 3_600_000.0)
        return HollowHud(selected.toString(), report.status, clock(elapsed / 1000), report.meter, report.tiles)
    }
}

/** One Harvest Hollow activity; false from [step] means it cannot carry on and the script stops. */
interface HollowActivity {
    /** Clears what the last run counted. */
    fun begin()

    suspend fun step(script: Script, settings: HollowSettings): Boolean

    /** What the panel shows, [hours] into the run. Game thread. */
    fun report(settings: HollowSettings, hours: Double): HollowReport
}

enum class Activity(private val label: String, val activity: HollowActivity) {
    BONFIRE("Bonfire (Firemaking)", Bonfire),
    SMASHING_PUMPKIN("Smashing Pumpkin (mask)", SmashingPumpkin),
    SUMMONING("Summoning circle", Summoning),
    THIEVING("Graveyard looting (Thieving)", Thieving),
    ARCHAEOLOGY("Mystery remains (Archaeology)", Excavation);

    override fun toString() = label
}

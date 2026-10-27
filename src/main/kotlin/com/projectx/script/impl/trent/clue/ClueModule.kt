package com.projectx.script.impl.trent.clue

import com.projectx.ui.backend.dsl.scopes.BackgroundDrawListScope

/**
 * One Treasure Trails puzzle or step type. Sensing and solving happen in [update] on the script loop;
 * [render] and [readout] only replay what the last update published, so they never disagree within a frame.
 *
 * A module must work as a pure advisory: [nextAction] is consulted only when the operator has turned
 * auto-solve on, and returning null there is always a valid implementation.
 */
interface ClueModule {

    val name: String

    /** True when this module's step is on screen right now and worth sensing. */
    fun active(): Boolean

    fun update()

    /** Drop any published state; called as soon as the step goes away. */
    fun reset()

    fun render(scope: BackgroundDrawListScope)

    /** What the panel shows for this step, or null while there is nothing on screen. Game thread. */
    fun readout(): ClueReadout?

    fun nextAction(): ClueAction? = null
}

/** A single click the module would make next. [fire] returns false when the click could not be issued. */
class ClueAction(val description: String, val fire: () -> Boolean)

enum class ClueTone { WORKING, DONE, FAILED, UNSURE }

data class ClueReadout(val step: String, val status: String, val tone: ClueTone, val lines: List<String>)

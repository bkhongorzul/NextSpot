package com.nextspot.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nextspot.app.R
import com.nextspot.app.ui.DemoEngine
import com.nextspot.app.ui.DemoEngine.Phase
import com.nextspot.app.ui.theme.NsColors

/** Everything the demo screen draws, as plain values (so it can be previewed without DemoEngine). */
internal data class DemoUi(
    val phase: Phase,
    val offerMeters: Int? = null,
    val credits: Long = 0L,
    val door: Boolean = false, val belt: Boolean = false, val nav: Boolean = false, val brake: Boolean = false,
    val searching: Boolean = false,
    val accepted: Boolean = false,
    val parked: Boolean = false,
    val declined: Boolean = false,
    val listening: Boolean = false,
    val autoPlaying: Boolean = false,
    val log: List<DemoEngine.LogItem> = emptyList(),
    val sdkLines: List<Pair<String, String>> = emptyList(),
)

/** What the buttons do. Real ones call DemoEngine; previews use the empty default. */
internal class DemoActions(
    val onYes: () -> Unit = {}, val onNo: () -> Unit = {}, val onTalk: () -> Unit = {},
    val onPlay: (cancelCase: Boolean) -> Unit = {}, val onStop: () -> Unit = {},
    val onSearch: () -> Unit = {}, val onParkB: () -> Unit = {},
    val onDoor: () -> Unit = {}, val onNav: () -> Unit = {}, val onBrake: () -> Unit = {},
    val onCancelDeparture: () -> Unit = {},
    val onReset: () -> Unit = {}, val onBack: () -> Unit = {},
)

/** Who wrote a record line: "Car A" = leaving car, "Car B" = searching car, anything else = NextSpot. */
internal enum class Who { LEAVING, SEARCHING, SYSTEM }

internal fun whoOf(raw: String) = when (raw) {
    "Car A" -> Who.LEAVING
    "Car B" -> Who.SEARCHING
    else -> Who.SYSTEM
}

internal fun Who.color(): Color = when (this) {
    Who.LEAVING -> NsColors.leavingCar
    Who.SEARCHING -> NsColors.text
    Who.SYSTEM -> NsColors.main      // NextSpot is the one that connects the two cars
}

@Composable
internal fun Who.label(): String = stringResource(
    when (this) {
        Who.LEAVING -> R.string.leaving_car
        Who.SEARCHING -> R.string.searching_car
        Who.SYSTEM -> R.string.next_spot
    }
)

/** Index (0-based) of the current step, -1 when nothing started. A cancelled flow stops at step 4 (index 3). */
internal fun stepIndex(p: Phase) = when (p) {
    Phase.IDLE -> -1
    Phase.SEARCHING -> 0
    Phase.LIKELY -> 1
    Phase.OFFER, Phase.RESERVED -> 2
    Phase.CONFIRMED, Phase.WITHDRAWN -> 3
    Phase.TAKEN -> 4
}

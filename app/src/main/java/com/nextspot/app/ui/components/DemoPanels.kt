package com.nextspot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextspot.app.R
import com.nextspot.app.ui.DemoEngine
import com.nextspot.app.ui.DemoEngine.Phase
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsType

private val STEP_TITLES = intArrayOf(
    R.string.step1_title, R.string.step2_title, R.string.step3_title, R.string.step4_title, R.string.step5_title,
)
private val STEP_CAPTIONS = intArrayOf(
    R.string.step1_caption, R.string.step2_caption, R.string.step3_caption, R.string.step4_caption, R.string.step5_caption,
)
private val STEP_ICONS = listOf(NsPaths.SEARCH, NsPaths.DETECT, NsPaths.VOICE, NsPaths.BRAKE, NsPaths.PARK)

/** "Now": one big sentence (and, on the voice offer, the yes / no / speak buttons). */
@Composable
internal fun NowPanel(ui: DemoUi, act: DemoActions, modifier: Modifier = Modifier) {
    val offer = ui.phase == Phase.OFFER
    Column(modifier.heightIn(min = 360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (offer) stringResource(R.string.now) + " · " + stringResource(R.string.step3_title) else stringResource(R.string.now),
            style = NsType.body, color = NsColors.text2,
        )
        if (offer) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    stringResource(R.string.offer, ui.offerMeters ?: 0),
                    style = NsType.nowQuestion.copy(lineBreak = NsType.keepAll), color = NsColors.text,
                )
                VoiceHint(ui.listening, act.onTalk)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NsButton(
                        stringResource(R.string.yes), act.onYes, Modifier.weight(1f), NsDims.bigButtonH,
                        NsColors.main, NsColors.onMain, NsType.yesNo, padH = 0.dp,
                    )
                    NsButton(
                        stringResource(R.string.no), act.onNo, Modifier.weight(1f), NsDims.bigButtonH,
                        NsColors.surfaceHigh, NsColors.text, NsType.yesNo, padH = 0.dp,
                    )
                }
            }
        } else {
            val (title, caption) = nowText(ui.phase)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    title, style = NsType.nowQuestion.copy(lineBreak = NsType.keepAll),
                    color = if (ui.phase == Phase.TAKEN) NsColors.ok else NsColors.text,
                )
                Text(caption, style = NsType.caption.copy(lineBreak = NsType.keepAll), color = NsColors.text2)
                if (ui.phase == Phase.TAKEN) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                        Text("+1", style = NsType.bigPoint, color = NsColors.main)
                        Text(stringResource(R.string.points), style = NsType.step, color = NsColors.text, modifier = Modifier.padding(bottom = 4.dp))
                        Text(stringResource(R.string.total, ui.credits), style = NsType.step, color = NsColors.text2, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun nowText(p: Phase): Pair<String, String> = when (p) {
    Phase.IDLE -> stringResource(R.string.ready) to stringResource(R.string.idle_hint)
    Phase.WITHDRAWN -> stringResource(R.string.cancelled_title) to stringResource(R.string.cancelled_caption)
    else -> {
        val i = stepIndex(p)
        stringResource(STEP_TITLES[i]) to stringResource(STEP_CAPTIONS[i])
    }
}

/** Mic icon + the hint. While the microphone is open the icon pulses and the hint says so. */
@Composable
private fun VoiceHint(listening: Boolean, onTalk: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
    )
    // Voice is the main way to answer; touching this line opens the microphone again (e.g. if it missed the answer).
    Row(
        Modifier.clip(NsShape.button).clickable(enabled = !listening, role = Role.Button, onClick = onTalk),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NsMic(NsColors.main, 28.dp, Modifier.alpha(if (listening) pulse else 1f))
        Text(
            stringResource(if (listening) R.string.voice_listening else R.string.voice_hint),
            style = NsType.body, color = if (listening) NsColors.text else NsColors.text2,
        )
    }
}

private enum class StepState { DONE, CURRENT, TODO, FINAL, STOP }

/** The five steps as one quiet list (bottom of the right column). The 4th row turns into "cancelled". */
@Composable
internal fun StepList(phase: Phase, modifier: Modifier = Modifier) {
    val cur = stepIndex(phase)
    Column(modifier) {
        for (i in 0..4) {
            val st = when {
                phase == Phase.WITHDRAWN && i == 3 -> StepState.STOP
                phase == Phase.TAKEN && i == 4 -> StepState.FINAL
                i < cur -> StepState.DONE
                i == cur -> StepState.CURRENT
                else -> StepState.TODO
            }
            val markBg by animateColorAsState(
                when (st) {
                    StepState.CURRENT -> NsColors.main
                    StepState.FINAL -> NsColors.ok
                    StepState.STOP -> NsColors.cancelTint
                    else -> NsColors.surface
                }, tween(200), label = "mark",
            )
            val iconColor = when (st) {
                StepState.CURRENT, StepState.FINAL -> NsColors.onMain
                StepState.STOP -> NsColors.cancelIcon
                StepState.DONE -> NsColors.main
                StepState.TODO -> NsColors.text3
            }
            val lead = st == StepState.CURRENT || st == StepState.FINAL || st == StepState.STOP
            Row(Modifier.height(NsDims.stepRowH), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(NsDims.stepMark).clip(NsShape.mark).background(markBg),
                    contentAlignment = Alignment.Center,
                ) { NsIcon(if (st == StepState.STOP) NsPaths.X else STEP_ICONS[i], iconColor, 24.dp, strokeWidth = 2.2f, square = true) }
                Spacer(Modifier.width(20.dp))
                Text(
                    stringResource(if (st == StepState.STOP) R.string.cancelled_title else STEP_TITLES[i]),
                    style = if (lead) NsType.stepCurrent else NsType.step,
                    color = when {
                        lead -> NsColors.text
                        st == StepState.DONE -> NsColors.text2
                        else -> NsColors.text3
                    },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One line under the map: the latest record, and the button that opens the full record. */
@Composable
internal fun Ticker(log: List<DemoEngine.LogItem>, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val last = log.firstOrNull()
    Row(modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.just), style = NsType.body, color = NsColors.text2, maxLines = 1)
        if (last != null) {
            val who = whoOf(last.who)
            Text(who.label(), style = NsType.bodyBold, color = who.color(), maxLines = 1)
            Text(
                last.text, style = NsType.body, color = NsColors.leavingCar, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        } else Spacer(Modifier.weight(1f))
        Box(
            Modifier.height(48.dp).clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (open) stringResource(R.string.log_close) + " ×" else stringResource(R.string.log_all) + " ›",
                style = NsType.body, color = NsColors.text2, maxLines = 1,
            )
        }
    }
}

/** The record panel over the bottom of the map: six rows tall, scrolls through the whole record. */
@Composable
internal fun RecordPanel(log: List<DemoEngine.LogItem>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(NsColors.appBg).padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 16.dp),
    ) {
        LazyColumn(Modifier.height(264.dp)) {
            items(log) { item ->
                val who = whoOf(item.who)
                Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(item.time, style = NsType.body, color = NsColors.text2, modifier = Modifier.width(104.dp), maxLines = 1)
                    Text(who.label(), style = NsType.bodyBold, color = who.color(), modifier = Modifier.width(150.dp), maxLines = 1)
                    Text(item.text, style = NsType.body, color = NsColors.leavingCar, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The quiet strip at the bottom: play, play the cancelled flow, start over, and the manual signals toggle. */
@Composable
internal fun DemoStrip(ui: DemoUi, act: DemoActions, signalsOpen: Boolean, onToggleSignals: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(NsDims.stripH).background(NsColors.strip).padding(horizontal = NsDims.sidePad),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.presenter), style = NsType.tag.copy(fontWeight = FontWeight.Normal), color = NsColors.text2, modifier = Modifier.padding(end = 12.dp), maxLines = 1)
        NsButton(
            stringResource(if (ui.autoPlaying) R.string.pause else R.string.play),
            { if (ui.autoPlaying) act.onStop() else act.onPlay(false) },
            icon = { NsIcon(if (ui.autoPlaying) NsPaths.PAUSE else NsPaths.PLAY, NsColors.text, 22.dp, filled = true) },
        )
        NsButton(
            stringResource(R.string.play_cancelled), { act.onPlay(true) },
            icon = { NsIcon(NsPaths.PLAY, NsColors.text, 22.dp, filled = true) },
        )
        NsButton(
            stringResource(R.string.reset), act.onReset,
            icon = { NsIcon(NsPaths.RESET, NsColors.text, 22.dp, strokeWidth = 2.4f, square = true) },
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.height(64.dp).clickable(role = Role.Button, onClick = onToggleSignals).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.signals_open), style = NsType.body, color = NsColors.text2, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            NsIcon(if (signalsOpen) NsPaths.CHEVRON_UP else NsPaths.CHEVRON_DOWN, NsColors.text2, 24.dp, strokeWidth = 2.4f, square = true)
        }
    }
}

private class Tile(val leaving: Boolean, val label: Int, val done: Boolean, val can: Boolean, val go: () -> Unit)

/** Manual car signals (3x2) and, small, which SDK powers each part. Opens above the strip. */
@Composable
internal fun SignalsPopover(ui: DemoUi, act: DemoActions, modifier: Modifier = Modifier) {
    val p = ui.phase
    val idle = !ui.autoPlaying
    val tiles = listOf(
        Tile(false, R.string.sig_search, ui.searching, idle && !ui.searching, act.onSearch),
        Tile(true, R.string.sig_door, ui.door, idle, act.onDoor),
        Tile(true, R.string.sig_nav, ui.nav, idle, act.onNav),
        Tile(true, R.string.sig_brake, ui.brake, idle && !ui.brake, act.onBrake),
        Tile(false, R.string.sig_park, ui.parked, idle && ui.accepted && !ui.parked, act.onParkB),
        Tile(true, R.string.sig_cancel, p == Phase.WITHDRAWN, idle && (p == Phase.LIKELY || p == Phase.OFFER || p == Phase.RESERVED), act.onCancelDeparture),
    )
    Column(
        modifier.width(820.dp).clip(NsShape.button).background(NsColors.popover).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.signals_sub), style = NsType.tag.copy(fontWeight = FontWeight.Normal), color = NsColors.text2)
        for (row in tiles.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in row) SignalTile(t, Modifier.weight(1f))
            }
        }
        if (ui.sdkLines.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sdk_title), style = NsType.small, color = NsColors.text2)
                ui.sdkLines.forEach { (part, sdk) ->
                    Row {
                        Text(part, style = NsType.small.copy(fontWeight = FontWeight.Normal), color = NsColors.text3, modifier = Modifier.weight(0.45f), maxLines = 1)
                        Text(
                            sdk, style = NsType.small.copy(fontWeight = FontWeight.Normal),
                            color = if (sdk.contains("Pleos")) NsColors.text else NsColors.text2,
                            modifier = Modifier.weight(0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalTile(t: Tile, modifier: Modifier) {
    val active = t.done || t.can
    val whoColor: Color = if (active) (if (t.leaving) NsColors.leavingCar else NsColors.main) else NsColors.text3
    Row(
        modifier.height(NsDims.signalButtonH).clip(NsShape.button)
            .background(if (t.can) NsColors.surfaceHigh else NsColors.surface)
            .clickable(enabled = t.can, role = Role.Button, onClick = t.go)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (t.leaving) R.string.leaving_car else R.string.searching_car),
                style = NsType.small.copy(fontWeight = FontWeight.Bold), color = whoColor, maxLines = 1,
            )
            Text(
                stringResource(t.label), style = NsType.bodyBold,
                color = if (t.can) NsColors.text else if (t.done) NsColors.text2 else NsColors.text3,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (t.done) NsIcon(NsPaths.CHECK, NsColors.main, 24.dp, strokeWidth = 3f, square = true)
    }
}

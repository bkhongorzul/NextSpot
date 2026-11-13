package com.nextspot.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextspot.app.R
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.DemoEngine
import com.nextspot.app.ui.components.DemoActions
import com.nextspot.app.ui.components.DemoStrip
import com.nextspot.app.ui.components.DemoUi
import com.nextspot.app.ui.components.LotMap
import com.nextspot.app.ui.components.NowPanel
import com.nextspot.app.ui.components.NsTitleBar
import com.nextspot.app.ui.components.NsWindow
import com.nextspot.app.ui.components.RecordPanel
import com.nextspot.app.ui.components.SignalsPopover
import com.nextspot.app.ui.components.StepList
import com.nextspot.app.ui.components.Ticker
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsTheme

/**
 * One-screen DEMO (both cars at once), laid out as in docs/design/DESIGN.md sections 4 to 8.
 * All state comes from DemoEngine; this file only draws it and calls its functions.
 */
private fun DemoEngine.toUi() = DemoUi(
    phase = phase, offerMeters = offerMeters, credits = credits,
    door = door, belt = belt, nav = nav, brake = brake,
    searching = searching, accepted = accepted, parked = parked,
    listening = listening, autoPlaying = autoPlaying,
    log = log.toList(), sdkLines = sdkLines,
)

private fun DemoEngine.toActions(onBack: () -> Unit) = DemoActions(
    onYes = { answerYes() }, onNo = { answerNo() }, onTalk = { listenForAnswer() },
    onPlay = { cancel -> playScenario(cancelCase = cancel) }, onStop = { stopAutoPlay() },
    onSearch = { startSearch() }, onParkB = { parkCarB() },
    onDoor = { onDoorOpenedAndClosed() }, onNav = { onNavigationSet() }, onBrake = { onParkingBrakeReleased() },
    onCancelDeparture = { cancelDeparture() },
    onReset = { reset() }, onBack = onBack,
)

@Composable
fun DemoScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val e = remember { DemoEngine(context, speaker, scope) }
    DisposableEffect(Unit) { onDispose { e.dispose() } }

    // Ask once for the microphone (for the "yes" voice answer). Buttons still work without it.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    NsTheme { DemoContent(e.toUi(), e.toActions(onBack)) }
}

@Composable
internal fun DemoContent(ui: DemoUi, act: DemoActions, startRecordOpen: Boolean = false, startSignalsOpen: Boolean = false) {
    var recordOpen by remember { mutableStateOf(startRecordOpen) }
    var signalsOpen by remember { mutableStateOf(startSignalsOpen) }
    var playingCancel by remember { mutableStateOf(false) }   // which flow was started last (for its pause button)

    NsWindow {
        Column(Modifier.fillMaxSize()) {
            NsTitleBar(stringResource(R.string.demo_title), stringResource(R.string.demo_back), act.onBack)

            Row(
                Modifier.fillMaxWidth().height(NsDims.windowH - NsDims.titleBar - NsDims.stripH)
                    .padding(horizontal = NsDims.sidePad),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                // left: the lot and a one-line ticker
                Column(Modifier.width(NsDims.mapW), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box {
                        LotMap(ui.phase, ui.offerMeters)
                        if (recordOpen) RecordPanel(ui.log, Modifier.align(Alignment.BottomCenter))
                    }
                    Ticker(ui.log, recordOpen, { recordOpen = !recordOpen })
                }
                // right: what is happening now (top), the steps (bottom)
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, bottom = 52.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    NowPanel(ui, act)
                    StepList(ui.phase)
                }
            }
        }

        DemoStrip(
            ui, act, playingCancel, { cancel -> playingCancel = cancel; act.onPlay(cancel) },
            signalsOpen, { signalsOpen = !signalsOpen }, Modifier.align(Alignment.BottomCenter),
        )
        if (signalsOpen) {
            SignalsPopover(ui, act, Modifier.align(Alignment.BottomEnd).padding(end = NsDims.sidePad, bottom = 116.dp))
        }
    }
}

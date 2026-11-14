package com.nextspot.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.R
import com.nextspot.app.car.CarSignals
import com.nextspot.app.car.DepartureDetector
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.ui.components.CarBox
import com.nextspot.app.ui.components.CarFrame
import com.nextspot.app.ui.components.CarGlyph
import com.nextspot.app.ui.components.CenterColumn
import com.nextspot.app.ui.components.CheckDisc
import com.nextspot.app.ui.components.Eyebrow
import com.nextspot.app.ui.components.Headline
import com.nextspot.app.ui.components.HintRow
import com.nextspot.app.ui.components.MarkKind
import com.nextspot.app.ui.components.NsIcon
import com.nextspot.app.ui.components.NsPaths
import com.nextspot.app.ui.components.Pill
import com.nextspot.app.ui.components.SimButton
import com.nextspot.app.ui.components.StatusMark
import com.nextspot.app.ui.components.SubText
import com.nextspot.app.ui.components.TextLink
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsType
import kotlinx.coroutines.delay

private const val CAR_A_ID = "car-A"

/**
 * Car A: the car that is leaving its parking spot. Screens follow the design photos:
 * waiting -> A1 signal detected -> A2 waiting for the brake -> A3 departure confirmed, or A4 cancelled.
 * In the real car there are no buttons: the car's own sensors drive everything and this screen only shows
 * which sensors fired. The simulator row (hidden by default) stands in for the sensors.
 */
@Composable
fun CarAScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    // spoken lines are read here because the Firestore callback below is not a composable
    val sayMatched = stringResource(R.string.a_say_matched)
    val sayTaken = stringResource(R.string.a_say_taken)
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { speaker.setLanguage(if (language == "en") "en" else "ko") }
    val repo = remember { SpotRepository() }
    var spotId by remember { mutableStateOf<String?>(null) }
    var spot by remember { mutableStateOf<Spot?>(null) }
    var credits by remember { mutableLongStateOf(0L) }
    var reward by remember { mutableStateOf(false) }
    // which sensors have fired (for the display only; the logic stays in DepartureDetector)
    var door by remember { mutableStateOf(false) }
    var belt by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    var brake by remember { mutableStateOf(false) }
    val seat = door || belt   // either one counts as "driver got in" (same as DepartureDetector)

    // the "+1 credit" card stays 2.5 s, then fades out
    LaunchedEffect(reward) {
        if (reward) { delay(2500); reward = false }
    }

    val detector = remember {
        DepartureDetector(
            repo = repo,
            carId = CAR_A_ID,
            group = DemoPlaces.GROUP,
            lat = DemoPlaces.CAR_A_LAT,
            lng = DemoPlaces.CAR_A_LNG,
            onSpotPosted = { spotId = it },
            onLog = { },
        )
    }
    // every sensor (real or simulated) goes through here, so the screen can show which ones arrived
    val sensors = remember {
        object : CarSignals.Listener {
            override fun onDoorOpenedAndClosed() { door = true; detector.onDoorOpenedAndClosed() }
            override fun onSeatbeltBuckled() { belt = true; detector.onSeatbeltBuckled() }
            override fun onNavigationSet() { nav = true; detector.onNavigationSet() }
            override fun onParkingBrakeReleased() { brake = true; detector.onParkingBrakeReleased() }
        }
    }
    val signals = remember { Platform.carSignals(context) }

    fun clearSensors() { door = false; belt = false; nav = false; brake = false }

    // Real car signals (Pleos variant). In the sim variant this does nothing.
    DisposableEffect(Unit) {
        signals.start(sensors)
        onDispose { signals.stop() }
    }

    // Credits earned by this car
    DisposableEffect(Unit) {
        val reg = repo.listenCredits(CAR_A_ID) { credits = it }
        onDispose { reg.remove() }
    }

    // Watch our own spot and tell the driver by voice what happens to it
    DisposableEffect(spotId) {
        val id = spotId
        var lastStatus: String? = null
        val reg = id?.let {
            repo.listenSpot(it) { s ->
                spot = s
                val status = s?.status
                if (status != lastStatus) {
                    when (status) {
                        SpotStatus.MATCHED -> speaker.say(sayMatched)
                        SpotStatus.TAKEN -> {
                            speaker.say(sayTaken)
                            reward = true   // big "+1 credit" for a moment (the voice may not be audible)
                        }
                        SpotStatus.WITHDRAWN -> clearSensors()   // the detector starts over; so does the display
                    }
                    lastStatus = status
                }
            }
        }
        onDispose { reg?.remove() }
    }

    val status = spot?.status
    val registered = status == SpotStatus.LIKELY || status == SpotStatus.CONFIRMED ||
        status == SpotStatus.MATCHED || status == SpotStatus.TAKEN
    val confirmedOn = status == SpotStatus.CONFIRMED || status == SpotStatus.TAKEN || spot?.confirmed == true

    // A1 stays on screen for a moment after the spot is registered, so the detected signals can be read
    var a1Done by remember { mutableStateOf(false) }
    LaunchedEffect(registered) {
        if (registered) { delay(2000); a1Done = true } else a1Done = false
    }

    Box(Modifier.fillMaxSize()) {
        // top right: demo only (cancel the departure / start over); the real car needs neither
        CarFrame(onBack, right = {
            TextLink(stringResource(R.string.a_not_leaving), { detector.cancel(); clearSensors() }, NsCar.text3)
            TextLink(stringResource(R.string.a_restart), {
                detector.reset()
                clearSensors()
                spotId = null
                spot = null
                reward = false
            }, NsCar.text3)
        }) {
          Column(Modifier.fillMaxSize()) {
            // every state keeps the same centered text; the sensors sit at the bottom
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // A3 departure confirmed
                confirmedOn -> {
                    CenterColumn {
                        StatusMark(MarkKind.FILLED)
                        Spacer(Modifier.height(36.dp))
                        Headline(stringResource(R.string.a_confirmed_title), center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText(stringResource(R.string.a_confirmed_sub), center = true)
                        Spacer(Modifier.height(36.dp))
                        Pill(
                            stringResource(
                                when (status) {
                                    SpotStatus.TAKEN -> R.string.a_pill_taken
                                    SpotStatus.MATCHED -> R.string.a_pill_matched
                                    else -> R.string.a_pill_waiting
                                },
                            ),
                        )
                        if (credits > 0) {
                            Spacer(Modifier.height(20.dp))
                            Text(stringResource(R.string.a_credits, credits), style = NsType.body, color = NsCar.text3)
                        }
                    }
                }

                // A4 cancelled: the driver did not really leave (45 s without the brake, or "출발 안 함")
                status == SpotStatus.WITHDRAWN && !seat && !nav -> {
                    CenterColumn {
                        StatusMark(MarkKind.CANCEL)
                        Spacer(Modifier.height(36.dp))
                        Headline(stringResource(R.string.a_cancel_title), center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText(stringResource(R.string.a_cancel_sub), center = true)
                        Spacer(Modifier.height(36.dp))
                        Pill(stringResource(R.string.a_pill_stopped), cancel = true)
                    }
                }

                // A2 waiting: registered, waits for the parking brake
                registered && a1Done -> {
                    CenterColumn {
                        Headline(stringResource(R.string.a_told_title), center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText(stringResource(R.string.a_told_sub), center = true)
                        Spacer(Modifier.height(36.dp))
                        CarBox(Modifier.width(1000.dp), pad = 36.dp) {
                            Row(
                                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            ) {
                                CarGlyph(NsCar.accent, 44.dp)
                                Text(
                                    stringResource(if (status == SpotStatus.MATCHED) R.string.a_one_waiting else R.string.a_nearby_told),
                                    style = NsType.title.copy(fontSize = 30.sp), color = NsCar.text,
                                )
                            }
                        }
                        Spacer(Modifier.height(36.dp))
                        HintRow(
                            stringResource(R.string.a_hint_brake),
                            icon = { NsIcon(NsPaths.BRAKE, NsCar.text2, 34.dp, strokeWidth = 1.8f) },
                        )
                    }
                }

                // A1 signal detected: door / belt, destination set (no driver action needed)
                seat || nav || registered -> {
                    CenterColumn {
                        Headline(stringResource(if (registered) R.string.a_detect_title_registered else R.string.a_detect_title), center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText(stringResource(if (registered) R.string.a_detect_sub_registered else R.string.a_detect_sub), center = true)
                        Spacer(Modifier.height(36.dp))
                        HintRow(stringResource(if (registered) R.string.a_hint_notifying else R.string.a_hint_waiting_signal))
                    }
                }

                // nothing yet: parked, waiting for the first signal
                else -> {
                    CenterColumn {
                        Headline(stringResource(R.string.a_idle_title), center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText(stringResource(R.string.a_idle_sub), center = true)
                    }
                }
            }
            }
            // The four car sensors, always at the bottom. In the real car a cell turns yellow when the car reports
            // the signal by itself; in the simulator a tap sends the same signal (no separate button panel).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SensorCell(stringResource(R.string.a_sensor_door), door, Modifier.weight(1f)) { sensors.onDoorOpenedAndClosed() }
                SensorCell(stringResource(R.string.a_sensor_belt), belt, Modifier.weight(1f)) { sensors.onSeatbeltBuckled() }
                SensorCell(stringResource(R.string.a_sensor_nav), nav, Modifier.weight(1f)) { sensors.onNavigationSet() }
                SensorCell(stringResource(R.string.a_sensor_brake), brake, Modifier.weight(1f)) { sensors.onParkingBrakeReleased() }
            }
          }
        }

        // "+1 credit" when the spot has been taken
        androidx.compose.animation.AnimatedVisibility(
            visible = reward,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(500)),
        ) {
            CarBox(Modifier.width(900.dp), pad = 56.dp) {
                CenterColumn2 {
                    Text(stringResource(R.string.a_reward_title), style = NsType.logo.copy(fontSize = 80.sp, lineHeight = 92.sp), color = NsCar.accent)
                    Spacer(Modifier.height(10.dp))
                    SubText(stringResource(R.string.a_reward_sub), center = true)
                }
            }
        }
    }
}

@Composable
private fun CenterColumn2(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)

/**
 * One car sensor. Fired = yellow outline and yellow text, not fired = grey outline and text.
 * The real car fires it by itself; in the simulator a tap sends the same signal.
 */
@Composable
private fun SensorCell(label: String, on: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val line by animateColorAsState(if (on) NsCar.accent else NsCar.border, tween(250), label = "line")
    val textColor by animateColorAsState(if (on) NsCar.accent else NsCar.text2, tween(250), label = "text")
    Box(
        modifier.clip(NsCar.shape).border(if (on) 2.dp else 1.dp, line, NsCar.shape)
            .clickable(role = Role.Button, onClick = onTap).padding(vertical = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NsType.title.copy(fontSize = 28.sp), color = textColor, maxLines = 1)
    }
}

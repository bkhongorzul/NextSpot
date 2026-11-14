package com.nextspot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.R
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.data.distanceMeters
import com.nextspot.app.ui.components.CarBox
import com.nextspot.app.ui.components.CarFrame
import com.nextspot.app.ui.components.CarGlyph
import com.nextspot.app.ui.components.CenterColumn
import com.nextspot.app.ui.components.Eyebrow
import com.nextspot.app.ui.components.Headline
import com.nextspot.app.ui.components.HintRow
import com.nextspot.app.ui.components.MarkKind
import com.nextspot.app.ui.components.MicBadge
import com.nextspot.app.ui.components.NsIcon
import com.nextspot.app.ui.components.NsPaths
import com.nextspot.app.ui.components.Pill
import com.nextspot.app.ui.components.Radar
import com.nextspot.app.ui.components.SimButton
import com.nextspot.app.ui.components.StatusMark
import com.nextspot.app.ui.components.StepTrack
import com.nextspot.app.ui.components.SubText
import com.nextspot.app.ui.components.TextLink
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsType
import kotlin.math.roundToInt

private const val CAR_B_ID = "car-B"

private fun Spot.metersFromCarB(): Int =
    distanceMeters(DemoPlaces.CAR_B_LAT, DemoPlaces.CAR_B_LNG, lat, lng).roundToInt()

/**
 * Car B: the car searching for a parking spot. Screens follow the design photos:
 * driving -> B1 start offer -> B2 searching -> B3 candidate -> B4 on the way -> B5 spot confirmed -> B6 parked.
 * Voice is the main way to answer; the underlined texts are the quiet touch alternative.
 */
@Composable
fun CarBScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    // spoken lines are read with context.getString because they are used inside callbacks (not composables)
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { speaker.setLanguage(if (language == "en") "en" else "ko") }
    val repo = remember { SpotRepository() }
    val navigator = remember { Platform.navigator(context) }

    var arrived by remember { mutableStateOf(false) }      // near the destination: B1
    var searching by remember { mutableStateOf(false) }    // B2 and on
    var openSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }
    val declined = remember { mutableStateListOf<String>() }
    val announced = remember { mutableStateListOf<String>() }
    var accepted by remember { mutableStateOf<Spot?>(null) }
    var parked by remember { mutableStateOf(false) }       // B6
    var thanked by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }      // the driver said "아니요" to the offer
    var searchNote by remember { mutableStateOf(context.getString(R.string.b_note_default)) }

    DisposableEffect(Unit) {
        navigator.start()
        onDispose { navigator.stop() }
    }

    // While searching: listen for open spots in our group
    DisposableEffect(searching) {
        val reg = if (searching) {
            repo.listenOpenSpots(DemoPlaces.GROUP, CAR_B_ID) { openSpots = it }
        } else null
        onDispose { reg?.remove() }
    }

    // The closest open spot we haven't declined = the current offer
    val offer: Spot? = if (searching && accepted == null) {
        openSpots.filter { it.id !in declined }.minByOrNull { it.metersFromCarB() }
    } else null

    // Speak the offer once, when it first appears
    LaunchedEffect(offer?.id) {
        val o = offer ?: return@LaunchedEffect
        if (o.id !in announced) {
            announced.add(o.id)
            speaker.say(context.getString(R.string.b_say_offer, o.metersFromCarB()))
        }
    }

    // After accepting: watch the spot in case Car A cancels
    DisposableEffect(accepted?.id) {
        val id = accepted?.id
        val reg = id?.let {
            repo.listenSpot(it) { s ->
                if (s != null && s.status == SpotStatus.WITHDRAWN) {
                    speaker.say(context.getString(R.string.b_say_cancelled))
                    searchNote = context.getString(R.string.b_note_cancelled)
                    accepted = null
                } else if (s != null && s.status == SpotStatus.MATCHED) {
                    accepted = s   // keep the latest copy (e.g. confirmed = true)
                }
            }
        }
        onDispose { reg?.remove() }
    }

    fun accept(o: Spot) {
        repo.accept(o.id, CAR_B_ID) { ok ->
            if (ok) {
                refused = false
                accepted = o
                navigator.navigateTo(o.lat, o.lng, context.getString(R.string.b_poi_name))
                speaker.say(context.getString(R.string.b_say_guide))
            } else {
                declined.add(o.id)
                speaker.say(context.getString(R.string.b_say_taken_other))
            }
        }
    }

    fun parkNow(acc: Spot) {
        repo.markTaken(acc)
        speaker.say(context.getString(R.string.b_say_parked))
        accepted = null
        searching = false
        parked = true
    }

    val acc = accepted
    CarFrame(onBack) {
        when {
            // B6 parked
            parked -> {
                CenterColumn {
                    StatusMark(MarkKind.FILLED)
                    Spacer(Modifier.height(40.dp))
                    Headline(stringResource(R.string.b_parked_title), center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText(stringResource(R.string.b_parked_sub), center = true)
                    Spacer(Modifier.height(44.dp))
                    // TODO(zol): send the thanks to Car A (not stored anywhere yet)
                    Row(
                        Modifier.clip(NsCar.shape).background(NsCar.cardHigh)
                            .clickableBox { thanked = true }.padding(horizontal = 48.dp, vertical = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(stringResource(if (thanked) R.string.b_thanks_sent else R.string.b_thanks_send), style = NsType.button.copy(fontSize = 30.sp), color = NsCar.text)
                    }
                }
            }

            // B5 spot confirmed: the parking brake of Car B finishes the trip by itself in the real car
            acc != null && acc.confirmed -> {
                CenterColumn {
                    StatusMark(MarkKind.RING)
                    Spacer(Modifier.height(40.dp))
                    Headline(stringResource(R.string.b_confirmed_title), center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText(stringResource(R.string.b_confirmed_sub), center = true)
                    Spacer(Modifier.height(44.dp))
                    HintRow(stringResource(R.string.b_hint_auto))
                }
                // Simulator: stands in for Car B's own parking brake signal.
                // TODO: in the real car, finish when Car B's parking brake is set (CarSignals / Pleos Vehicle SDK) - no button.
                Box(Modifier.align(Alignment.BottomEnd)) { SimButton(stringResource(R.string.b_sim_brake)) { parkNow(acc) } }
            }

            // B4 on the way: Car A's live state is shown
            acc != null -> {
                Column(Modifier.fillMaxSize()) {
                    Eyebrow(stringResource(R.string.b_guiding))
                    Spacer(Modifier.height(10.dp))
                    Headline(stringResource(R.string.b_going_title))
                    Spacer(Modifier.height(10.dp))
                    SubText(stringResource(R.string.b_meters_ahead, acc.metersFromCarB()))
                    Spacer(Modifier.weight(1f))
                    StepTrack(current = 2)
                    Spacer(Modifier.weight(1f))
                    CarBox(pad = 36.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(NsCar.cardHigh), contentAlignment = Alignment.Center) {
                                NsIcon(NsPaths.CLOCK, NsCar.accent, 34.dp, strokeWidth = 1.8f)
                            }
                            Column {
                                Text(stringResource(R.string.b_front_preparing), style = NsType.title.copy(fontSize = 32.sp), color = NsCar.text)
                                Text(stringResource(R.string.b_soon_confirmed), style = NsType.caption, color = NsCar.text2)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    // TODO(zol): releasing the matched spot is not in SpotRepository yet; for now this only leaves the screen state
                    Box(
                        Modifier.fillMaxWidth().clip(NsCar.shape).background(NsCar.cardHigh)
                            .clickableBox { declined.add(acc.id); accepted = null; refused = false; searchNote = context.getString(R.string.b_note_other) }
                            .padding(vertical = 34.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.b_other_spot), style = NsType.button.copy(fontSize = 32.sp), color = NsCar.text) }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.align(Alignment.End)) { SimButton(stringResource(R.string.b_sim_brake)) { parkNow(acc) } }
                }
            }

            // B3 candidate
            offer != null -> {
                val confirmedNow = offer.status == SpotStatus.CONFIRMED || offer.confirmed
                Column(Modifier.fillMaxSize()) {
                    Eyebrow(stringResource(R.string.b_found))
                    Spacer(Modifier.height(10.dp))
                    Headline(stringResource(if (confirmedNow) R.string.b_title_leaving else R.string.b_title_soon))
                    Spacer(Modifier.height(28.dp))
                    CarBox(pad = 36.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NsIcon(NsPaths.PIN, NsCar.accent, 44.dp, strokeWidth = 1.8f)
                            Spacer(Modifier.width(22.dp))
                            Text(stringResource(R.string.b_about_meters, offer.metersFromCarB()), style = NsType.title.copy(fontSize = 38.sp), color = NsCar.text)
                            Spacer(Modifier.weight(1f))
                            // likely = might open, confirmed = leaving now (the change must be visible): plain yellow words
                            Text(
                                stringResource(if (confirmedNow) R.string.b_status_confirmed else R.string.b_status_likely),
                                style = NsType.bodyBold.copy(fontSize = 30.sp), color = NsCar.accent,
                            )
                        }
                        Spacer(Modifier.height(26.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(NsCar.border))
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            NsIcon(if (confirmedNow) NsPaths.BRAKE else NsPaths.DETECT, NsCar.text2, 32.dp, strokeWidth = 1.8f)
                            Text(
                                stringResource(if (confirmedNow) R.string.b_why_confirmed else R.string.b_why_likely),
                                style = NsType.bodyBold.copy(fontSize = 28.sp), color = NsCar.text2,
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    // voice first: the big card asks for the voice, touch is the small underlined text below
                    // TODO(zol): connect Platform.voiceInput(context).listen(...) here -
                    //   YES -> accept(offer), NO -> declined.add(offer.id) + refused = true,
                    //   UNKNOWN -> ask again; start listening after the TTS offer ends
                    // no extra card here: the voice prompt stands free, the only card is the info above
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CenterColumn {
                            MicBadge()
                            Text(stringResource(R.string.b_voice_q), style = NsType.logo.copy(fontSize = 46.sp, lineHeight = 56.sp), color = NsCar.text)
                            Spacer(Modifier.height(12.dp))
                            SubText(stringResource(R.string.b_voice_sub), center = true)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TextLink(stringResource(R.string.yes), { accept(offer) })
                        Text("·", color = NsCar.text3, style = NsType.body)
                        TextLink(stringResource(R.string.no), { declined.add(offer.id); refused = true })
                    }
                }
            }

            // B2 searching (also after "아니요" or a cancelled spot)
            searching -> {
                CenterColumn {
                    Radar()
                    Spacer(Modifier.height(36.dp))
                    Headline(stringResource(R.string.b_searching), center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText(searchNote, center = true)
                    Spacer(Modifier.height(36.dp))
                    HintRow(stringResource(R.string.b_wait))
                    if (refused) {
                        Spacer(Modifier.height(12.dp))
                        TextLink(stringResource(R.string.b_see_again), { declined.clear(); refused = false; searchNote = context.getString(R.string.b_note_default) })
                    }
                }
            }

            // B1 start offer: voice "자리 찾아줘", tap is the quiet alternative
            arrived -> {
                // TODO: in the real car this screen comes from the NaviHelper arrival event:
                //   Platform.carContext(context).start(onNearDestination = { arrived = true }, onPosition = { _, _ -> })
                //   (pleos threshold is NEAR_DESTINATION_M = 500)
                // TODO(zol): voice start - the driver says "자리 찾아줘" -> searching = true (needs the keyword in VoiceInput.listen)
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    // the distance line sits right on top of the mic card
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NsIcon(NsPaths.PIN, NsCar.text2, 34.dp, strokeWidth = 1.8f)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.b_dist_line), style = NsType.bodyBold.copy(fontSize = 28.sp), color = NsCar.text2)
                    }
                    CarBox(pad = 36.dp) {   // height follows the content, so nothing is cut off
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            MicBadge()
                            Text(stringResource(R.string.b_say_find), style = NsType.logo.copy(fontSize = 58.sp, lineHeight = 68.sp), color = NsCar.text)
                            Spacer(Modifier.height(10.dp))
                            SubText(stringResource(R.string.b_say_find_sub), center = true)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextLink(stringResource(R.string.b_tap_start), {
                            searching = true
                            speaker.say(context.getString(R.string.b_say_start))
                        })
                    }
                }
            }

            // driving to the destination (before B1)
            else -> {
                CenterColumn {
                    Headline(stringResource(R.string.b_driving_title), center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText(stringResource(R.string.b_driving_sub), center = true)
                }
                // Simulator: stands in for the navigation arrival event (see the TODO at B1)
                Box(Modifier.align(Alignment.BottomEnd)) { SimButton(stringResource(R.string.b_sim_arrive)) { arrived = true } }
            }
        }
    }
}

private fun Modifier.clickableBox(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

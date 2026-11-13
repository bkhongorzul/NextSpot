package com.nextspot.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Driver-facing actions of the offer card: answer buttons (and the "listening" hint).
 * The question itself is written by the card in DemoScreen.kt.
 */
@Composable
fun OfferActions(e: DemoEngine) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (e.listening) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // pulsing dot = microphone is open
                val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
                    0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
                )
                Box(Modifier.size(14.dp).alpha(pulse).background(NsColor.Positive, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text("듣고 있어요. \"네\" 또는 \"아니요\"라고 말해 주세요", fontSize = NsText.Label, color = NsColor.TextTertiary)
            }
        }
        // Narrow cards (portrait half screen): yes/no side by side, "말하기" on its own row below
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tight = maxWidth < 560.dp
            val talk = if (e.listening) "듣는 중" else "말하기"
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledButton("네", { e.answerYes() }, Modifier.weight(1f))
                    BasicButton("아니요", { e.answerNo() }, Modifier.weight(1f))
                    if (!tight) BasicButton(talk, { e.listenForAnswer() }, Modifier.weight(0.8f))
                }
                if (tight) BasicButton(talk, { e.listenForAnswer() }, Modifier.fillMaxWidth(), BtnSize.Small)
            }
        }
    }
}

/** Details panel: which signals came in and how the spot travels (developer view, not for the main screen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalSection(e: DemoEngine, modifier: Modifier = Modifier) {
    val posted = e.spot?.status != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Car A 신호", fontSize = NsText.Label, color = NsColor.TextTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalChip("문", e.door)
            SignalChip("벨트", e.belt)
            SignalChip("내비", e.nav)
            SignalChip("브레이크", e.brake)
        }
        Text("자리 공유 흐름", fontSize = NsText.Label, color = NsColor.TextTertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalChip("Car A 신호", e.door || e.belt || e.nav || e.brake)
            FlowArrow()
            SignalChip("자리 등록", posted)
            FlowArrow()
            SignalChip("Car B 수신", e.offerMeters != null)
        }
    }
}

@Composable
private fun FlowArrow() {
    Text("→", fontSize = NsText.Body, color = NsColor.TextQuaternary, modifier = Modifier.padding(top = 6.dp))
}

/** A step chip: gray dot when waiting, Pleos green dot when it happened. */
@Composable
private fun SignalChip(label: String, on: Boolean) {
    Row(
        Modifier.background(NsColor.ButtonBasic, NsChip).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(if (on) NsColor.Active else NsColor.Gray300, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = NsText.Label, color = if (on) NsColor.Text else NsColor.TextTertiary, maxLines = 1)
    }
}

/** Live event log: what each car and the cloud did, newest first. */
@Composable
fun EventLog(e: DemoEngine, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier
            .fillMaxWidth()
            .background(NsColor.BgSecondary, NsChip)
            .padding(16.dp)
    ) {
        items(e.log) { item ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(item.time, fontSize = NsText.Label, color = NsColor.TextTertiary, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.who, fontSize = NsText.Label, fontWeight = FontWeight.Bold,
                        color = when (item.who) {
                            "Car B" -> NsColor.Positive
                            "클라우드" -> NsColor.Active
                            else -> NsColor.TextSecondary
                        },
                    )
                }
                Text(item.text, fontSize = NsText.Label, color = NsColor.Text)
            }
        }
    }
}

/** Small list: which SDK powers each part (Pleos in the pleos build). */
@Composable
fun SdkInfo(e: DemoEngine) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        e.sdkLines.forEach { (part, sdk) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(part, fontSize = NsText.Label, color = NsColor.TextTertiary, modifier = Modifier.weight(0.45f))
                Text(
                    sdk, fontSize = NsText.Label, modifier = Modifier.weight(0.55f),
                    color = if (sdk.contains("Pleos")) NsColor.Text else NsColor.TextSecondary,
                )
            }
        }
    }
}

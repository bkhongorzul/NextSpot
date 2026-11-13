package com.nextspot.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One lit/unlit chip per step of the data flow: car signals -> cloud -> Car B. */
@Composable
fun SignalPanel(e: DemoEngine, modifier: Modifier = Modifier) {
    val st = e.spot?.status
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("문", e.door, Modifier.weight(1f))
        Chip("벨트", e.belt, Modifier.weight(1f))
        Chip("내비", e.nav, Modifier.weight(1f))
        Chip("브레이크", e.brake, Modifier.weight(1.2f))
        Chip("클라우드", st != null, Modifier.weight(1.2f))
        Chip("Car B", e.offerMeters != null, Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier) {
    Text(
        label, fontSize = NsText.Chip, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (on) NsColor.OnAccent else NsColor.TextDim,
        modifier = modifier
            .background(if (on) NsColor.Accent else NsColor.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        textAlign = TextAlign.Center,
    )
}

/** Live event log: what each car and the cloud did, newest first. */
@Composable
fun EventLog(e: DemoEngine, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier
            .fillMaxWidth()
            .background(NsColor.Bg, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        items(e.log) { item ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(item.time, fontSize = NsText.Small, color = NsColor.TextDim, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(
                    item.who, fontSize = NsText.Small, fontWeight = FontWeight.Bold,
                    color = when (item.who) {
                        "Car A" -> NsColor.CarA
                        "Car B" -> NsColor.Accent
                        "클라우드" -> NsColor.Warn
                        else -> NsColor.TextDim
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(item.text, fontSize = NsText.Small, color = NsColor.Text)
            }
        }
    }
}

/** Car B's voice offer card: shown while the car is asking "갈까요?". */
@Composable
fun VoiceOfferCard(e: DemoEngine) {
    val m = e.offerMeters ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(NsColor.overlay(NsColor.Surface, 0.96f), RoundedCornerShape(18.dp))
            .border(2.dp, NsColor.Accent, RoundedCornerShape(18.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.listening) {
                // pulsing dot = microphone is open
                val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
                    0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
                )
                Box(Modifier.size(14.dp).alpha(pulse).background(NsColor.Accent, CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                if (e.listening) "듣는 중… \"네\" 또는 \"아니요\"라고 말해 주세요" else "Car B 음성 안내",
                fontSize = NsText.Label, color = NsColor.Accent, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${m}m 앞에 자리가 곧 납니다. 갈까요?", fontSize = NsText.Headline, fontWeight = FontWeight.Bold, color = NsColor.Text)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("네", { e.answerYes() }, Modifier.weight(1.4f).height(88.dp), NsText.Answer)
            SecondaryButton("아니요", { e.answerNo() }, Modifier.weight(1.4f).height(88.dp), NsText.Answer)
            SecondaryButton(
                if (e.listening) "듣는 중" else "말하기", { e.listenForAnswer() },
                Modifier.weight(1f).height(88.dp),
            )
        }
    }
}

/** Small list: which SDK powers each part (Pleos in the pleos build). */
@Composable
fun SdkInfo(e: DemoEngine) {
    Column(Modifier.fillMaxWidth().background(NsColor.Bg, RoundedCornerShape(10.dp)).padding(10.dp)) {
        e.sdkLines.forEach { (part, sdk) ->
            Row {
                Text(part, fontSize = NsText.Small, color = NsColor.TextDim, modifier = Modifier.width(150.dp))
                Text(sdk, fontSize = NsText.Small, color = if (sdk.contains("Pleos")) NsColor.Accent else NsColor.Text)
            }
        }
    }
}

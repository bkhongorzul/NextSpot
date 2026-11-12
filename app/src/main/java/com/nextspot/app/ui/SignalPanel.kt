package com.nextspot.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One lit/unlit chip per step of the data flow: car signals -> cloud -> Car B. */
@Composable
fun SignalPanel(e: DemoEngine) {
    val st = e.spot?.status
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("🚪 문", e.door, Modifier.weight(1f))
        Chip("💺 벨트", e.belt, Modifier.weight(1f))
        Chip("🧭 내비", e.nav, Modifier.weight(1f))
        Chip("🅿️ 브레이크", e.brake, Modifier.weight(1.2f))
        Chip("📡 클라우드", st != null, Modifier.weight(1.2f))
        Chip("🚗 Car B", e.offerMeters != null, Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier) {
    val bg = if (on) Color(0xFF0F6E56) else Color(0xFF2C2C2A)
    val fg = if (on) Color(0xFFE1F5EE) else Color(0xFF888780)
    Text(
        label, fontSize = 11.sp, color = fg, maxLines = 1,
        modifier = modifier.background(bg, RoundedCornerShape(8.dp)).padding(vertical = 6.dp, horizontal = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Live event log: what each car and the cloud did, newest first. */
@Composable
fun EventLog(e: DemoEngine, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier
            .fillMaxWidth()
            .heightIn(max = 110.dp)
            .background(Color(0xFF15181D), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        items(e.log) { item ->
            Row {
                Text(item.time, fontSize = 11.sp, color = Color(0xFF5F5E5A), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Text(
                    item.who, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = when (item.who) {
                        "Car A" -> Color(0xFFF0997B)
                        "Car B" -> Color(0xFFAFA9EC)
                        "클라우드" -> Color(0xFF5DCAA5)
                        else -> Color(0xFFB4B2A9)
                    },
                )
                Spacer(Modifier.width(6.dp))
                Text(item.text, fontSize = 11.sp, color = Color(0xFFD3D1C7))
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
            .background(Color(0xFF26215C), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF7F77DD), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("🔊 Car B 음성 안내", fontSize = 12.sp, color = Color(0xFFAFA9EC))
        Spacer(Modifier.height(4.dp))
        Text("${m}m 앞에 자리가 곧 납니다. 갈까요?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { e.answerYes() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75)),
                modifier = Modifier.weight(1f),
            ) { Text("네") }
            OutlinedButton(onClick = { e.answerNo() }, modifier = Modifier.weight(1f)) { Text("아니요") }
            OutlinedButton(onClick = { e.listenForAnswer() }, modifier = Modifier.weight(1f)) {
                Text(if (e.listening) "🎤 듣는 중" else "🎤 말하기")
            }
        }
    }
}

/** Small list: which SDK powers each part (Pleos in the pleos build). */
@Composable
fun SdkInfo(e: DemoEngine) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF15181D), RoundedCornerShape(10.dp)).padding(8.dp)) {
        e.sdkLines.forEach { (part, sdk) ->
            Row {
                Text(part, fontSize = 11.sp, color = Color(0xFF888780), modifier = Modifier.width(100.dp))
                Text(sdk, fontSize = 11.sp, color = if (sdk.contains("Pleos")) Color(0xFF5DCAA5) else Color(0xFFD3D1C7))
            }
        }
    }
}

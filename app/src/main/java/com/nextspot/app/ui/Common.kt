package com.nextspot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleScreen(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NextSpot", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("주차 자리 핸드오버 · Parking Handover", fontSize = 18.sp)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onPick("demo") }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("▶ 한 화면 데모 · One-screen demo", fontSize = 20.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onPick("A") }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("차 A · 떠나는 차 (Car A, leaving)", fontSize = 20.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onPick("B") }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("차 B · 자리 찾는 차 (Car B, searching)", fontSize = 20.sp)
        }
    }
}

/** Big status card at the top of each screen. */
@Composable
fun StatusCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun statusLabel(status: String?): String = when (status) {
    "likely" -> "곧 빌 가능성 · Likely opening"
    "confirmed" -> "출발 확정 · Confirmed"
    "matched" -> "다음 차 배정됨 · Matched"
    "taken" -> "자리 넘김 완료 · Handed over"
    "withdrawn" -> "제안 철회 · Withdrawn"
    else -> "대기 중 · Parked"
}

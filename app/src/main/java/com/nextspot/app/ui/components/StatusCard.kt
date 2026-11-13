package com.nextspot.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Big status card at the top of the Car A / Car B screens. */
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
    "likely" -> "곧 빌 가능성"
    "confirmed" -> "출발 확정"
    "matched" -> "다음 차 배정됨"
    "taken" -> "자리 넘김 완료"
    "withdrawn" -> "제안 철회"
    else -> "주차 중"
}

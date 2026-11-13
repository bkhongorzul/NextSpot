package com.nextspot.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextspot.app.ui.DemoEngine.LogItem
import com.nextspot.app.ui.components.DemoActions
import com.nextspot.app.ui.components.DemoUi
import com.nextspot.app.ui.theme.NsTheme
import com.nextspot.app.ui.DemoEngine.Phase

/**
 * Design previews for Android Studio (Split / Design view). No emulator, no Firebase.
 * They show the final state of each phase; movement and voice need a real run.
 * Sizes: WIDE = app area on Pleos (~1700x1300), HALF = half screen, TALL = portrait half.
 */
private fun sample(phase: Phase, listening: Boolean = false) = DemoUi(
    phase = phase,
    offerMeters = if (phase == Phase.OFFER || phase == Phase.RESERVED) 147 else null,
    credits = 3,
    door = phase != Phase.IDLE && phase != Phase.SEARCHING,
    belt = phase != Phase.IDLE && phase != Phase.SEARCHING,
    nav = phase != Phase.IDLE && phase != Phase.SEARCHING,
    brake = phase == Phase.CONFIRMED || phase == Phase.TAKEN,
    searching = phase != Phase.IDLE,
    accepted = phase == Phase.RESERVED || phase == Phase.CONFIRMED || phase == Phase.TAKEN,
    parked = phase == Phase.TAKEN,
    listening = listening,
    log = listOf(
        LogItem("14:02:11", "Car B", "147m 앞 자리 제안 받음"),
        LogItem("14:02:10", "클라우드", "곧 빌 자리로 등록"),
        LogItem("14:02:08", "Car A", "내비 목적지 설정"),
        LogItem("14:02:05", "Car B", "자리 찾기 시작"),
    ),
    sdkLines = listOf(
        "Car A 신호" to "Pleos Connect SDK", "음성 안내" to "Pleos Connect SDK", "자리 공유" to "Firebase Firestore",
    ),
)

@Composable
private fun Shot(phase: Phase, listening: Boolean = false, record: Boolean = false, signals: Boolean = false) =
    NsTheme { DemoContent(sample(phase, listening), DemoActions(), record, signals) }

// ---- every phase at the wide size ----
@Preview(name = "1 대기", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideIdle() { Shot(Phase.IDLE) }

@Preview(name = "2 탐색", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideSearching() { Shot(Phase.SEARCHING) }

@Preview(name = "3 곧 빔", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideLikely() { Shot(Phase.LIKELY) }

@Preview(name = "4 제안", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideOffer() { Shot(Phase.OFFER) }

@Preview(name = "4b 제안 듣는 중", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideOfferListening() { Shot(Phase.OFFER, listening = true) }

@Preview(name = "5 예약", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideReserved() { Shot(Phase.RESERVED) }

@Preview(name = "6 출발 확정", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideConfirmed() { Shot(Phase.CONFIRMED) }

@Preview(name = "7 주차 완료", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideTaken() { Shot(Phase.TAKEN) }

@Preview(name = "8 취소", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewWideWithdrawn() { Shot(Phase.WITHDRAWN) }


// ---- record, car signals, other window sizes ----
@Preview(name = "기록 열림", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewRecord() { Shot(Phase.LIKELY, record = true) }

@Preview(name = "차 신호 열림", widthDp = 1700, heightDp = 1184)
@Composable
fun PreviewSignals() { Shot(Phase.LIKELY, signals = true) }

@Preview(name = "작은 화면 1280x800", widthDp = 1280, heightDp = 800)
@Composable
fun PreviewSmall() { Shot(Phase.OFFER) }

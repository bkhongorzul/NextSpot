package com.nextspot.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nextspot.app.ui.DemoEngine.LogItem
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
    posted = phase != Phase.IDLE && phase != Phase.SEARCHING,
    listening = listening,
    log = listOf(
        LogItem("14:02:11", "Car B", "자리 수신: 147m 앞 (likely)"),
        LogItem("14:02:10", "클라우드", "자리 등록: 곧 빌 가능성 (likely)"),
        LogItem("14:02:08", "Car A", "내비 목적지 설정 (차 신호)"),
        LogItem("14:02:05", "Car B", "주차 자리 탐색 시작"),
    ),
    sdkLines = listOf(
        "Car A 신호" to "Pleos Connect SDK", "음성 안내" to "Pleos Connect SDK", "자리 공유" to "Firebase Firestore",
    ),
)

@Composable
private fun Shot(phase: Phase, listening: Boolean = false, panel: Boolean = false) =
    NsTheme { DemoContent(sample(phase, listening), DemoActions(), startPanelOpen = panel) }

// ---- every phase at the wide size ----
@Preview(name = "1 대기", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideIdle() { Shot(Phase.IDLE) }

@Preview(name = "2 탐색", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideSearching() { Shot(Phase.SEARCHING) }

@Preview(name = "3 곧 빔", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideLikely() { Shot(Phase.LIKELY) }

@Preview(name = "4 제안", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideOffer() { Shot(Phase.OFFER) }

@Preview(name = "4b 제안 듣는 중", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideOfferListening() { Shot(Phase.OFFER, listening = true) }

@Preview(name = "5 예약", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideReserved() { Shot(Phase.RESERVED) }

@Preview(name = "6 출발 확정", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideConfirmed() { Shot(Phase.CONFIRMED) }

@Preview(name = "7 주차 완료", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideTaken() { Shot(Phase.TAKEN) }

@Preview(name = "8 취소", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWideWithdrawn() { Shot(Phase.WITHDRAWN) }


// ---- details panel and other sizes ----
@Preview(name = "상세 패널 열림", widthDp = 1700, heightDp = 1300)
@Composable
fun PreviewWidePanel() { Shot(Phase.LIKELY, panel = true) }

@Preview(name = "절반 가로 1280 제안", widthDp = 1280, heightDp = 1440)
@Composable
fun PreviewHalfOffer() { Shot(Phase.OFFER) }

@Preview(name = "절반 가로 1280 상세", widthDp = 1280, heightDp = 1440)
@Composable
fun PreviewHalfPanel() { Shot(Phase.OFFER, panel = true) }

@Preview(name = "세로 절반 제안", widthDp = 850, heightDp = 1300)
@Composable
fun PreviewTallOffer() { Shot(Phase.OFFER) }

@Preview(name = "세로 절반 확정", widthDp = 850, heightDp = 1300)
@Composable
fun PreviewTallConfirmed() { Shot(Phase.CONFIRMED) }

@Preview(name = "세로 절반 상세", widthDp = 850, heightDp = 1300)
@Composable
fun PreviewTallPanel() { Shot(Phase.LIKELY, panel = true) }


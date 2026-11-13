package com.nextspot.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Look of the real Car A / Car B screens (design photos in 디자인사진/).
 * One accent (yellow). Cards and buttons are nearly square (10 dp); only status tags are fully round.
 * The one-screen demo keeps using [NsColors].
 */
object NsCar {
    val bg = Color(0xFF0A0A0D)
    val card = Color(0xFF18181D)
    val cardHigh = Color(0xFF202026)
    val border = Color(0xFF2B2B32)

    val text = Color(0xFFFFFFFF)
    val text2 = Color(0xFFA0A0A8)
    val text3 = Color(0xFF66666E)

    val accent = Color(0xFFFFC83A)
    val onAccent = Color(0xFF17140A)
    val accentDim = Color(0x2EFFC83A)      // 18% accent: tag background, mic disc

    val cancel = Color(0xFFD0705F)         // muted red, only for "cancelled"
    val cancelDim = Color(0x26D0705F)

    val shape = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(50)
}

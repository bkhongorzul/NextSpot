package com.nextspot.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NextSpot colors. Source: Pleos Connect design assets / display guide 2.1.5 (dark mode only).
 * See docs/design/DESIGN.md section 2.
 */
object NsColors {
    val screen = Color(0xFF131417)       // map floor
    val appBg = Color(0xFF1D1E21)        // app window
    val surface = Color(0xFF313236)      // secondary buttons, step markers, disabled
    val surfaceHigh = Color(0xFF44464E)  // grey buttons, parking lines, "No"
    val strip = Color(0xFF18191C)        // demo-controls strip
    val popover = Color(0xFF26272B)      // car-signals panel

    val text = Color(0xFFFFFFFF)
    val text2 = Color(0xFFA4A8AE)
    val text3 = Color(0xFF686A72)
    val leavingCar = Color(0xFFE0E1E6)

    val main = Color(0xFFFFC224)         // Pleos regulation yellow: the one accent
    val onMain = Color(0xFF131417)
    val ok = Color(0xFF02C265)           // Pleos informative/active: free, parked, done
    val cancel = Color(0xFFE0574D)       // muted red for the bay and its word
    val cancelIcon = Color(0xFFFF4339)
    val cancelTint = Color(0x29FF4339)   // 16%
}

package com.nextspot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsType
import kotlin.math.min

/**
 * The 1700x1184 app window of DESIGN.md. On a smaller screen only the ratio is scaled
 * (dp and sp together), and the window is centered. System UI is not drawn.
 */
@Composable
fun NsWindow(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(NsColors.appBg)) {
        val scale = min(maxWidth / NsDims.windowW, maxHeight / NsDims.windowH)
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
            Box(Modifier.size(NsDims.windowW, NsDims.windowH).align(Alignment.Center), content = content)
        }
    }
}

/** 112-high title bar: back arrow (80x80) and the title. [trailing] is empty by default. */
@Composable
fun NsTitleBar(title: String, backDescription: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(NsDims.titleBar).padding(start = 24.dp, end = NsDims.sidePad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(80.dp).clip(NsShape.button)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backDescription },
            contentAlignment = Alignment.Center,
        ) { NsIcon(NsPaths.BACK, NsColors.text, 40.dp, strokeWidth = 2.2f, square = true) }
        Spacer(Modifier.width(12.dp))
        Text(title, style = NsType.title, color = NsColors.text)
    }
}

/** A rounded button with a text label (and an optional icon before it). */
@Composable
fun NsButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    height: Dp = NsDims.smallButtonH, bg: Color = NsColors.surface,
    fg: Color = NsColors.text, textStyle: TextStyle = NsType.bodyBold,
    enabled: Boolean = true, padH: Dp = 28.dp, icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.height(height).clip(NsShape.button).background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { icon(); Spacer(Modifier.width(10.dp)) }
        Text(label, style = textStyle, color = fg, maxLines = 1)
    }
}

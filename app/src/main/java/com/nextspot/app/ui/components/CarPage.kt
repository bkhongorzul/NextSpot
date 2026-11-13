package com.nextspot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextspot.app.R
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsTheme
import com.nextspot.app.ui.theme.NsType

/** Page frame of the single-car screens: same window, title bar and look as the demo. */
@Composable
internal fun CarPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    NsTheme {
        NsWindow {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    NsTitleBar(title, stringResource(R.string.demo_back), onBack)
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(start = NsDims.sidePad, end = NsDims.sidePad, bottom = 40.dp),
                        content = content,
                    )
                }
                LiveBadge(Modifier.align(Alignment.TopEnd).padding(end = NsDims.sidePad))
            }
        }
    }
}

/** Top-right tag: this is the real app, reading and writing the live Firestore (not a drawing). */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.height(NsDims.titleBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(NsShape.button).background(NsColors.surface).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(NsColors.ok))
            Text("LIVE · Firestore", style = NsType.tag, color = NsColors.text)
        }
    }
}

/** Big square button (100 high, large type). [primary] = the one yellow button of the screen. */
@Composable
internal fun CarButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    // every button turns yellow while it is pressed, so a touch is clearly answered
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val yellow = primary || pressed
    Row(
        modifier.height(NsDims.bigButtonH).clip(NsShape.button)
            .background(if (yellow) NsColors.main else NsColors.surfaceHigh)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = NsType.button, color = if (yellow) NsColors.onMain else NsColors.text, maxLines = 1)
    }
}

/** A status block: small label, then the state in large type. */
@Composable
internal fun CarCard(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(NsShape.button).background(NsColors.surface).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = NsType.body, color = NsColors.text2)
        Text(body, style = NsType.nowQuestion, color = NsColors.text)
    }
}

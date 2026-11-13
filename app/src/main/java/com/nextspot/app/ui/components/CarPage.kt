package com.nextspot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
            Column(Modifier.fillMaxSize()) {
                NsTitleBar(title, stringResource(R.string.demo_back), onBack)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(start = NsDims.sidePad, end = NsDims.sidePad, bottom = 40.dp),
                    content = content,
                )
            }
        }
    }
}

/** Big square button (100 high, large type). [primary] = the one yellow button of the screen. */
@Composable
internal fun CarButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    NsButton(
        label, onClick, modifier, NsDims.bigButtonH,
        if (primary) NsColors.main else NsColors.surfaceHigh,
        if (primary) NsColors.onMain else NsColors.text,
        NsType.button, padH = 24.dp,
    )
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

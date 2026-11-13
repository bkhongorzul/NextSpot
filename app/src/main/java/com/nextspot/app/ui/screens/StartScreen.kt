package com.nextspot.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nextspot.app.R
import com.nextspot.app.ui.components.NsMic
import com.nextspot.app.ui.components.NsPin
import com.nextspot.app.ui.components.NsTitleBar
import com.nextspot.app.ui.components.NsWindow
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsMotion
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsTheme
import com.nextspot.app.ui.theme.NsType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Start screen (DESIGN.md section 5). [onPick]: "demo" for the one-screen demo, "real" to choose Car A / Car B. */
@Composable
fun StartScreen(onPick: (String) -> Unit) {
    NsTheme {
        NsWindow(NsCar.bg) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(NsDims.titleBar))   // empty title bar
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Logo()
                    Spacer(Modifier.height(72.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StartButton(stringResource(R.string.start_demo)) { onPick("demo") }
                        StartButton(stringResource(R.string.start_real)) { onPick("real") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 56.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    NsMic(NsColors.text2, 30.dp)
                }
            }
        }
    }
}

/** Second step of "Start for real": which car is this? Car A / Car B keep their own screens. */
@Composable
fun RolePickScreen(onPick: (String) -> Unit, onBack: () -> Unit) {
    NsTheme {
        NsWindow(NsCar.bg) {
            Column(Modifier.fillMaxSize()) {
                NsTitleBar(stringResource(R.string.start_real), stringResource(R.string.demo_back), onBack)
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.role_title), style = NsType.nowQuestion, color = NsColors.text)
                    Spacer(Modifier.height(48.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StartButton(stringResource(R.string.role_leaving)) { onPick("A") }
                        StartButton(stringResource(R.string.role_searching)) { onPick("B") }
                    }
                }
                Spacer(Modifier.height(112.dp))
            }
        }
    }
}

@Composable
private fun StartButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.width(860.dp).height(100.dp).clip(NsShape.button).background(NsColors.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = NsType.button, color = NsColors.text, textAlign = TextAlign.Center) }
}

/** "NextSpot" with the P pin at its lower right, and the parking-bay line that draws itself once. */
@Composable
private fun Logo() {
    val h = remember { Animatable(0f) }
    val v = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(350)
        coroutineScope {
            launch { h.animateTo(1f, tween(600, easing = NsMotion.logoLine)) }
            launch {
                delay(550)
                v.animateTo(1f, tween(300, easing = NsMotion.logoLine))
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Text("NextSpot", style = NsType.logo.copy(letterSpacing = (-0.02).em), color = NsColors.text)
            // overlay: not counted when centering the word
            NsPin(32.dp, Modifier.align(Alignment.BottomEnd).offset(x = 38.dp, y = (-14).dp))
        }
        Spacer(Modifier.height(20.dp))
        Canvas(Modifier.size(400.dp, 36.dp)) {
            val t = 6.dp.toPx()
            val c = NsColors.main
            drawLine(c, Offset(0f, size.height - t / 2), Offset(size.width * h.value, size.height - t / 2), t)
            val len = size.height * v.value
            drawLine(c, Offset(t / 2, size.height), Offset(t / 2, size.height - len), t)
            drawLine(c, Offset(size.width - t / 2, size.height), Offset(size.width - t / 2, size.height - len), t)
        }
    }
}

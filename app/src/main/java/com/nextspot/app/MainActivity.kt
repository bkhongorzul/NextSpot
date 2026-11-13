package com.nextspot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.screens.CarAScreen
import com.nextspot.app.ui.screens.CarBScreen
import com.nextspot.app.ui.screens.DemoScreen
import com.nextspot.app.ui.screens.RolePickScreen
import com.nextspot.app.ui.screens.StartScreen
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsTheme

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Platform.speaker(this)
        setContent {
            NsTheme {
                Surface(color = NsColors.appBg) {
                    var role by remember { mutableStateOf<String?>(null) }
                    when (role) {
                        null -> StartScreen(onPick = { role = it })
                        "real" -> RolePickScreen(onPick = { role = it }, onBack = { role = null })
                        "demo" -> DemoScreen(speaker = speaker, onBack = { role = null })
                        "A" -> CarAScreen(speaker = speaker, onBack = { role = "real" })
                        else -> CarBScreen(speaker = speaker, onBack = { role = "real" })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}

package com.nextspot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.CarAScreen
import com.nextspot.app.ui.CarBScreen
import com.nextspot.app.ui.DemoScreen
import com.nextspot.app.ui.RoleScreen

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Platform.speaker(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    var role by remember { mutableStateOf<String?>("demo") }
                    when (role) {
                        null -> RoleScreen(onPick = { role = it })
                        "demo" -> DemoScreen(speaker = speaker, onBack = { role = null })
                        "A" -> CarAScreen(speaker = speaker, onBack = { role = null })
                        else -> CarBScreen(speaker = speaker, onBack = { role = null })
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

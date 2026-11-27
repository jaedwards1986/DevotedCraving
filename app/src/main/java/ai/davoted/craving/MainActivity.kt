package ai.davoted.craving

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ai.davoted.craving.ui.DavotedCravingApp
import ai.davoted.craving.ui.security.PinGate
import ai.davoted.craving.ui.theme.CravingTheme

class MainActivity : ComponentActivity() {
    private var isUnlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        isUnlocked = savedInstanceState?.getBoolean(STATE_UNLOCKED) ?: false

        setContent {
            CravingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isUnlocked) {
                        DavotedCravingApp()
                    } else {
                        PinGate(onUnlocked = { isUnlocked = true })
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_UNLOCKED, isUnlocked)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) isUnlocked = false
    }

    private companion object {
        const val STATE_UNLOCKED = "davotedcraving.unlocked"
    }
}

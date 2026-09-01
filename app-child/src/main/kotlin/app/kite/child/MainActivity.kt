package app.kite.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kite.child.status.ChildStatusScreen
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChildStatusScreen(
                platformVariant = platformServices.variant,
                disableEnforcement = killSwitch.disableEnforcement,
                updateStatus = killSwitch.updateStatus,
            )
        }
    }
}

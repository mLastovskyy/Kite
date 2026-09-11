package app.kite.core.navigation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Destination(val screen: String, val childId: String? = null)

object PendingDestination {
    private const val EXTRA_SCREEN = "app.kite.screen"
    private const val EXTRA_CHILD = "app.kite.child"

    private val state = MutableStateFlow<Destination?>(null)
    val flow: StateFlow<Destination?> = state

    fun offer(intent: Intent?) {
        val screen = intent?.getStringExtra(EXTRA_SCREEN) ?: return
        state.value = Destination(screen, intent.getStringExtra(EXTRA_CHILD))
        intent.removeExtra(EXTRA_SCREEN)
        intent.removeExtra(EXTRA_CHILD)
    }

    fun consume() {
        state.value = null
    }

    fun tap(context: Context, activity: Class<*>, destination: Destination): PendingIntent {
        val intent =
            Intent(context, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_SCREEN, destination.screen)
                .putExtra(EXTRA_CHILD, destination.childId)
        val requestCode = (destination.screen + destination.childId).hashCode()
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

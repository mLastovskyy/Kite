package app.kite.parent.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runs [block] every time the screen comes back to the foreground. The parent looks at these
 * screens for a few seconds at a time, so «когда открыл» is the only moment worth spending
 * network on — nothing is polled while the app is away.
 */
@Composable
fun OnResumeEffect(key: Any?, block: () -> Unit) {
    val current by rememberUpdatedState(block)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, key) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

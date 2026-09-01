package app.kite.core.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.net.ConnectivityObserver

/**
 * Wraps a screen with the app-wide offline banner overlaid at the top (below the status
 * bar), Apple-style. The banner is the only chrome shared by both apps; it appears only
 * while the device has no validated internet and never blocks the content beneath it.
 */
@Composable
fun AppChrome(connectivityObserver: ConnectivityObserver, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val onlineFlow = remember { connectivityObserver.online(scope) }
    val online by onlineFlow.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        OfflineBanner(
            online = online,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
    }
}

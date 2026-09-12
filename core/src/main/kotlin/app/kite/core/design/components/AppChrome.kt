package app.kite.core.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.net.ConnectivityObserver

@Composable
fun AppChrome(connectivityObserver: ConnectivityObserver, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val onlineFlow = remember { connectivityObserver.online(scope) }
    val online by onlineFlow.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(online) { if (online) dismissed = false }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) dismissed = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showBanner = !online && !dismissed
    Column(Modifier.fillMaxSize()) {
        OfflineBanner(visible = showBanner, onDismiss = { dismissed = true })
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (showBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            content()
        }
    }
}

package app.kite.child.enforce

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProtectionState(context: Context) {
    private val prefs = context.getSharedPreferences("protection_state", Context.MODE_PRIVATE)
    private val _released = MutableStateFlow(prefs.getBoolean(KEY_RELEASED, false))

    val released: StateFlow<Boolean> = _released.asStateFlow()

    fun isReleased(): Boolean = _released.value

    fun release() {
        prefs.edit().putBoolean(KEY_RELEASED, true).apply()
        _released.value = true
    }

    fun restore() {
        prefs.edit().putBoolean(KEY_RELEASED, false).apply()
        _released.value = false
    }

    private companion object {
        const val KEY_RELEASED = "released"
    }
}

package app.kite.child.nav

import android.app.PendingIntent
import android.content.Context
import app.kite.child.MainActivity
import app.kite.core.navigation.Destination
import app.kite.core.navigation.PendingDestination

object ChildScreens {
    const val STATUS = "Status"
    const val TASKS = "Tasks"
    const val HEALTH = "Health"
    const val RULES = "Rules"

    fun tap(context: Context, screen: String): PendingIntent =
        PendingDestination.tap(context, MainActivity::class.java, Destination(screen))
}

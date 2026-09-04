package app.kite.parent.location

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

fun deviceCountryCode(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val fromSim = telephony?.simCountryIso?.takeIf { it.length == 2 }
    val fromNetwork = telephony?.networkCountryIso?.takeIf { it.length == 2 }
    val fromLocale = Locale.getDefault().country.takeIf { it.length == 2 }
    return (fromNetwork ?: fromSim ?: fromLocale)?.lowercase()
}

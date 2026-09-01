package app.kite.core.config

import app.kite.core.BuildConfig

/**
 * Supabase project coordinates, injected at build time from CI env or local.properties.
 * The publishable key ships inside the APK on purpose — Postgres RLS is the protection.
 *
 * Offline-first rule: nothing may crash or block when [isConfigured] is false; the server
 * only syncs. First real consumer is M3 (auth + family linking).
 */
object SupabaseConfig {
    const val URL: String = BuildConfig.SUPABASE_URL
    const val PUBLISHABLE_KEY: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val isConfigured: Boolean
        get() = URL.isNotBlank() && PUBLISHABLE_KEY.isNotBlank()
}

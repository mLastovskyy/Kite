package app.kite.parent.di

import org.koin.core.qualifier.named
import org.koin.dsl.module

/** hms-flavor bindings. Flavor-specific handlers (push, App Linking) land here in M2+. */
val flavorModule =
    module {
        single(named("servicesFlavor")) { "HMS" }
    }

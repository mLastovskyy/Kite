package app.kite.parent.di

import org.koin.core.qualifier.named
import org.koin.dsl.module

/** gms-flavor bindings. Flavor-specific handlers (push, install referrer) land here in M2+. */
val flavorModule =
    module {
        single(named("servicesFlavor")) { "GMS" }
    }

package app.kite.parent.di

import app.kite.parent.auth.PinLock
import org.koin.dsl.module

/** Parent-only bindings shared by both flavors. */
val parentModule =
    module {
        single { PinLock(get()) }
    }

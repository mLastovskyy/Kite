package app.kite.child.di

import androidx.room.Room
import app.kite.child.usage.UsageCollector
import app.kite.core.usage.UsageDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Bindings that exist only on the child device: raw usage telemetry and its collector. */
val childModule =
    module {
        single { Room.databaseBuilder(androidContext(), UsageDatabase::class.java, "usage.db").build() }
        single { get<UsageDatabase>().usageDao() }
        single { UsageCollector(androidContext(), get()) }
    }

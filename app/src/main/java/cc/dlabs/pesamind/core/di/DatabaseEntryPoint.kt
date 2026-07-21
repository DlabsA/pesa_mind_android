package cc.dlabs.pesamind.core.di

import cc.dlabs.pesamind.core.database.PesaMindDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Accessor for [PesaMindDatabase] from places Hilt can't constructor- or field-inject
 * into directly (`Application.onCreate()` before any injection point exists, and
 * `BroadcastReceiver`s like `SmsReceiver` — see `EntryPointAccessors.fromApplication`).
 * Deliberately not `@Inject lateinit var` field injection: this Dagger/Hilt version's
 * bundled Kotlin-metadata reader can't parse Kotlin 2.1's metadata format for field
 * injection sites, so `@EntryPoint` (a different codegen path) is used instead.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): PesaMindDatabase
}

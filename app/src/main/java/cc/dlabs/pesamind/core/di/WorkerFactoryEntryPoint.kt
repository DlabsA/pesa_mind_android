package cc.dlabs.pesamind.core.di

import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Accessor for [HiltWorkerFactory] from `PesaMindApp.workManagerConfiguration`
 * (`Configuration.Provider`), which — like [DatabaseEntryPoint] — can't use `@Inject
 * lateinit var` field injection: this Dagger/Hilt version's bundled Kotlin-metadata reader
 * can't parse Kotlin 2.1's metadata format for field injection sites (see ADR-0004 Step 1).
 * `@EntryPoint` is a different codegen path, unaffected.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun workerFactory(): HiltWorkerFactory
}

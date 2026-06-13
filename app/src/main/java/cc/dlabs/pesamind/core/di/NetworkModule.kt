package cc.dlabs.pesamind.core.di

import android.content.Context
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    fun provideApiService(): ApiService {
        return ApiClient.api
    }

    @Singleton
    @Provides
    fun provideNetworkMonitor(
        @ApplicationContext context: Context,
    ): NetworkMonitor {
        return NetworkMonitor(context)
    }
}
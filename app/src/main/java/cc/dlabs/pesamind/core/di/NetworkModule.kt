package cc.dlabs.pesamind.core.di

import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}


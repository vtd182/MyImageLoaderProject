package com.example.myimageloaderproject.modules.home.di

import com.example.myimageloaderproject.modules.home.data.repository.PhotoRepositoryImpl
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(
        impl: PhotoRepositoryImpl
    ): PhotoRepository
}

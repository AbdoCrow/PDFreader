package com.example.camscan.di

import com.example.camscan.data.repository.DefaultDocumentRepository
import com.example.camscan.data.repository.DocumentRepository
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
    abstract fun bindDocumentRepository(
        defaultDocumentRepository: DefaultDocumentRepository
    ): DocumentRepository
}

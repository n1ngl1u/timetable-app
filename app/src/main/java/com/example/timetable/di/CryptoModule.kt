package com.example.timetable.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.example.timetable.data.crypto.CipherEngine
import com.example.timetable.data.crypto.CryptoKeyProvider
import com.example.timetable.data.crypto.KeystoreCipherEngine
import com.example.timetable.data.crypto.KeystoreSecretKeyGenerator
import com.example.timetable.data.crypto.MasterKeySource
import com.example.timetable.data.crypto.SecretKeyGenerator
import com.example.timetable.data.crypto.SecureKeyValueStore
import com.example.timetable.data.crypto.SharedPreferencesKeyValueStore
import java.security.KeyStore
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Provides
    @Singleton
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Provides
    @Singleton
    fun provideCipherEngine(impl: KeystoreCipherEngine): CipherEngine = impl

    @Provides
    @Singleton
    fun provideSecretKeyGenerator(impl: KeystoreSecretKeyGenerator): SecretKeyGenerator = impl

    @Provides
    @Singleton
    fun provideSecureKeyValueStore(impl: SharedPreferencesKeyValueStore): SecureKeyValueStore = impl

    @Provides
    @Singleton
    fun provideMasterKeySource(impl: CryptoKeyProvider): MasterKeySource = impl

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
}

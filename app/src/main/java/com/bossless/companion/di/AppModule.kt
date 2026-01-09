
package com.bossless.companion.di

import com.bossless.companion.data.repository.JobDetailRepository

import android.content.Context
import androidx.room.Room
import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.api.AuthInterceptor
import com.bossless.companion.data.api.DynamicUrlInterceptor
import com.bossless.companion.data.api.TokenAuthenticator
import com.bossless.companion.data.api.UnsafeOkHttpClient
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.db.AppDatabase
import com.bossless.companion.data.local.db.JobDao
import com.bossless.companion.data.local.db.JobDetailDao
import com.bossless.companion.data.local.db.NotificationDao
import com.bossless.companion.data.local.db.PendingUserLocationDao
import com.bossless.companion.data.local.db.TimeEntryDao
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_user_locations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "user_id TEXT NOT NULL, " +
                    "latitude REAL NOT NULL, " +
                    "longitude REAL NOT NULL, " +
                    "accuracy REAL, " +
                    "speed REAL, " +
                    "heading REAL, " +
                    "altitude REAL, " +
                    "distance_delta_meters REAL NOT NULL, " +
                    "recorded_at TEXT NOT NULL, " +
                    "created_at_epoch_ms INTEGER NOT NULL, " +
                    "attempt_count INTEGER NOT NULL DEFAULT 0, " +
                    "last_attempt_epoch_ms INTEGER, " +
                    "last_error TEXT" +
                    ")"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_pending_user_locations_created_at_epoch_ms " +
                    "ON pending_user_locations(created_at_epoch_ms)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideJobDetailRepository(
        apiService: ApiService,
        jobDetailDao: JobDetailDao
    ): JobDetailRepository = JobDetailRepository(apiService, jobDetailDao)

    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SecurePrefs {
        return SecurePrefs(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClientFactory(
        securePrefs: SecurePrefs,
        authInterceptor: AuthInterceptor,
        dynamicUrlInterceptor: DynamicUrlInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClientFactory {
        return OkHttpClientFactory(
            securePrefs,
            authInterceptor,
            dynamicUrlInterceptor,
            tokenAuthenticator
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        factory: OkHttpClientFactory
    ): OkHttpClient {
        return factory.createClient()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        securePrefs: SecurePrefs
    ): RetrofitBuilderProvider {
        return RetrofitBuilderProvider(securePrefs)
    }

    @Provides
    @Singleton
    fun provideApiService(
        retrofitBuilderProvider: RetrofitBuilderProvider,
        okHttpClientFactory: OkHttpClientFactory
    ): ApiService {
        return retrofitBuilderProvider.createApiService(okHttpClientFactory.createClient())
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "companion-db")
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideJobDao(db: AppDatabase): JobDao = db.jobDao()

    @Provides
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideJobDetailDao(db: AppDatabase): JobDetailDao = db.jobDetailDao()

    @Provides
    fun provideTimeEntryDao(db: AppDatabase): TimeEntryDao = db.timeEntryDao()

    @Provides
    fun provideJobDocumentDao(db: AppDatabase): com.bossless.companion.data.local.db.JobDocumentDao = db.jobDocumentDao()

    @Provides
    fun providePendingUserLocationDao(db: AppDatabase): PendingUserLocationDao = db.pendingUserLocationDao()
}

package com.prioritymatrix.app.di

import android.content.Context
import androidx.room.Room
import com.prioritymatrix.app.data.db.AppDatabase
import com.prioritymatrix.app.data.db.Migrations
import com.prioritymatrix.app.notifications.AlarmReminderScheduler
import com.prioritymatrix.app.notifications.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun reminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler
}

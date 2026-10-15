package id.sukiapps.hermes.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sukiapps.hermes.data.local.HermesDao
import id.sukiapps.hermes.data.local.HermesDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): HermesDatabase =
        Room.databaseBuilder(ctx, HermesDatabase::class.java, HermesDatabase.DB_NAME)
            // 4 -> 5: recreate tabel ke DDL kanonis Room (lihat HermesDatabase).
            // Data user disalin utuh; TANPA destructive migration.
            .addMigrations(HermesDatabase.MIGRATION_4_5)
            .build()

    @Provides
    fun provideDao(db: HermesDatabase): HermesDao = db.dao()

    // NOTE: AppPrefs dan ChatRepository memakai @Inject constructor,
    // jadi tidak perlu @Provides di sini (akan duplicate binding).
}

package com.lumen.player.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

const val DATABASE_NAME = "lumen.db"

@Database(
    entities = [
        TrackEntity::class, ArtistEntity::class, TrackArtistEntity::class, AlbumEntity::class, PlaylistEntity::class,
        PlaylistTrackEntity::class, PlayEventEntity::class, DownloadJobEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackArtistDao(): TrackArtistDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun statsDao(): StatsDao
}

// 2: playlists can keep their songs out of the library views
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN hide_songs INTEGER NOT NULL DEFAULT 0")
    }
}

// 3: artists and albums can keep their songs out of the library views too
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE artists ADD COLUMN hide_songs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE albums ADD COLUMN hide_songs INTEGER NOT NULL DEFAULT 0")
    }
}

// Room opens the file on the first query, so a restore can copy the snapshot in before that
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LumenDatabase =
        Room.databaseBuilder(context, LumenDatabase::class.java, DATABASE_NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides fun trackDao(db: LumenDatabase) = db.trackDao()
    @Provides fun trackArtistDao(db: LumenDatabase) = db.trackArtistDao()
    @Provides fun artistDao(db: LumenDatabase) = db.artistDao()
    @Provides fun albumDao(db: LumenDatabase) = db.albumDao()
    @Provides fun playlistDao(db: LumenDatabase) = db.playlistDao()
    @Provides fun playEventDao(db: LumenDatabase) = db.playEventDao()
    @Provides fun downloadJobDao(db: LumenDatabase) = db.downloadJobDao()
    @Provides fun statsDao(db: LumenDatabase) = db.statsDao()
}

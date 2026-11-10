package com.idealplayer.app.di

import android.content.Context
import androidx.room.Room
import com.idealplayer.app.core.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IdealPlayerDatabase {
        return Room.databaseBuilder(
            context,
            IdealPlayerDatabase::class.java,
            "idealplayer.db"
        )
            .addMigrations(
                IdealPlayerDatabase.MIGRATION_1_2,
                IdealPlayerDatabase.MIGRATION_2_3,
                IdealPlayerDatabase.MIGRATION_3_4,
                IdealPlayerDatabase.MIGRATION_4_5,
                IdealPlayerDatabase.MIGRATION_5_6,
                IdealPlayerDatabase.MIGRATION_6_7
            )
            .build()
    }

    @Provides fun providePlaylistDao(db: IdealPlayerDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideChannelDao(db: IdealPlayerDatabase): ChannelDao = db.channelDao()
    @Provides fun provideMovieDao(db: IdealPlayerDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: IdealPlayerDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: IdealPlayerDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideCategoryDao(db: IdealPlayerDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideEpgDao(db: IdealPlayerDatabase): EpgDao = db.epgDao()
    @Provides fun provideWatchHistoryDao(db: IdealPlayerDatabase): WatchHistoryDao = db.watchHistoryDao()
    @Provides fun provideFavoriteDao(db: IdealPlayerDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideMetadataCacheDao(db: IdealPlayerDatabase): MetadataCacheDao = db.metadataCacheDao()
}

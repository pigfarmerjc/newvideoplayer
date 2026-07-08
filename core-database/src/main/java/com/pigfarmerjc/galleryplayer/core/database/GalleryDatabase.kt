package com.pigfarmerjc.galleryplayer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.pigfarmerjc.galleryplayer.core.database.dao.*
import com.pigfarmerjc.galleryplayer.core.database.entity.*
import com.pigfarmerjc.galleryplayer.core.model.MediaType

class DatabaseConverters {
    @TypeConverter
    fun toMediaType(value: String?): MediaType? {
        return value?.let { MediaType.valueOf(it) }
    }

    @TypeConverter
    fun fromMediaType(type: MediaType?): String? {
        return type?.name
    }
}

@Database(
    entities = [
        MediaItemEntity::class,
        FolderEntity::class,
        PlaybackHistoryEntity::class,
        ThumbnailCacheEntity::class,
        StorageVolumeEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun folderDao(): FolderDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun thumbnailCacheDao(): ThumbnailCacheDao
    abstract fun storageVolumeDao(): StorageVolumeDao

    companion object {
        private const val DB_NAME = "gallery_player.db"

        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getDatabase(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    DB_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

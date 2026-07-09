package com.pigfarmerjc.galleryplayer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Migrate media_items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `content_uri` TEXT NOT NULL, 
                        `media_type` TEXT NOT NULL, 
                        `volume_name` TEXT NOT NULL, 
                        `media_store_id` INTEGER, 
                        `relative_path` TEXT, 
                        `display_name` TEXT NOT NULL, 
                        `mime_type` TEXT, 
                        `file_size` INTEGER NOT NULL, 
                        `duration_ms` INTEGER, 
                        `width` INTEGER, 
                        `height` INTEGER, 
                        `rotation_degrees` INTEGER, 
                        `date_added_epoch_seconds` INTEGER, 
                        `date_modified_epoch_seconds` INTEGER, 
                        `date_taken_epoch_millis` INTEGER, 
                        `video_codec` TEXT, 
                        `audio_codec` TEXT, 
                        `audio_sample_format` TEXT, 
                        `audio_sample_rate` INTEGER, 
                        `audio_channels` INTEGER, 
                        `frame_rate` REAL, 
                        `bitrate` INTEGER, 
                        `is_hdr` INTEGER NOT NULL DEFAULT 0, 
                        `is_gif` INTEGER NOT NULL DEFAULT 0, 
                        `is_favorite` INTEGER NOT NULL DEFAULT 0, 
                        `is_hidden` INTEGER NOT NULL DEFAULT 0, 
                        `scan_state` TEXT NOT NULL DEFAULT 'SCANNED', 
                        `last_error` TEXT
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO `media_items_new` (
                        `content_uri`, `media_type`, `volume_name`, `media_store_id`, `relative_path`, `display_name`, 
                        `file_size`, `duration_ms`, `width`, `height`, `date_modified_epoch_seconds`, `scan_state`
                    )
                    SELECT 
                        `content_uri`, 
                        COALESCE(`media_type`, 'VIDEO'), 
                        COALESCE(`volume_name`, 'external_primary'), 
                        `media_store_id`, 
                        `relative_path`, 
                        COALESCE(`display_name`, 'unknown'), 
                        COALESCE(`size_bytes`, 0), 
                        `duration_ms`, 
                        `width`, 
                        `height`, 
                        `date_modified`,
                        'SCANNED'
                    FROM `media_items`
                """)
                
                db.execSQL("DROP TABLE `media_items`")
                db.execSQL("ALTER TABLE `media_items_new` RENAME TO `media_items`")
                
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_items_content_uri` ON `media_items` (`content_uri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_volume_name_media_store_id_media_type` ON `media_items` (`volume_name`, `media_store_id`, `media_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_relative_path` ON `media_items` (`relative_path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_date_modified_epoch_seconds` ON `media_items` (`date_modified_epoch_seconds`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_media_type` ON `media_items` (`media_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_is_favorite` ON `media_items` (`is_favorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_is_hidden` ON `media_items` (`is_hidden`)")

                // 2. Migrate folders
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `folders_new` (
                        `folder_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `volume_name` TEXT NOT NULL, 
                        `relative_path` TEXT NOT NULL, 
                        `display_name` TEXT NOT NULL, 
                        `media_count` INTEGER NOT NULL DEFAULT 0, 
                        `video_count` INTEGER NOT NULL DEFAULT 0, 
                        `image_count` INTEGER NOT NULL DEFAULT 0, 
                        `total_size` INTEGER NOT NULL DEFAULT 0, 
                        `primary_cover_media_id` INTEGER, 
                        `latest_modified` INTEGER NOT NULL DEFAULT 0, 
                        `is_favorite` INTEGER NOT NULL DEFAULT 0, 
                        `is_hidden` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO `folders_new` (`volume_name`, `relative_path`, `display_name`, `latest_modified`)
                    SELECT 'external_primary', `path`, `display_name`, `date_modified` FROM `folders`
                """)
                
                db.execSQL("DROP TABLE `folders`")
                db.execSQL("ALTER TABLE `folders_new` RENAME TO `folders`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_volume_name_relative_path` ON `folders` (`volume_name`, `relative_path`)")

                // 3. Migrate playback_history
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_history_new` (
                        `media_id` INTEGER PRIMARY KEY NOT NULL, 
                        `position_ms` INTEGER NOT NULL, 
                        `duration_ms` INTEGER NOT NULL, 
                        `last_played_at` INTEGER NOT NULL, 
                        `completed` INTEGER NOT NULL, 
                        `play_count` INTEGER NOT NULL, 
                        `preferred_speed` REAL NOT NULL, 
                        FOREIGN KEY(`media_id`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO `playback_history_new` (`media_id`, `position_ms`, `duration_ms`, `last_played_at`, `completed`, `play_count`, `preferred_speed`)
                    SELECT 
                        m.id, 
                        h.playback_position_ms, 
                        COALESCE(m.duration_ms, 0), 
                        h.last_played_time, 
                        h.finished, 
                        1, 
                        1.0
                    FROM `playback_history` h
                    INNER JOIN `media_items` m ON m.content_uri = h.content_uri
                """)
                
                db.execSQL("DROP TABLE `playback_history`")
                db.execSQL("ALTER TABLE `playback_history_new` RENAME TO `playback_history`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_last_played_at` ON `playback_history` (`last_played_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_completed` ON `playback_history` (`completed`)")

                // 4. Migrate thumbnail_cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `thumbnail_cache_new` (
                        `media_id` INTEGER NOT NULL, 
                        `thumbnail_type` TEXT NOT NULL, 
                        `width` INTEGER NOT NULL, 
                        `height` INTEGER NOT NULL, 
                        `frame_position_ms` INTEGER NOT NULL, 
                        `media_modified_time` INTEGER NOT NULL, 
                        `cache_path` TEXT NOT NULL, 
                        `generation_state` TEXT NOT NULL, 
                        `last_access_time` INTEGER NOT NULL, 
                        PRIMARY KEY(`media_id`, `thumbnail_type`, `width`, `height`, `frame_position_ms`, `media_modified_time`), 
                        FOREIGN KEY(`media_id`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                
                db.execSQL("""
                    INSERT INTO `thumbnail_cache_new` (
                        `media_id`, `thumbnail_type`, `width`, `height`, `frame_position_ms`, `media_modified_time`, 
                        `cache_path`, `generation_state`, `last_access_time`
                    )
                    SELECT 
                        m.id, 
                        'COVER_MEDIUM', 
                        0, 
                        0, 
                        0, 
                        COALESCE(m.date_modified_epoch_seconds, 0), 
                        t.thumbnail_path, 
                        'SUCCESS', 
                        t.last_accessed
                    FROM `thumbnail_cache` t
                    INNER JOIN `media_items` m ON m.content_uri = t.content_uri
                """)
                
                db.execSQL("DROP TABLE `thumbnail_cache`")
                db.execSQL("ALTER TABLE `thumbnail_cache_new` RENAME TO `thumbnail_cache`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_thumbnail_cache_media_id` ON `thumbnail_cache` (`media_id`)")
            }
        }

        @Volatile
        private var INSTANCE: GalleryDatabase? = null

        fun getDatabase(context: Context): GalleryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GalleryDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.pigfarmerjc.galleryplayer.core.storage.util

import com.pigfarmerjc.galleryplayer.core.model.MediaType

object MimeMapper {

    fun map(mimeType: String?, displayName: String?): MediaType? {
        val nameLower = displayName?.lowercase() ?: ""
        
        // 1. GIF mapping check (GIF MIME is image/gif OR extension is .gif)
        if (mimeType == "image/gif" || nameLower.endsWith(".gif")) {
            return MediaType.GIF
        }

        // 2. Map standard MIMEs
        if (mimeType != null) {
            when {
                mimeType.startsWith("video/") -> return MediaType.VIDEO
                mimeType.startsWith("image/") -> return MediaType.IMAGE
                mimeType.startsWith("audio/") -> return MediaType.AUDIO
            }
        }

        // 3. Fallback to extensions if MIME is unknown/empty
        return when {
            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") ||
            nameLower.endsWith(".avi") || nameLower.endsWith(".3gp") || nameLower.endsWith(".ts") ||
            nameLower.endsWith(".mov") || nameLower.endsWith(".flv") -> MediaType.VIDEO

            nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
            nameLower.endsWith(".webp") || nameLower.endsWith(".bmp") -> MediaType.IMAGE

            nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".ogg") ||
            nameLower.endsWith(".m4a") || nameLower.endsWith(".aac") || nameLower.endsWith(".flac") -> MediaType.AUDIO

            else -> null
        }
    }
}

package com.pigfarmerjc.galleryplayer.core.storage.util

import com.pigfarmerjc.galleryplayer.core.model.MediaType

object MimeMapper {

    fun map(mimeType: String?, displayName: String?): MediaType? {
        // Normalize: lowercase and strip MIME type parameters (e.g. "image/gif; charset=utf-8" -> "image/gif")
        val mimeClean = mimeType?.lowercase()?.substringBefore(';')?.trim()
        val nameLower = displayName?.trim()?.lowercase() ?: ""

        // 1. GIF mapping check (GIF MIME is image/gif OR extension is .gif)
        if (mimeClean == "image/gif" || nameLower.endsWith(".gif")) {
            return MediaType.GIF
        }

        // 2. Map standard MIMEs (case-insensitive after normalization)
        if (mimeClean != null) {
            when {
                mimeClean.startsWith("video/") -> return MediaType.VIDEO
                mimeClean.startsWith("image/") -> return MediaType.IMAGE
                mimeClean.startsWith("audio/") -> return MediaType.AUDIO
            }
        }

        // 3. Fallback to extensions if MIME is unknown/empty
        return when {
            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".webm") ||
            nameLower.endsWith(".avi") || nameLower.endsWith(".3gp") || nameLower.endsWith(".ts") ||
            nameLower.endsWith(".mov") || nameLower.endsWith(".flv") || nameLower.endsWith(".wmv") ||
            nameLower.endsWith(".m4v") || nameLower.endsWith(".rmvb") || nameLower.endsWith(".rm") ||
            nameLower.endsWith(".vob") || nameLower.endsWith(".ogv") || nameLower.endsWith(".divx") ||
            nameLower.endsWith(".mpg") || nameLower.endsWith(".mpeg") || nameLower.endsWith(".m2ts") ||
            nameLower.endsWith(".mts") || nameLower.endsWith(".asf") || nameLower.endsWith(".f4v") -> MediaType.VIDEO

            nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
            nameLower.endsWith(".webp") || nameLower.endsWith(".bmp") || nameLower.endsWith(".heic") ||
            nameLower.endsWith(".heif") || nameLower.endsWith(".avif") || nameLower.endsWith(".tiff") ||
            nameLower.endsWith(".tif") || nameLower.endsWith(".dng") || nameLower.endsWith(".raw") ||
            nameLower.endsWith(".cr2") || nameLower.endsWith(".nef") || nameLower.endsWith(".arw") -> MediaType.IMAGE

            nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".ogg") ||
            nameLower.endsWith(".m4a") || nameLower.endsWith(".aac") || nameLower.endsWith(".flac") ||
            nameLower.endsWith(".wma") || nameLower.endsWith(".opus") || nameLower.endsWith(".aiff") ||
            nameLower.endsWith(".aif") || nameLower.endsWith(".amr") || nameLower.endsWith(".ape") -> MediaType.AUDIO

            else -> null
        }
    }
}

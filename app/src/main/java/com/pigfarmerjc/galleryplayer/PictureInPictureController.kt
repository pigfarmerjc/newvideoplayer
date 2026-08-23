package com.pigfarmerjc.galleryplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import com.pigfarmerjc.galleryplayer.core.player.api.PlaybackState
import com.pigfarmerjc.galleryplayer.core.player.api.VideoSize

enum class PipCommand { PREVIOUS, TOGGLE_PLAYBACK, NEXT }

const val ACTION_PIP_CONTROL = "com.pigfarmerjc.galleryplayer.action.PIP_CONTROL"
const val EXTRA_PIP_COMMAND = "pip_command"

fun isActivelyPlaying(state: PlaybackState): Boolean =
    state == PlaybackState.Playing || state == PlaybackState.Buffering || state == PlaybackState.Opening

fun updatePictureInPictureParams(
    activity: ComponentActivity,
    videoSize: VideoSize?,
    playbackState: PlaybackState,
    playlistSize: Int,
    title: String
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    activity.setPictureInPictureParams(
        createPictureInPictureParams(activity, videoSize, playbackState, playlistSize, title)
    )
}

fun enterPictureInPicture(
    activity: ComponentActivity,
    videoSize: VideoSize?,
    playbackState: PlaybackState,
    playlistSize: Int,
    title: String
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val params = createPictureInPictureParams(activity, videoSize, playbackState, playlistSize, title)
    return activity.enterPictureInPictureMode(params)
}

private fun createPictureInPictureParams(
    context: Context,
    videoSize: VideoSize?,
    playbackState: PlaybackState,
    playlistSize: Int,
    title: String
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    videoSize?.let { size ->
        pipAspectRatio(size.width, size.height)?.let { ratio ->
            builder.setAspectRatio(safeSystemPipRatio(ratio))
        }
    }

    val playing = isActivelyPlaying(playbackState)
    val actions = buildList {
        if (playlistSize > 1) add(pipRemoteAction(context, PipCommand.PREVIOUS, "上一个", android.R.drawable.ic_media_previous, 1))
        add(
            pipRemoteAction(
                context,
                PipCommand.TOGGLE_PLAYBACK,
                if (playing) "暂停" else "播放",
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                2
            )
        )
        if (playlistSize > 1) add(pipRemoteAction(context, PipCommand.NEXT, "下一个", android.R.drawable.ic_media_next, 3))
    }
    builder.setActions(actions)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setSeamlessResizeEnabled(true)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        builder.setTitle(title)
        builder.setSubtitle("GalleryPlayer")
    }
    return builder.build()
}

private fun safeSystemPipRatio(ratio: PipAspectRatio): Rational {
    val value = ratio.width.toDouble() / ratio.height.toDouble()
    return when {
        value > 2.39 -> Rational(239, 100)
        value < 1.0 / 2.39 -> Rational(100, 239)
        else -> Rational(ratio.width, ratio.height)
    }
}

private fun pipRemoteAction(
    context: Context,
    command: PipCommand,
    label: String,
    iconResource: Int,
    requestCode: Int
): RemoteAction {
    val intent = Intent(ACTION_PIP_CONTROL)
        .setPackage(context.packageName)
        .putExtra(EXTRA_PIP_COMMAND, command.name)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return RemoteAction(Icon.createWithResource(context, iconResource), label, label, pendingIntent)
}

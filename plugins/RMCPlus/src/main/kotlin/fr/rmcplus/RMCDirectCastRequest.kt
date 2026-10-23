package fr.rmcplus

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata

/** A bounded LOAD request: the standard receiver does not need CloudStream's full episode list. */
internal object RMCDirectCastRequest {
    fun build(url: String, title: String, positionMs: Long, contentType: String = "application/dash+xml"): MediaLoadRequestData {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, title.take(512))
        }
        val media = MediaInfo.Builder(url).setContentType(contentType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
        return MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true)
            .setCurrentTime(positionMs.coerceAtLeast(0)).build()
    }
}

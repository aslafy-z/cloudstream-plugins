package fr.t18

import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata

/** A bounded LOAD request: the standard receiver does not need CloudStream's full episode list. */
internal object T18DirectCastRequest {
    fun build(url: String, title: String, positionMs: Long, fragmented: Boolean): MediaLoadRequestData {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, title.take(512))
        }
        val media = MediaInfo.Builder(url).setContentType("application/x-mpegURL")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata)
            .apply { if (fragmented) setHlsSegmentFormat(HlsSegmentFormat.FMP4).setHlsVideoSegmentFormat(HlsVideoSegmentFormat.FMP4) }
            .build()
        return MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true)
            .setCurrentTime(positionMs.coerceAtLeast(0)).build()
    }
}

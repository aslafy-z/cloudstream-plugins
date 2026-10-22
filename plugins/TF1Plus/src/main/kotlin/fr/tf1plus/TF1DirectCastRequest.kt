package fr.tf1plus

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack

/** A bounded LOAD request: the standard receiver does not need CloudStream's full episode list. */
internal object TF1DirectCastRequest {
    fun build(url: String, title: String, positionMs: Long, subtitles: List<String>): MediaLoadRequestData {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, title.take(512))
        }
        val tracks = subtitles.take(8).mapIndexed { index, subtitle ->
            MediaTrack.Builder(index.toLong() + 1, MediaTrack.TYPE_TEXT)
                .setContentId(subtitle).setContentType("text/vtt")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES).setName("French").setLanguage("fr").build()
        }
        val media = MediaInfo.Builder(url).setContentType("application/dash+xml")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata).setMediaTracks(tracks).build()
        return MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true)
            .setCurrentTime(positionMs.coerceAtLeast(0)).build()
    }
}

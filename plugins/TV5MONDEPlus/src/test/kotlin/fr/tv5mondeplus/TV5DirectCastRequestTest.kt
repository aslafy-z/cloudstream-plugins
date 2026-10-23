package fr.tv5mondeplus

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TV5DirectCastRequestTest {
    @Test fun `Cast receives DASH with resume position and valid subtitle tracks`() {
        val request = TV5DirectCastRequest.build("http://192.0.2.1/session/manifest.mpd", "Replay", 42_000, listOf("https://cdn.test/fr.vtt"))
        assertEquals(42_000, request.currentTime)
        assertEquals(true, request.autoplay)
        val media = request.mediaInfo!!
        assertEquals("application/dash+xml", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, media.streamType)
        assertEquals("http://192.0.2.1/session/manifest.mpd", media.contentId)
        assertNull(media.customData)
        val track = media.mediaTracks!!.single()
        assertEquals("text/vtt", track.contentType)
        assertEquals(MediaTrack.SUBTYPE_SUBTITLES, track.subtype)
        assertEquals("fr", track.language)
        assertEquals("https://cdn.test/fr.vtt", track.contentId)
    }

    @Test fun `request remains bounded and cannot seek to negative time`() {
        val request = TV5DirectCastRequest.build("http://192.0.2.1/manifest.mpd", "x".repeat(100_000), -1, List(100) { "https://cdn.test/$it.vtt" })
        assertEquals(0, request.currentTime)
        assertEquals(512, request.mediaInfo!!.metadata!!.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)!!.length)
        assertEquals(8, request.mediaInfo!!.mediaTracks!!.size)
    }
}

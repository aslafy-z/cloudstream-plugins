package fr.novo19

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NovoDirectCastRequestTest {
    @Test fun `Cast receives DASH with resume position`() {
        val request = NovoDirectCastRequest.build("http://192.0.2.1/session/manifest.mpd", "Replay", 42_000)
        assertEquals(42_000, request.currentTime)
        assertEquals(true, request.autoplay)
        val media = request.mediaInfo!!
        assertEquals("application/dash+xml", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, media.streamType)
        assertEquals("http://192.0.2.1/session/manifest.mpd", media.contentId)
        assertNull(media.customData)
        assertTrue(media.mediaTracks.isNullOrEmpty())
    }

    @Test fun `request remains bounded and cannot seek to negative time`() {
        val request = NovoDirectCastRequest.build("http://192.0.2.1/manifest.mpd", "x".repeat(100_000), -1)
        assertEquals(0, request.currentTime)
        assertEquals(512, request.mediaInfo!!.metadata!!.getString(MediaMetadata.KEY_TITLE)!!.length)
    }
}

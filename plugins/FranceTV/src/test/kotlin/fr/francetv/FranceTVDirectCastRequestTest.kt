package fr.francetv

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FranceTVDirectCastRequestTest {
    @Test fun `Cast receives DASH with resume position`() {
        val request = FranceTVDirectCastRequest.build("http://192.0.2.1/session/manifest.mpd", true, "Replay", 42_000)
        assertEquals(42_000, request.currentTime)
        assertEquals(true, request.autoplay)
        val media = request.mediaInfo!!
        assertEquals("application/dash+xml", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, media.streamType)
        assertEquals("http://192.0.2.1/session/manifest.mpd", media.contentId)
        assertNull(media.customData)
    }

    @Test fun `unprotected HLS is announced with its own content type`() {
        val media = FranceTVDirectCastRequest.build("https://cdn.test/replay.m3u8", false, "Replay", 0).mediaInfo!!
        assertEquals("application/x-mpegURL", media.contentType)
    }

    @Test fun `request remains bounded and cannot seek to negative time`() {
        val request = FranceTVDirectCastRequest.build("http://192.0.2.1/manifest.mpd", true, "x".repeat(100_000), -1)
        assertEquals(0, request.currentTime)
        assertEquals(512, request.mediaInfo!!.metadata!!.getString(MediaMetadata.KEY_TITLE)!!.length)
    }
}

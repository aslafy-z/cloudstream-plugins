package fr.rmcplus

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RMCDirectCastRequestTest {
    @Test fun `Cast receives DASH with resume position`() {
        val request = RMCDirectCastRequest.build("http://192.0.2.1/session/manifest.mpd", "Replay", 42_000)
        assertEquals(42_000, request.currentTime)
        assertEquals(true, request.autoplay)
        val media = request.mediaInfo!!
        assertEquals("application/dash+xml", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, media.streamType)
        assertEquals("http://192.0.2.1/session/manifest.mpd", media.contentId)
        assertNull(media.customData)
    }

    @Test fun `request remains bounded and cannot seek to negative time`() {
        val request = RMCDirectCastRequest.build("https://cdn.test/replay.m3u8", "x".repeat(100_000), -1, "application/x-mpegurl")
        assertEquals(0, request.currentTime)
        assertEquals("application/x-mpegurl", request.mediaInfo!!.contentType)
        assertEquals(512, request.mediaInfo!!.metadata!!.getString(MediaMetadata.KEY_TITLE)!!.length)
    }
}

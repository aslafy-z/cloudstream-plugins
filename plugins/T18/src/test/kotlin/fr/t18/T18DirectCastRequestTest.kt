package fr.t18

import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class T18DirectCastRequestTest {
    @Test fun `Cast receives fragmented HLS with resume position and no sidecar tracks`() {
        val request = T18DirectCastRequest.build("https://cdn.test/video.m3u8", "Replay", 42_000, true)
        assertEquals(42_000, request.currentTime)
        assertEquals(true, request.autoplay)
        val media = request.mediaInfo!!
        assertEquals("application/x-mpegURL", media.contentType)
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, media.streamType)
        assertEquals("https://cdn.test/video.m3u8", media.contentId)
        assertEquals(HlsSegmentFormat.FMP4, media.hlsSegmentFormat)
        assertEquals(HlsVideoSegmentFormat.FMP4, media.hlsVideoSegmentFormat)
        assertTrue(media.mediaTracks.isNullOrEmpty())
        assertNull(media.customData)
    }

    @Test fun `MPEG-TS HLS keeps receiver defaults and request stays bounded`() {
        val request = T18DirectCastRequest.build("https://cdn.test/video.m3u8", "x".repeat(100_000), -1, false)
        assertEquals(0, request.currentTime)
        assertNull(request.mediaInfo!!.hlsSegmentFormat)
        assertNull(request.mediaInfo!!.hlsVideoSegmentFormat)
        assertEquals(512, request.mediaInfo!!.metadata!!.getString(MediaMetadata.KEY_TITLE)!!.length)
    }
}

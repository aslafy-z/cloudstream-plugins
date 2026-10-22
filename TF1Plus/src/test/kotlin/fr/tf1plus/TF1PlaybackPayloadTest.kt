package fr.tf1plus

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TF1PlaybackPayloadTest {
    @Test fun parsesStreamAndLicenseHeaders() {
        val body = JSONObject("""{"delivery":{
          "code":200,
          "url":"https://example.org/master.mpd",
          "drms":[{"url":"https://drm-wide.tf1.fr/proxy?id=v1",
            "h":[{"k":"Authorization","v":"Bearer private"}]}]
        }}""")
        val stream = TF1PlaybackPayload.parse(body, "v1")
        assertEquals("https://example.org/master.mpd", stream.manifest)
        assertEquals("https://drm-wide.tf1.fr/proxy?id=v1", stream.licenseUrl)
        assertEquals("Bearer private", stream.licenseHeaders["Authorization"])
    }

    @Test fun usesFallbackLicenseEndpoint() {
        val stream = TF1PlaybackPayload.parse(JSONObject("""{"delivery":{
            "code":200,"url":"https://example.org/v1.mpd"}}"""), "A+B")
        assertEquals("https://drm-wide.tf1.fr/proxy?id=A%2BB", stream.licenseUrl)
    }

    @Test(expected = IllegalStateException::class)
    fun refusesUnavailableStreams() {
        TF1PlaybackPayload.parse(JSONObject("""{"delivery":{"code":403}}"""), "v")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesNonHttpsManifest() {
        TF1PlaybackPayload.parse(JSONObject("""{"delivery":{
            "code":200,"url":"http://example.org/untrusted.mpd"}}"""), "v")
    }
}

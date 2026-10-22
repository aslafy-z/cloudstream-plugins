package fr.m6plus.api

import org.junit.Assert.*
import org.junit.Test

class M6AuthClientTest {
    private fun client(response: String) = M6AuthClient(object : M6ApiClient() {
        override fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String>) = response
        override fun get(url: String, headers: Map<String, String>, cacheMs: Long): String {
            assertEquals("signature", headers["x-auth-gigya-signature"])
            assertEquals("123", headers["x-auth-gigya-signature-timestamp"])
            assertEquals("device", headers["x-auth-device-id"])
            return "{\"token\":\"jwt\"}"
        }
    })

    @Test fun `JSON and variable JSONP callbacks both exchange Gigya identity`() {
        val json = """{"UID":"user","UIDSignature":"signature","signatureTimestamp":123,"errorCode":0}"""
        for (response in listOf(json, "callback_123($json);")) {
            val session = client(response).login("email", "password", "device")
            assertEquals("user", session.uid)
            assertEquals("jwt", session.jwt)
            assertEquals("signature", session.signature)
            assertEquals(123L, session.signatureTimestamp)
        }
    }

    @Test fun `Gigya login error is not treated as a session`() {
        assertThrows(IllegalStateException::class.java) {
            client("{\"errorCode\":403042}").login("email", "password", "device")
        }
    }
}

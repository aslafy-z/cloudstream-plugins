package fr.tf1plus

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TF1AuthPayloadTest {
    @Test fun parsesSuccessfulGigyaResponse() {
        val login = JSONObject("""{"errorCode":0,"userInfo":{
            "UID":"user-id","UIDSignature":"signed","signatureTimestamp":1234567890}}""")
        val result = TF1AuthPayload.parseLogin(login)
        assertEquals("user-id", result.uid)
        assertEquals("signed", result.signature)
        assertEquals(1234567890L, result.timestamp)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsInvalidCredentials() {
        TF1AuthPayload.parseLogin(JSONObject("""{"errorCode":403042}"""))
    }

    @Test(expected = IllegalStateException::class)
    fun refusesUnsignedIdentities() {
        TF1AuthPayload.parseLogin(JSONObject("""{"errorCode":0,"userInfo":{"UID":"x"}}"""))
    }
}

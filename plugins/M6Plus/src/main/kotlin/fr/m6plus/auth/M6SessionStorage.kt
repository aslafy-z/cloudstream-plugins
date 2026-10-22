package fr.m6plus.auth

import fr.m6plus.api.M6Session

internal class M6StoredAccount(val email: String, val password: String, val session: M6Session)

internal interface M6SessionStorage {
    val deviceId: String
    fun read(): M6StoredAccount?
    fun save(account: M6StoredAccount)
    fun clear()
}

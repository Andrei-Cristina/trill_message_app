package org.message.trill.session.storage

import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.UserRecord

expect class SessionStorage {
    fun getIdentityKey(): IdentityKey?
    fun storeIdentityKey(identityKey: IdentityKey)

    fun getPreKey(id: Int): PreKey?
    fun storePreKey(preKey: PreKey)
    fun removePreKey(id: Int)

    fun getSignedPreKey(): SignedPreKey?
    fun storeSignedPreKey(signedPreKey: SignedPreKey)
    fun removeSignedPreKey()

    fun getOneTimePreKey(): PreKey
    fun storeOneTimePreKeys(preKeys: List<PreKey>)
    fun removeOneTimePreKey(id: Int = 0)

    fun loadUserRecords(): Map<out String, UserRecord>
    fun saveUserRecords(userRecords: MutableMap<String, UserRecord>)

    fun saveDeviceRecord(user:String, record:DeviceRecord)

    fun loadUserEmail(): String
    fun loadDeviceId(): String
    fun setClientInfo(userEmail: String, deviceId: String)
}
package org.message.trill.session.storage

import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.session.sesame.UserRecord

actual class SessionStorage {
    actual fun getIdentityKey(): IdentityKey? {
        TODO("Not yet implemented")
    }

    actual fun storeIdentityKey(identityKey: IdentityKey) {
    }

    actual fun getPreKey(id: Int): PreKey? {
        TODO("Not yet implemented")
    }

    actual fun storePreKey(preKey: PreKey) {
    }

    actual fun removePreKey(id: Int) {
    }

    actual fun getSignedPreKey(): SignedPreKey? {
        TODO("Not yet implemented")
    }

    actual fun storeSignedPreKey(signedPreKey: SignedPreKey) {
    }

    actual fun removeSignedPreKey() {
    }

    actual fun getOneTimePreKey(): PreKey {
        TODO("Not yet implemented")
    }

    actual fun storeOneTimePreKeys(preKeys: List<PreKey>) {
    }

    actual fun removeOneTimePreKey(id: Int) {
    }

    actual fun loadUserRecords(): Map<out String, UserRecord> {
        TODO("Not yet implemented")
    }

    actual fun saveUserRecords(userRecords: MutableMap<String, UserRecord>) {
    }

    actual fun loadUserEmail(): String {
        TODO("Not yet implemented")
    }

    actual fun loadDeviceId(): String {
        TODO("Not yet implemented")
    }

}
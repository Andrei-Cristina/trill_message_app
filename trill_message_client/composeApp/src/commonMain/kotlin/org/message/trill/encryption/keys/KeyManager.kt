package org.message.trill.encryption.keys

import org.message.trill.session.storage.SessionStorage


class KeyManager(private val storage: SessionStorage) {
    //TODO "Add more key management operations"

    fun generateIdentityKey(): IdentityKey {
        val key = IdentityKey.generate()

        //storage.storeIdentityKey(key)

        return key
    }

    fun getIdentityKey(): IdentityKey = storage.getIdentityKey()!!

    fun generateSignedPreKey(): SignedPreKey {
        val key = SignedPreKey.generate()
        //storage.storeSignedPreKey(key)

        return key
    }

    fun getSignedPreKey(): SignedPreKey = storage.getSignedPreKey()!!

    fun generateOneTimePreKeys(count: Int): List<PreKey>{
        val keys = (1..count).map { PreKey.generate(it) }

        //storage.storeOneTimePreKeys(keys)

        return keys
    }

    fun getOnetimePreKey():PreKey = storage.getOneTimePreKey()

    fun deleteOnetimePreKey(id: Int){
        storage.removeOneTimePreKey(id)
    }
}
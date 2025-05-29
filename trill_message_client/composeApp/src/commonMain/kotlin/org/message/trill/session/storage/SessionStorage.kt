package org.message.trill.session.storage

import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.encryption.utils.models.DebugInfo
import org.message.trill.messaging.models.LocalDbMessage
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.UserRecord

expect class SessionStorage {
    fun getIdentityKey(userEmail: String): IdentityKey?
    fun storeIdentityKey(userEmail: String, identityKey: IdentityKey)

    fun getPreKey(userEmail: String, id: Int): PreKey?
    fun storePreKey(userEmail: String, preKey: PreKey)
    fun removePreKey(userEmail: String, id: Int)

    fun getSignedPreKey(userEmail: String): SignedPreKey?
    fun storeSignedPreKey(userEmail: String, signedPreKey: SignedPreKey)
    fun removeSignedPreKey(userEmail: String)

    fun getOneTimePreKey(userEmail: String): PreKey
    fun storeOneTimePreKeys(userEmail: String, preKeys: List<PreKey>)
    fun removeOneTimePreKey(userEmail: String, id: Int = 0)

    fun loadUserRecords(): Map<String, UserRecord>
    fun saveUserRecords(userRecords: MutableMap<String, UserRecord>)

    fun storeOrUpdateContactNickname(contactEmail: String, nickname: String?)
    fun getContactNickname(contactEmail: String): String?

    fun saveDeviceRecord(userEmail: String, nickname: String, record: DeviceRecord)

    fun loadUserEmail(userEmail: String): String
    fun loadDeviceId(userEmail: String): String
    fun setClientInfo(userEmail: String, userNickname:String, deviceId: String)
    fun getDevicePublicKey(userEmail: String): ByteArray?
    fun listClientInfos(): List<Triple<String, String?, String>>

    fun saveMessage(senderEmail: String, receiverEmail: String, content: String, timestamp: Long, isSentByLocalUser: Boolean): String
    fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<LocalDbMessage>
    fun getRecentConversationPartners(currentUserEmail: String): List<String>

    fun saveDebugData(messageId: Long, debugInfo: DebugInfo)
    suspend fun loadDebugData(messageId: Long): DebugInfo?
}
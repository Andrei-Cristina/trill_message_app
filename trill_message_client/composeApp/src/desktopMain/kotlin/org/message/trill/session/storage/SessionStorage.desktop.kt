package org.message.trill.session.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.message.trill.db.TrillMessageDatabaseQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.message.trill.db.TrillMessageDatabase
import org.message.trill.encryption.double_ratchet.RatchetState
import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.encryption.utils.models.DebugInfo
import org.message.trill.messaging.models.LocalDbMessage
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.Session
import org.message.trill.session.sesame.UserRecord
import java.io.File
import java.util.*
import kotlin.NoSuchElementException

fun sanitizeEmailForFilename(email: String): String {
    val username = email.substringBefore("@")
    val provider = email.substringAfter("@").substringBefore(".")
    val sanitizedUsername = username.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
    val sanitizedProvider = provider.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

    return "${sanitizedUsername}_${sanitizedProvider}"
}

actual class SessionStorage(private val currentUser: String) {
    private val driver: SqlDriver
    private val database: TrillMessageDatabase
    private val queries: TrillMessageDatabaseQueries

    init {
        val dbFileName = sanitizeEmailForFilename(currentUser) + ".db"
        val dbDir = File("user_dbs")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        val dbFile = File(dbDir, dbFileName)

        driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        TrillMessageDatabase.Schema.create(driver)
        database = TrillMessageDatabase(driver)
        queries = database.trillMessageDatabaseQueries
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
            driver.execute(null, "PRAGMA journal_mode=WAL;", 0)
        } catch (e: Exception) {
            println("Warning: Could not set PRAGMA for $currentUser's database: ${e.message}")
        }
        println("SessionStorage initialized for user $currentUser with DB: ${dbFile.absolutePath}")
    }

    actual fun getIdentityKey(userEmail: String): IdentityKey? {
        return queries.selectIdentityKey(userEmail).executeAsOneOrNull()?.let {
            IdentityKey(it.public_key, it.private_key)
        }
    }

    actual fun storeIdentityKey(userEmail: String, identityKey: IdentityKey) {
        queries.deleteIdentityKey(userEmail)
        queries.insertIdentityKey(userEmail, identityKey.publicKey, identityKey.privateKey)
    }

    actual fun getPreKey(userEmail: String, id: Int): PreKey? {
        return queries.selectOneTimePreKey(userEmail, id.toLong()).executeAsOneOrNull()?.let {
            PreKey(it.id.toInt(), it.public_key, it.private_key)
        }
    }

    actual fun storePreKey(userEmail: String, preKey: PreKey) {
        queries.insertOneTimePreKey(userEmail, preKey.id.toLong(), preKey.publicKey, preKey.privateKey)
    }

    actual fun removePreKey(userEmail: String, id: Int) {
        queries.deleteOneTimePreKey(userEmail, id.toLong())
    }

    actual fun getSignedPreKey(userEmail: String): SignedPreKey? {
        return queries.selectLatestSignedPreKey(userEmail).executeAsOneOrNull()?.let {
            SignedPreKey(PreKey(it.id.toInt(), it.public_key, it.private_key), it.signature)
        }
    }

    actual fun storeSignedPreKey(userEmail: String, signedPreKey: SignedPreKey) {
        queries.insertSignedPreKey(
            userEmail,
            signedPreKey.preKey.id.toLong(),
            signedPreKey.preKey.publicKey,
            signedPreKey.preKey.privateKey,
            signedPreKey.signature
        )
    }

    actual fun removeSignedPreKey(userEmail: String) {
        queries.deleteAllSignedPreKeys(userEmail)
    }

    actual fun getOneTimePreKey(userEmail: String): PreKey {
        val preKey = queries.selectFirstOneTimePreKey(userEmail).executeAsOneOrNull()
        return preKey?.let { PreKey(it.id.toInt(), it.public_key, it.private_key) }
            ?: throw NoSuchElementException("No one-time prekeys available for $userEmail")
    }

    actual fun storeOneTimePreKeys(userEmail: String, preKeys: List<PreKey>) {
        database.transaction {
            preKeys.forEach { preKey ->
                queries.insertOneTimePreKey(userEmail, preKey.id.toLong(), preKey.publicKey, preKey.privateKey)
            }
        }
    }

    actual fun removeOneTimePreKey(userEmail: String, id: Int) {
        queries.deleteOneTimePreKey(userEmail, id.toLong())
    }

    actual fun loadUserRecords(): Map<String, UserRecord> {
        val userRecords = mutableMapOf<String, UserRecord>()
        try {
            queries.selectAllUserRecords().executeAsList().forEach { user ->
                val devices = queries.selectDevicesByUser(user.user_id).executeAsList().associate { device ->
                    val sessions = queries.selectSessionsByDevice(user.user_id, device.device_id)
                        .executeAsList().map { session ->
                            Session(
                                session.session_id,
                                RatchetState.fromByteArray(session.ratchet_state),
                                session.is_initiating == 1L,
                                session.timestamp
                            )
                        }
                    val activeSession = sessions.find { it.sessionId == device.active_session_id }
                    val inactiveSessions = sessions.filter { it != activeSession }.toMutableList()
                    println("Loaded device ${device.device_id.encodeToBase64()} for ${user.user_id} with ${sessions.size} sessions (active: ${activeSession?.sessionId})")
                    device.device_id to DeviceRecord(
                        device.device_id,
                        device.public_key,
                        activeSession,
                        inactiveSessions,
                        device.is_stale == 1L,
                        device.stale_since
                    )
                }
                userRecords[user.user_id] = UserRecord(
                    user.user_id,
                    user.nickname ?: "",
                    devices.toMutableMap(),
                    user.is_stale == 1L,
                    user.stale_since
                )
                println("Loaded user record for ${user.user_id} with ${devices.size} devices")
            }
            println("Loaded ${userRecords.size} user records")
        } catch (e: Exception) {
            println("Error loading user records: ${e.message}")
        }
        return userRecords
    }

    actual fun saveUserRecords(userRecords: MutableMap<String, UserRecord>) {
        try {
            database.transaction {
                userRecords.forEach { (userId, userRecord) ->
                    queries.insertOrReplaceUserRecord(
                        user_id = userId,
                        nickname = userRecord.nickname,
                        is_stale = if (userRecord.isStale) 1 else 0,
                        stale_since = userRecord.staleTransitionTimestamp
                    )
                    println("Saved user record for $userId")
                    userRecord.devices.forEach l1@{ (deviceId, deviceRecord) ->
                        if (deviceRecord.publicKey.isEmpty()) {
                            println("Skipping device $deviceId for $userId: Empty public key")
                            return@l1
                        }
                        queries.insertOrReplaceDeviceRecord(
                            user_id = userId,
                            device_id = deviceId,
                            public_key = decodeIdentityKey(deviceRecord.publicKey),
                            is_stale = if (deviceRecord.isStale) 1 else 0,
                            stale_since = deviceRecord.staleTransitionTimestamp,
                            active_session_id = deviceRecord.activeSession?.sessionId
                        )
                        println("Saved device record $deviceId for $userId")
                        (listOfNotNull(deviceRecord.activeSession) + deviceRecord.inactiveSessions).forEach { session ->
                            try {
                                queries.insertOrReplaceSession(
                                    session_id = session.sessionId,
                                    user_id = userId,
                                    device_id = deviceId,
                                    ratchet_state = session.ratchetState.toByteArray(),
                                    is_initiating = if (session.isInitiating) 1 else 0,
                                    timestamp = session.timestamp
                                )
                                println("Saved session ${session.sessionId} for device $deviceId of $userId")
                            } catch (e: Exception) {
                                println("Error saving session ${session.sessionId} for $userId/$deviceId: ${e.message}")
                            }
                        }
                    }
                }
            }
            println("Saved ${userRecords.size} user records")
        } catch (e: Exception) {
            println("Error saving user records: ${e.message}")
            throw Exception("Error saving user records: ${e.message}")
        }
    }


    actual fun storeOrUpdateContactNickname(contactEmail: String, nickname: String?) {
        if (contactEmail == currentUser) {
            println("StoreOrUpdateContactNickname: Attempted to update self ($currentUser) as a contact.")
            val currentRecord = queries.selectUserRecord(currentUser).executeAsOneOrNull()
            if (currentRecord?.nickname != nickname) {
                queries.insertOrReplaceUserRecord(
                    user_id = currentUser,
                    nickname = nickname.takeIf { !it.isNullOrBlank() },
                    is_stale = currentRecord?.is_stale ?: 0,
                    stale_since = currentRecord?.stale_since
                )
                println("Updated self-record nickname for $currentUser to '$nickname'")
            }
            return
        }

        database.transaction {
            val existingRecord = queries.selectUserRecord(user_id = contactEmail).executeAsOneOrNull()
            if (existingRecord != null) {
                if (existingRecord.nickname != nickname && !nickname.isNullOrBlank()) {
                    queries.updateUserRecordNickname(nickname = nickname, user_id = contactEmail)
                    println("Updated nickname for contact $contactEmail to '$nickname' in $currentUser's DB.")
                } else if (existingRecord.nickname != null && nickname.isNullOrBlank()) {
                    queries.updateUserRecordNickname(nickname = null, user_id = contactEmail)
                    println("Cleared nickname for contact $contactEmail in $currentUser's DB.")
                }
            } else {
                queries.insertOrReplaceUserRecord(
                    user_id = contactEmail,
                    nickname = nickname.takeIf { !it.isNullOrBlank() },
                    is_stale = 0,
                    stale_since = null
                )
                println("Inserted new contact $contactEmail with nickname '$nickname' in $currentUser's DB.")
            }
        }
    }

    actual fun getContactNickname(contactEmail: String): String? {
        if (contactEmail == currentUser) {
            return queries.selectClientInfo(currentUser).executeAsOneOrNull()?.user_nickname
                ?: queries.selectUserRecord(currentUser).executeAsOneOrNull()?.nickname
        }
        return queries.selectUserRecord(user_id = contactEmail).executeAsOneOrNull()?.nickname
    }

    actual fun saveDeviceRecord(userEmail: String, nickname: String, record: DeviceRecord) {
        try {
            database.transaction {
                queries.insertOrReplaceUserRecord(userEmail, nickname, 0, null)
                if (record.publicKey.isEmpty()) {
                    println("Skipping device record for $userEmail: Empty public key")
                    return@transaction
                }
                queries.insertOrReplaceDeviceRecord(
                    user_id = userEmail,
                    device_id = record.deviceId,
                    public_key = decodeIdentityKey(record.publicKey),
                    is_stale = if (record.isStale) 1 else 0,
                    stale_since = record.staleTransitionTimestamp,
                    active_session_id = record.activeSession?.sessionId
                )
                (listOfNotNull(record.activeSession) + record.inactiveSessions).forEach { session ->
                    try {
                        queries.insertOrReplaceSession(
                            session_id = session.sessionId,
                            user_id = userEmail,
                            device_id = record.deviceId,
                            ratchet_state = session.ratchetState.toByteArray(),
                            is_initiating = if (session.isInitiating) 1 else 0,
                            timestamp = session.timestamp
                        )
                        println("Saved session ${session.sessionId} for device ${record.deviceId} of $userEmail")
                    } catch (e: Exception) {
                        println("Error saving session ${session.sessionId} for $userEmail/${record.deviceId}: ${e.message}")
                    }
                }
                println("Saved device record ${record.deviceId} for $userEmail")
            }
        } catch (e: Exception) {
            println("Error saving device record for $userEmail: ${e.message}")
            throw Exception("Error saving device record for $userEmail: ${e.message}")
        }
    }

    actual fun loadUserEmail(userEmail: String): String {
        return queries.selectClientInfo(userEmail).executeAsOneOrNull()?.user_email
            ?: throw IllegalStateException("User not found: $userEmail")
    }

    actual fun loadDeviceId(userEmail: String): String {
        return queries.selectClientInfo(userEmail).executeAsOneOrNull()?.device_id
            ?: throw IllegalStateException("User not found: $userEmail")
    }

    actual fun setClientInfo(userEmail: String, userNickname: String, deviceId: String) {
        if (!isBase64(deviceId)) {
            throw IllegalArgumentException("Invalid deviceId format for $userEmail: $deviceId")
        }
        println("Setting client info: userEmail=$userEmail, deviceId=$deviceId")

        queries.insertOrReplaceClientInfo(userEmail, userNickname, deviceId)
        storeOrUpdateContactNickname(userEmail, userNickname)
    }

    actual fun getDevicePublicKey(userEmail: String): ByteArray? {
        try {
            val deviceId = loadDeviceId(userEmail)
            val publicKey = queries.selectDevicesByUser(user_id = userEmail)
                .executeAsList()
                .find { it.device_id == deviceId }
                ?.public_key
            println("Retrieved device publicKey for userEmail=$userEmail, deviceId=$deviceId: ${publicKey?.encodeToBase64()}")
            return publicKey?.takeIf { it.size == 32 }
        } catch (e: Exception) {
            println("Error retrieving device public key for $userEmail: ${e.message}")
            return null
        }
    }

    actual fun listClientInfos(): List<Triple<String, String?, String>> {
        return queries.listClientInfos().executeAsList().map {
            Triple(it.user_email, it.user_nickname, it.device_id)
        }
    }

    private fun decodeIdentityKey(key: ByteArray): ByteArray {
        return try {
            if (key.size == 32) {
                key
            } else {
                throw IllegalArgumentException("Identity key must be 32 bytes, got ${key.size}")
            }
        } catch (e: Exception) {
            throw Exception("Failed to decode identity key: ${e.message}")
        }
    }

    private fun isBase64(str: String): Boolean {
        return try {
            Base64.getDecoder().decode(str)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    actual fun saveMessage(senderEmail: String, receiverEmail: String, content: String, timestamp: Long, isSentByLocalUser: Boolean): String {
        queries.insertMessage(
            sender_email = senderEmail,
            receiver_email = receiverEmail,
            content = content,
            timestamp = timestamp,
            is_sent_by_local_user = if (isSentByLocalUser) 1L else 0L
        )

        return queries.internalLastInsertRowId()
            .executeAsOne()
            .toString()
    }

    actual fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<LocalDbMessage> {
        return queries.getMessagesForConversation(user1_email = currentUserEmail, user2_email = contactEmail)
            .executeAsList()
            .map {
                LocalDbMessage(
                    id = it.id,
                    senderEmail = it.sender_email,
                    receiverEmail = it.receiver_email,
                    content = it.content,
                    timestamp = it.timestamp,
                    isSentByLocalUser = it.is_sent_by_local_user == 1L
                )
            }
    }

    actual fun getRecentConversationPartners(currentUserEmail: String): List<String> {
        return queries.getRecentConversations(current_user_email = currentUserEmail)
            .executeAsList()
            .map { it.contact_email }
    }

    actual fun saveDebugData(messageId: Long, debugInfo: DebugInfo) {
        queries.insertDebugData(
            messageId = messageId,
            messageKey = debugInfo.messageKey,
            headerDh = debugInfo.headerDh,
            headerPn = debugInfo.headerPn?.toLong(),
            headerN = debugInfo.headerN?.toLong(),
            fullAd = debugInfo.fullAd,
            ciphertext = debugInfo.ciphertext,
            rootKey = debugInfo.rootKey,
            receiveChainKey = debugInfo.receiveChainKey,
            sendChainKey = debugInfo.sendChainKey
        )
    }

    actual suspend fun loadDebugData(messageId: Long): DebugInfo? = withContext(Dispatchers.IO) {
        queries.selectDebugData(messageId).executeAsOneOrNull()?.let { row ->
            DebugInfo(
                messageKey = row.message_key,
                headerDh = row.header_dh,
                headerPn = row.header_pn?.toInt(),
                headerN = row.header_n?.toInt(),
                fullAd = row.full_ad!!,
                ciphertext = row.ciphertext!!,
                rootKey = row.root_key!!,
                receiveChainKey = row.receive_chain_key,
                sendChainKey = row.send_chain_key
            )
        }
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)
    private fun String.encodeToBase64(): ByteArray = Base64.getEncoder().encode(this.toByteArray())
}
package org.message.trill.session.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.message.trill.db.TrillMessageDatabaseQueries
import org.message.trill.db.TrillMessageDatabase
import org.message.trill.encryption.double_ratchet.RatchetState
import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.Session
import org.message.trill.session.sesame.UserRecord
import java.util.*
import kotlin.NoSuchElementException

actual class SessionStorage {
    private val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:app.db")
    private val database: TrillMessageDatabase
    private val queries: TrillMessageDatabaseQueries

    init {
        TrillMessageDatabase.Schema.create(driver)
        database = TrillMessageDatabase(driver)
        queries = database.trillMessageDatabaseQueries
        driver.execute(null, "PRAGMA journal_mode=WAL;", 0)
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
        }
        return userRecords
    }

    actual fun saveUserRecords(userRecords: MutableMap<String, UserRecord>) {
        database.transaction {
            userRecords.forEach { (userId, userRecord) ->
                queries.insertOrReplaceUserRecord(
                    user_id = userId,
                    nickname = userRecord.nickname,
                    is_stale = if (userRecord.isStale) 1 else 0,
                    stale_since = userRecord.staleTransitionTimestamp
                )
                userRecord.devices.forEach { (deviceId, deviceRecord) ->
                    queries.insertOrReplaceDeviceRecord(
                        user_id = userId,
                        device_id = deviceId,
                        public_key = deviceRecord.publicKey,
                        is_stale = if (deviceRecord.isStale) 1 else 0,
                        stale_since = deviceRecord.staleTransitionTimestamp,
                        active_session_id = deviceRecord.activeSession?.sessionId
                    )
                    (listOfNotNull(deviceRecord.activeSession) + deviceRecord.inactiveSessions).forEach { session ->
                        queries.insertOrReplaceSession(
                            session_id = session.sessionId,
                            user_id = userId,
                            device_id = deviceId,
                            ratchet_state = session.ratchetState.toByteArray(),
                            is_initiating = if (session.isInitiating) 1 else 0,
                            timestamp = session.timestamp
                        )
                    }
                }
            }
        }
    }

    actual fun saveDeviceRecord(userEmail: String, nickname: String, record: DeviceRecord) {
        database.transaction {
            queries.insertOrReplaceUserRecord(userEmail, nickname, 0, null)
            queries.insertOrReplaceDeviceRecord(
                user_id = userEmail,
                device_id = record.deviceId,
                public_key = record.publicKey,
                is_stale = if (record.isStale) 1 else 0,
                stale_since = record.staleTransitionTimestamp,
                active_session_id = record.activeSession?.sessionId
            )
            (listOfNotNull(record.activeSession) + record.inactiveSessions).forEach { session ->
                queries.insertOrReplaceSession(
                    session_id = session.sessionId,
                    user_id = userEmail,
                    device_id = record.deviceId,
                    ratchet_state = session.ratchetState.toByteArray(),
                    is_initiating = if (session.isInitiating) 1 else 0,
                    timestamp = session.timestamp
                )
            }
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
        queries.insertOrReplaceClientInfo(userEmail, userNickname, deviceId)
    }

    actual fun getDevicePublicKey(userEmail: String): ByteArray? {
        val publicKey = queries.selectDevicesByUser(user_id = userEmail)
            .executeAsList()
            .find { it.device_id == loadDeviceId(userEmail) }
            ?.public_key
        println("Retrieved device publicKey for userEmail=$userEmail: ${publicKey?.encodeToBase64()}")
        return publicKey
    }

    actual fun listClientInfos(): List<Triple<String, String?, String>> {
        return queries.listClientInfos().executeAsList().map {
            Triple(it.user_email, it.user_nickname, it.device_id)
        }
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}
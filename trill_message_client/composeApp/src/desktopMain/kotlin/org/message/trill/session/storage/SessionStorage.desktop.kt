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

actual class SessionStorage {
    private val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:app.db")
    private val database: TrillMessageDatabase

    init {
        TrillMessageDatabase.Schema.create(driver)
        database = TrillMessageDatabase(driver)

        if (database.trillMessageDatabaseQueries.selectClientInfo().executeAsOneOrNull() == null) {
            database.trillMessageDatabaseQueries.insertOrReplaceClientInfo(
                user_email = "user@example.com",
                user_nickname = "nickname",
                device_id = "desktop-device-1"
            )
        }
    }

    actual fun getIdentityKey(): IdentityKey? {
        return database.trillMessageDatabaseQueries.selectIdentityKey().executeAsOneOrNull()?.let {
            IdentityKey(it.public_key, it.private_key)
        }
    }

    actual fun storeIdentityKey(identityKey: IdentityKey) {
        database.trillMessageDatabaseQueries.deleteIdentityKey()
        database.trillMessageDatabaseQueries.insertIdentityKey(identityKey.publicKey, identityKey.privateKey)
    }

    actual fun getPreKey(id: Int): PreKey? {
        return database.trillMessageDatabaseQueries.selectOneTimePreKey(id.toLong()).executeAsOneOrNull()?.let {
            PreKey(it.id.toInt(), it.public_key, it.private_key)
        }
    }

    actual fun storePreKey(preKey: PreKey) {
        database.trillMessageDatabaseQueries.insertOneTimePreKey(preKey.publicKey, preKey.privateKey)
    }

    actual fun removePreKey(id: Int) {
        database.trillMessageDatabaseQueries.deleteOneTimePreKey(id.toLong())
    }

    actual fun getSignedPreKey(): SignedPreKey? {
        return database.trillMessageDatabaseQueries.selectLatestSignedPreKey().executeAsOneOrNull()?.let {
            SignedPreKey(PreKey(it.id.toInt(), it.public_key, it.private_key), it.signature)
        }
    }

    actual fun storeSignedPreKey(signedPreKey: SignedPreKey) {
        database.trillMessageDatabaseQueries.insertSignedPreKey(
            signedPreKey.preKey.id.toLong(),
            signedPreKey.preKey.publicKey,
            signedPreKey.preKey.privateKey,
            signedPreKey.signature
        )
    }

    actual fun removeSignedPreKey() {
        database.trillMessageDatabaseQueries.deleteAllSignedPreKeys()
    }

    actual fun getOneTimePreKey(): PreKey {
        val preKey = database.trillMessageDatabaseQueries.selectFirstOneTimePreKey().executeAsOneOrNull()
        return preKey?.let { PreKey(it.id.toInt(), it.public_key, it.private_key) }
            ?: throw NoSuchElementException("No one-time prekeys available")
    }

    actual fun storeOneTimePreKeys(preKeys: List<PreKey>) {
        database.transaction {
            preKeys.forEach { preKey ->
                database.trillMessageDatabaseQueries.insertOneTimePreKey(preKey.publicKey, preKey.privateKey)
            }
        }
    }

    actual fun removeOneTimePreKey(id: Int) {
        database.trillMessageDatabaseQueries.deleteOneTimePreKey(id.toLong())
    }

    actual fun loadUserRecords(): Map<out String, UserRecord> {
        val userRecords = mutableMapOf<String, UserRecord>()

        database.trillMessageDatabaseQueries.selectAllUserRecords().executeAsList().forEach { user ->
            val devices = database.trillMessageDatabaseQueries.selectDevicesByUser(user.user_id).executeAsList()
                .associate { device ->
                    val sessions =
                        database.trillMessageDatabaseQueries.selectSessionsByDevice(user.user_id, device.device_id)
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
                user.nickname?: "",
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
                database.trillMessageDatabaseQueries.insertOrReplaceUserRecord(
                    user_id = userId,
                    nickname = userRecord.nickname,
                    is_stale = if (userRecord.isStale) 1 else 0,
                    stale_since = userRecord.staleTransitionTimestamp
                )

                userRecord.devices.forEach { (deviceId, deviceRecord) ->
                    database.trillMessageDatabaseQueries.insertOrReplaceDeviceRecord(
                        user_id = userId,
                        device_id = deviceId,
                        public_key = deviceRecord.publicKey,
                        is_stale = if (deviceRecord.isStale) 1 else 0,
                        stale_since = deviceRecord.staleTransitionTimestamp,
                        active_session_id = deviceRecord.activeSession?.sessionId
                    )

                    (listOfNotNull(deviceRecord.activeSession) + deviceRecord.inactiveSessions).forEach { session ->
                        database.trillMessageDatabaseQueries.insertOrReplaceSession(
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

    actual fun saveDeviceRecord(user: String, nickname:String, record: DeviceRecord) {
        database.transaction {
            database.trillMessageDatabaseQueries.insertOrReplaceUserRecord(user, nickname, 0, null)

            database.trillMessageDatabaseQueries.insertOrReplaceDeviceRecord(
                user_id = user,
                device_id = record.deviceId,
                public_key = record.publicKey,
                is_stale = if (record.isStale) 1 else 0,
                stale_since = record.staleTransitionTimestamp,
                active_session_id = record.activeSession?.sessionId
            )

            (listOfNotNull(record.activeSession) + record.inactiveSessions).forEach { session ->
                database.trillMessageDatabaseQueries.insertOrReplaceSession(
                    session_id = session.sessionId,
                    user_id = user,
                    device_id = record.deviceId,
                    ratchet_state = session.ratchetState.toByteArray(),
                    is_initiating = if (session.isInitiating) 1 else 0,
                    timestamp = session.timestamp
                )
            }
        }
    }

    actual fun loadUserEmail(): String {
        return database.trillMessageDatabaseQueries.selectClientInfo().executeAsOneOrNull()?.user_email
            ?: throw IllegalStateException("Client info not initialized in database")
    }

    actual fun loadDeviceId(): String {
        return database.trillMessageDatabaseQueries.selectClientInfo().executeAsOneOrNull()?.device_id
            ?: throw IllegalStateException("Client info not initialized in database")
    }

    actual fun setClientInfo(userEmail: String, userNickname:String, deviceId: String) {
        database.trillMessageDatabaseQueries.insertOrReplaceClientInfo(userEmail, userNickname, deviceId)
    }
}
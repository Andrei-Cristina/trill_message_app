package org.message.trill.session.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.message.trill.encryption.keys.IdentityKey
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.Session
import org.message.trill.session.sesame.UserRecord

actual class SessionStorage {
    private val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:app.db")
    private val database: AppDatabase

    init {
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)

        if (database.clientInfoQueries.selectClientInfo().executeAsOneOrNull() == null) {
            database.clientInfoQueries.insertOrReplaceClientInfo(
                user_email = "user@example.com",
                device_id = "desktop-device-1"
            )
        }
    }

    actual fun getIdentityKey(): IdentityKey? {
        return database.identityKeyQueries.selectIdentityKey().executeAsOneOrNull()?.let {
            IdentityKey(it.public_key, it.private_key)
        }
    }

    actual fun storeIdentityKey(identityKey: IdentityKey) {
        database.identityKeyQueries.deleteIdentityKey()
        database.identityKeyQueries.insertIdentityKey(identityKey.publicKey, identityKey.privateKey)
    }

    actual fun getPreKey(id: Int): PreKey? {
        return database.oneTimePreKeyQueries.selectOneTimePreKey(id.toLong()).executeAsOneOrNull()?.let {
            PreKey(it.id.toInt(), it.public_key, it.private_key)
        }
    }

    actual fun storePreKey(preKey: PreKey) {
        database.oneTimePreKeyQueries.insertOneTimePreKey(preKey.publicKey, preKey.privateKey)
    }

    actual fun removePreKey(id: Int) {
        database.oneTimePreKeyQueries.deleteOneTimePreKey(id.toLong())
    }

    actual fun getSignedPreKey(): SignedPreKey? {
        return database.signedPreKeyQueries.selectLatestSignedPreKey().executeAsOneOrNull()?.let {
            SignedPreKey(it.id, PreKey(it.id, it.public_key, it.private_key), it.signature)
        }
    }

    actual fun storeSignedPreKey(signedPreKey: SignedPreKey) {
        database.signedPreKeyQueries.insertSignedPreKey(
            signedPreKey.id,
            signedPreKey.preKey.publicKey,
            signedPreKey.preKey.privateKey,
            signedPreKey.signature
        )
    }

    actual fun removeSignedPreKey() {
        database.signedPreKeyQueries.deleteAllSignedPreKeys()
    }

    actual fun getOneTimePreKey(): PreKey {
        val preKey = database.oneTimePreKeyQueries.selectFirstOneTimePreKey().executeAsOneOrNull()
        return preKey?.let { PreKey(it.id.toInt(), it.public_key, it.private_key) }
            ?: throw NoSuchElementException("No one-time prekeys available")
    }

    actual fun storeOneTimePreKeys(preKeys: List<PreKey>) {
        database.transaction {
            preKeys.forEach { preKey ->
                database.oneTimePreKeyQueries.insertOneTimePreKey(preKey.publicKey, preKey.privateKey)
            }
        }
    }

    actual fun removeOneTimePreKey(id: Int) {
        database.oneTimePreKeyQueries.deleteOneTimePreKey(id.toLong())
    }

    actual fun loadUserRecords(): Map<String, UserRecord> {
        val userRecords = mutableMapOf<String, UserRecord>()
        database.userRecordsQueries.selectAllUserRecords().executeAsList().forEach { user ->
            val devices = database.deviceRecordsQueries.selectDevicesByUser(user.user_id).executeAsList().associate { device ->
                val sessions = database.sessionsQueries.selectSessionsByDevice(user.user_id, device.device_id).executeAsList().map { session ->
                    Session(
                        session.session_id,
                        session.ratchet_state,
                        session.is_initiating == 1L,
                        session.timestamp
                    )
                }
                val activeSession = sessions.find { it.sessionId == device.active_session_id }
                val inactiveSessions = sessions.filter { it != activeSession }.toMutableList()
                device.device_id to DeviceRecord(
                    device.device_id,
                    device.public_key,
                    device.is_stale == 1L,
                    device.stale_since,
                    activeSession,
                    inactiveSessions
                )
            }
            userRecords[user.user_id] = UserRecord(
                user.user_id,
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
                database.userRecordsQueries.insertOrReplaceUserRecord(
                    user_id = userId,
                    is_stale = if (userRecord.isStale) 1 else 0,
                    stale_since = userRecord.staleSince
                )
                userRecord.devices.forEach { (deviceId, deviceRecord) ->
                    database.deviceRecordsQueries.insertOrReplaceDeviceRecord(
                        user_id = userId,
                        device_id = deviceId,
                        public_key = deviceRecord.publicKey,
                        is_stale = if (deviceRecord.isStale) 1 else 0,
                        stale_since = deviceRecord.staleSince,
                        active_session_id = deviceRecord.activeSession?.sessionId
                    )
                    (listOfNotNull(deviceRecord.activeSession) + deviceRecord.inactiveSessions).forEach { session ->
                        database.sessionsQueries.insertOrReplaceSession(
                            session_id = session.sessionId,
                            user_id = userId,
                            device_id = deviceId,
                            ratchet_state = session.ratchetState,
                            is_initiating = if (session.isInitiating) 1 else 0,
                            timestamp = session.timestamp
                        )
                    }
                }
            }
        }
    }

    actual fun saveDeviceRecord(user: String, record: DeviceRecord) {
        database.transaction {
            database.userRecordsQueries.insertOrReplaceUserRecord(user, 0, null)
            database.deviceRecordsQueries.insertOrReplaceDeviceRecord(
                user_id = user,
                device_id = record.deviceId,
                public_key = record.publicKey,
                is_stale = if (record.isStale) 1 else 0,
                stale_since = record.staleSince,
                active_session_id = record.activeSession?.sessionId
            )
            (listOfNotNull(record.activeSession) + record.inactiveSessions).forEach { session ->
                database.sessionsQueries.insertOrReplaceSession(
                    session_id = session.sessionId,
                    user_id = user,
                    device_id = record.deviceId,
                    ratchet_state = session.ratchetState,
                    is_initiating = if (session.isInitiating) 1 else 0,
                    timestamp = session.timestamp
                )
            }
        }
    }

    actual fun loadUserEmail(): String {
        return database.clientInfoQueries.selectClientInfo().executeAsOneOrNull()?.user_email
            ?: throw IllegalStateException("Client info not initialized in database")
    }

    actual fun loadDeviceId(): String {
        return database.clientInfoQueries.selectClientInfo().executeAsOneOrNull()?.device_id
            ?: throw IllegalStateException("Client info not initialized in database")
    }

    actual fun setClientInfo(userEmail: String, deviceId: String) {
        database.clientInfoQueries.insertOrReplaceClientInfo(userEmail, deviceId)
    }
}
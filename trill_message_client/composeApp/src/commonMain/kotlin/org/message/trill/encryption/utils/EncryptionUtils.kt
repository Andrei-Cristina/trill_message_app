package org.message.trill.encryption.utils

data class EncryptedFileResult(val ciphertext: ByteArray, val hmac: ByteArray)

expect object EncryptionUtils {
    /**
     * Generates an X25519 key pair for Diffie-Hellman key exchange.
     * - X25519 is used for secure key exchange in the Signal Protocol.
     * - The private key is used for DH computations, and the public key is shared.
     *
     * @return A Pair containing the private key and public key as ByteArrays.
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray>

    /**
     * Performs Diffie-Hellman key exchange using X25519.
     * - Computes a shared secret using a private key and the peer's public key.
     * - Used in X3DH and Double Ratchet for establishing shared keys.
     *
     * @param privateKey The local private key (32 bytes).
     * @param publicKey The peer's public key (32 bytes).
     * @return The shared secret (32 bytes).
     * @throws Exception if the DH computation fails.
     */
    fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray

    /**
     * Computes the SHA-256 hash of the input.
     * - Used for hashing in various parts of the Signal Protocol, such as key derivation.
     *
     * @param input The data to hash.
     * @return The SHA-256 hash (32 bytes).
     */
    fun sha256(input: ByteArray): ByteArray

    /**
     * Helper function to compute HMAC-SHA256.
     *
     * @param key The HMAC key.
     * @param data The data to authenticate.
     * @return The HMAC-SHA256 output (32 bytes).
     * @throws Exception if HMAC computation fails.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /**
     * Implements HKDF (HMAC-based Key Derivation Function) using HMAC-SHA256.
     * - Follows RFC 5869 for key derivation in X3DH and Double Ratchet.
     * - Derives a key of specified length from salt, input key material (IKM), and info.
     *
     * @param salt The salt for the extract phase (can be zero bytes).
     * @param ikm The input key material.
     * @param info Context-specific information.
     * @param length The desired output key length.
     * @return The derived key (length bytes).
     * @throws Exception if the requested output length is too large.
     */
    fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray

    /**
     * Encrypts plaintext with associated data (AD) using AES-256 CBC with HMAC-SHA256.
     * - Conforms to the Signal Protocol's message encryption standard.
     * - The key must be 64 bytes: 32 bytes for AES-256, 32 bytes for HMAC-SHA256.
     * - A random 16-byte IV is generated for each encryption.
     * - The HMAC covers the AD, IV, and ciphertext.
     *
     * @param key The 64-byte key (32 bytes AES + 32 bytes HMAC).
     * @param plaintext The data to encrypt.
     * @param ad Associated data to authenticate (e.g., message headers).
     * @return IV (16 bytes) + ciphertext + HMAC (32 bytes).
     * @throws Exception if the key is invalid or encryption fails.
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray, ad: ByteArray): ByteArray

    fun encryptFile(key: ByteArray, iv: ByteArray, plaintext: ByteArray, ad: ByteArray = byteArrayOf()): EncryptedFileResult

    /**
     * Decrypts ciphertext with associated data (AD) using AES-256 CBC with HMAC-SHA256.
     * - Expects the ciphertext to contain IV (16 bytes) + ciphertext + HMAC (32 bytes).
     * - Verifies the HMAC before decrypting to ensure integrity and authenticity.
     *
     * @param key The 64-byte key (32 bytes AES + 32 bytes HMAC).
     * @param ciphertext The data to decrypt, including IV and HMAC.
     * @param ad Associated data used in authentication.
     * @return The decrypted plaintext.
     * @throws Exception if the key is invalid, ciphertext is malformed, or HMAC verification fails.
     */
    fun decrypt(key: ByteArray, ciphertext: ByteArray, ad: ByteArray): ByteArray

    fun decryptFile(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, receivedHmac: ByteArray, ad: ByteArray = byteArrayOf()): ByteArray

    /**
     * Signs data using Ed25519 with the provided private key.
     * - Used for signing prekeys and other critical data in the Signal Protocol.
     *
     * @param privateKey The Ed25519 private key (32 bytes).
     * @param data The data to sign.
     * @return The detached signature (64 bytes).
     */
    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray

    /**
     * Verifies an Ed25519 signature using the public key.
     * - Used to verify signatures on prekeys and other authenticated data.
     *
     * @param publicKey The Ed25519 public key (32 bytes).
     * @param data The signed data.
     * @param signature The detached signature (64 bytes).
     * @return True if the signature is valid, false otherwise.
     */
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
}
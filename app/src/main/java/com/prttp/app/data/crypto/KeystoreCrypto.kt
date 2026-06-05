// Путь: app/src/main/java/com/translator/app/data/crypto/KeystoreCrypto.kt
//
// Переиспользуемый AES-256-GCM шифратор поверх Android Keystore.
// Вынесено из AppSettingsSerializer, чтобы тем же проверенным способом
// шифровать чувствительную базу пациента и дневник.
//
// Ключ создаётся один раз (StrongBox если доступен) и НИКОГДА не покидает
// Secure Enclave. Каждый namespace (settings / patient / journal) имеет
// собственный ключ по alias — компрометация одного не раскрывает другие.
//
// Формат блоба: [magic 0x47][version 0x01][12B IV][ciphertext+GCM tag]
// ═══════════════════════════════════════════════════════════════════════════
package com.prttp.app.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreCrypto {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12
    private const val MAGIC = 0x47
    private const val VERSION = 0x01
    private const val HEADER_SIZE = 2 + IV_LENGTH // magic+version+IV

    /** Шифрует строку. null/исключение наверх не пробрасываем как креш — кидаем. */
    fun encrypt(alias: String, plain: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return ByteArrayOutputStream(HEADER_SIZE + ciphertext.size).apply {
            write(MAGIC); write(VERSION); write(iv); write(ciphertext)
        }.toByteArray()
    }

    /**
     * Дешифрует блоб. Возвращает null, если формат неверный/повреждён/ключ
     * сменился — вызывающий код подставит значение по умолчанию.
     */
    fun decrypt(alias: String, blob: ByteArray): ByteArray? {
        if (blob.size < HEADER_SIZE) return null
        if (blob[0].toInt() and 0xFF != MAGIC) return null
        if (blob[1].toInt() != VERSION) return null
        return try {
            val iv = blob.copyOfRange(2, 2 + IV_LENGTH)
            val ciphertext = blob.copyOfRange(HEADER_SIZE, blob.size)
            val key = getOrCreateKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

    /** Удаляет ключ namespace (например при «стереть все данные пациента»). */
    fun deleteKey(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                // StrongBox (аппаратный безопасный элемент) если есть — иначе TEE.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { setIsStrongBoxBacked(true) }
                }
            }
            .build()
        return try {
            gen.init(spec); gen.generateKey()
        } catch (e: Exception) {
            // StrongBox недоступен на устройстве → fallback без него.
            val fallback = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            gen.init(fallback); gen.generateKey()
        }
    }
}

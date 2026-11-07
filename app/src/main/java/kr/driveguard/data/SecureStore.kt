package kr.driveguard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 사전 입력 정보를 Android Keystore의 AES-GCM 키로 암호화해 백업 제외 폴더에 저장한다. */
class SecureStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "profile.enc")

    fun exists(): Boolean = file.exists()

    fun load(): Profile? = runCatching {
        val data = file.readBytes()
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        Profile.fromJson(String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8))
    }.getOrNull()

    fun save(profile: Profile) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(profile.toJson().toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "profile.tmp")
        tmp.writeBytes(cipher.iv + sealed)
        tmp.renameTo(file)
    }

    fun delete() {
        file.delete()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "profile_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

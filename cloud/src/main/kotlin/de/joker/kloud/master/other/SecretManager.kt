package de.joker.kloud.master.other

import org.koin.core.component.KoinComponent
import java.io.File
import java.security.SecureRandom

class SecretManager : KoinComponent {
    fun loadSecrets(): String {
        val home = System.getProperty("user.home")
        val secretsFile = "$home/.kloud/forwarding.secret"
        val file = File(secretsFile)

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateSecureRandomString())
        }
        return file.readText()
    }

    fun getFullSecretPath(): File {
        val home = System.getProperty("user.home")
        val secretsFile = "$home/.kloud/forwarding.secret"
        return File(secretsFile)
    }

    fun getSecret(): String {
        val file = getFullSecretPath()
        if (!file.exists()) {
            throw IllegalStateException("Secret file does not exist. Please run loadSecrets() first.")
        }
        return file.readText()
    }

    private fun generateSecureRandomString(): String {
        val random = SecureRandom()

        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02x", it) }
    }
}
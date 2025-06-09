package de.joker.kloud.master.core

import org.koin.core.component.KoinComponent
import java.io.File
import java.security.SecureRandom

class SecretManager: KoinComponent {
    val random: SecureRandom = SecureRandom()

    fun loadSecrets(): String {
        val home = System.getProperty("user.home")
        val secretsFile = "$home/.kloud/forwarding.secret"
        val file = File(secretsFile)

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()

            file.writeText(generateSecureRandomString(64))
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

    private fun generateSecureRandomString(length: Int): String {
        val s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+[]{}|;:,.<>?"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val index = random.nextInt(s.length)
            sb.append(s[index])
        }
        return sb.toString()
    }


}
package nxovaeng

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/** 瓜子影视加解密工具（移植自 guazi.py，与 Java 版行为一致） */
object GuaziCrypto {

    /** AES-CBC 加密，返回大写 hex（对应 Python 的 encrypted.hex().upper()） */
    fun aesEncrypt(text: String, key: String, iv: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
            )
            cipher.doFinal(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /** AES-CBC 解密，输入大写 hex */
    fun aesDecrypt(hexText: String, key: String, iv: String): String? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
            )
            val bytes = hexText.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            String(cipher.doFinal(bytes), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** RSA 公钥加密（PKCS1v1.5），返回 base64 */
    fun rsaEncrypt(text: String, publicKeyBase64: String): String {
        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            Base64.getEncoder().encodeToString(
                cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * RSA 私钥解密，返回明文；失败返回 null。
     *
     * 注意：GuaziApi 里内嵌的私钥 PEM 头写着 "BEGIN RSA PRIVATE KEY"（PKCS#1），
     * 但经 openssl asn1parse 确认，其 DER 实际是 PKCS#8 PrivateKeyInfo 结构
     * （SEQUENCE { INTEGER 0, SEQ{OID rsaEncryption, NULL}, OCTET STRING{PKCS#1} }），
     * 只是标签写错了 —— 所以这里用 PKCS8EncodedKeySpec 是对的，不要"修正"成 PKCS#1 解析。
     * 另：该私钥与 RSA_PUBLIC_KEY 本来就不是一对（openssl 对过 modulus），这是协议设计：
     * 公钥是服务端的（客户端用它加密请求 keys），私钥是客户端的（服务端用其配对公钥加密响应 keys），
     * 两对密钥各司其职，不必也不应相同。
     */
    fun rsaDecrypt(base64Text: String, privateKeyPem: String): String? {
        return try {
            val pem = privateKeyPem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(pem)
            val key = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            String(
                cipher.doFinal(Base64.getDecoder().decode(base64Text)),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            null
        }
    }

    /** MD5 大写 hex（与 Java 版一致） */
    fun md5Upper(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
    }
}

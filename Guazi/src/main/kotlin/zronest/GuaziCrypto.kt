package nxovaeng

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/** 瓜子影视加解密工具（移植自 guazi.py，纯 Kotlin/Java 实现，完全兼容跨平台 JAR） */
object GuaziCrypto {

    private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val BASE64_INV = IntArray(256) { -1 }.apply {
        for (i in BASE64_CHARS.indices) {
            this[BASE64_CHARS[i].code] = i
        }
        this['='.code] = 0
    }

    private fun pureBase64Encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)
            val c2 = if (b1 >= 0) ((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0) else -1
            val c3 = if (b2 >= 0) b2 and 0x3F else -1

            sb.append(BASE64_CHARS[c0])
            sb.append(BASE64_CHARS[c1])
            sb.append(if (c2 >= 0) BASE64_CHARS[c2] else '=')
            sb.append(if (c3 >= 0) BASE64_CHARS[c3] else '=')
        }
        return sb.toString()
    }

    private fun pureBase64Decode(str: String): ByteArray {
        val clean = str.filter { it in BASE64_CHARS || it == '=' }
        if (clean.isEmpty()) return ByteArray(0)
        val pad = clean.takeLastWhile { it == '=' }.length
        val outLen = (clean.length * 6) / 8 - pad
        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = BASE64_INV[clean[i++].code]
            val c1 = BASE64_INV[clean[i++].code]
            val c2 = BASE64_INV[clean[i++].code]
            val c3 = BASE64_INV[clean[i++].code]

            if (outIdx < outLen) out[outIdx++] = ((c0 shl 2) or (c1 ushr 4)).toByte()
            if (outIdx < outLen) out[outIdx++] = (((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte()
            if (outIdx < outLen) out[outIdx++] = (((c2 and 0x03) shl 6) or c3).toByte()
        }
        return out
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            pureBase64Decode(str)
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            pureBase64Encode(bytes)
        }
    }

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
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            null
        }
    }

    /** RSA 公钥加密（PKCS1v1.5），返回 base64 */
    fun rsaEncrypt(text: String, publicKeyBase64: String): String {
        return try {
            val keyBytes = decodeBase64(publicKeyBase64)
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            encodeBase64(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
        } catch (e: Throwable) {
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
            val keyBytes = decodeBase64(pem)
            val key = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            String(
                cipher.doFinal(decodeBase64(base64Text)),
                Charsets.UTF_8
            )
        } catch (e: Throwable) {
            null
        }
    }

    /** MD5 大写 hex（与 Java 版一致） */
    fun md5Upper(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }
        } catch (e: Throwable) {
            ""
        }
    }
}

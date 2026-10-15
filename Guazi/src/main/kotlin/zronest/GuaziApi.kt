package nxovaeng

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import kotlin.random.Random

/** 瓜子影视 API 客户端（移植自 cluntop/tvbox py/guazi.py） */
class GuaziApi {

    companion object {
        private val HOSTS = listOf(
            "https://apinew.uozvr.com",
            "https://api.w32z7vtd.com",
            "https://api.6a7nnf7.com",
            "https://api.umygrx3.com",
            "https://api.rmedphk.com",
            "https://apinew.qksov.com",
            "https://apinew.qwepe.com",
            "https://gzapi.45366fut.com",
            "https://gzapi.n55zw7th.com",
            "https://gzapi.tt9butrf.com",
            "https://gzapi.unggfd5u.com",
            "https://api.27yyukw.com",
            "https://api.2xvd33x.com",
            "https://api.wsz9yan.com"
        )

        private const val AES_KEY = "OITxa5OqAYjhswxx"
        private const val AES_IV = "rCMNwZASNBKZ8mXV"
        private const val RSA_PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB"
        private const val RSA_PRIVATE_KEY = """-----BEGIN RSA PRIVATE KEY-----
MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1
ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU
1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcK
ZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7
HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcW
V9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdI
DblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34
saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVM
iMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUM
WBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8
jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZ
K7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1b
L3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oa
t5lYKfpe8k83ZA==
-----END RSA PRIVATE KEY-----"""

        private const val DEVICE_OLD_KEY = "aLFBMWpxBrIDAD1Si/KVvm41"
        private const val SIGN_SUFFIX = "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br"
    }

    private var hostIndex = 0
    private val host: String get() = HOSTS[hostIndex]

    private val deviceId = (864150060000000L + Random.nextLong(0, 9999)).toString()
    private val deviceKey = (1..40)
        .map { "0123456789ABCDEF".random() }
        .joinToString("")

    @Volatile private var token: String = ""
    @Volatile private var tokenId: String = ""
    @Volatile private var authed = false
    @Volatile private var registered = false
    private val authMutex = Mutex()

    /** 供业务参数使用（ensureToken 后调用） */
    suspend fun authParams(): Map<String, String> {
        ensureToken()
        return mapOf("token" to token, "token_id" to tokenId)
    }

    private fun headers(targetHost: String = host): Map<String, String> = mapOf(
        "User-Agent" to "Lavf/57.83.100",
        "code" to "GZ0369",
        "deviceId" to deviceId,
        "lang" to "zh_cn",
        "Cache-Control" to "no-cache",
        "Content-Type" to "application/x-www-form-urlencoded",
        "Version" to "2604028",
        "PackageName" to "com.ae06aebdbb.y286327f5a.ofe849883320260517",
        "Ver" to "3.0.3.2",
        "api-ver" to "3.0.3.2",
        "Referer" to targetHost
    )

    /** 确保 token 有效（懒加载，加 Mutex 防止并发重复认证） */
    private suspend fun ensureToken() {
        if (authed && token.isNotEmpty() && tokenId.isNotEmpty()) return
        authMutex.withLock {
            if (authed && token.isNotEmpty() && tokenId.isNotEmpty()) return

            // 先尝试注册，失败则尝试登录
            val signResult = if (registered) {
                authRequest(
                    "/App/Authentication/Device/signIn",
                    mapOf("new_key" to deviceKey, "old_key" to DEVICE_OLD_KEY)
                )
            } else {
                authRequest(
                    "/App/Authentication/Device/signUp",
                    mapOf(
                        "new_key" to deviceKey,
                        "old_key" to DEVICE_OLD_KEY,
                        "phone_type" to 1,
                        "code" to ""
                    )
                )
            }
            if (signResult != null) {
                applyAuth(signResult)
                registered = true
            }
            val refreshResult = authRequest(
                "/App/Authentication/Authenticator/refresh",
                emptyMap()
            )
            if (refreshResult != null) {
                applyAuth(refreshResult)
                authed = true
            }
        }
    }

    private fun applyAuth(result: JsonObject) {
        result.get("token")?.asString?.takeIf { it.isNotEmpty() }?.let {
            token = it
        } ?: throw Exception("认证失败，无 token 返回")
        result.get("app_user_id")?.asString?.takeIf { it.isNotEmpty() }?.let {
            tokenId = it
        }
    }

    /** 认证类请求（最多尝试 3 个 host） */
    private suspend fun authRequest(path: String, params: Map<String, Any>): JsonObject? {
        val maxTry = minOf(3, HOSTS.size)
        for (i in 0 until maxTry) {
            val idx = (hostIndex + i) % HOSTS.size
            val currentHost = HOSTS[idx]
            val result = sendEncryptedRequest(params, path, isAuth = true, targetHost = currentHost)
            if (result != null) {
                hostIndex = idx
                return result
            }
        }
        return null
    }

    /**
     * 发送加密请求，返回解密后的 JsonObject。
     * 轮询尝试最多 3 个 host，避免长达数分钟超时等待。
     */
    suspend fun request(params: Map<String, Any>, path: String): JsonObject? {
        val maxTry = minOf(3, HOSTS.size)
        for (attempt in 0 until 2) {
            for (i in 0 until maxTry) {
                val idx = (hostIndex + i) % HOSTS.size
                val currentHost = HOSTS[idx]
                val result = sendEncryptedRequest(params, path, isAuth = false, targetHost = currentHost)
                if (result != null) {
                    hostIndex = idx
                    return result
                }
            }
            // 失败时尝试重新认证一次
            if (attempt == 0) {
                authed = false
                try { ensureToken() } catch (_: Throwable) {}
            }
        }
        return null
    }

    private suspend fun sendEncryptedRequest(
        params: Map<String, Any>,
        path: String,
        isAuth: Boolean,
        targetHost: String = host
    ): JsonObject? {
        return try {
            if (!isAuth) ensureToken()

            // 1. AES 加密业务参数 -> request_key（大写 hex）
            val jsonParams = params.toJsonString()
            val requestKey = GuaziCrypto.aesEncrypt(jsonParams, AES_KEY, AES_IV)
            if (requestKey.isEmpty()) return null

            // 2. RSA 加密 keys
            val keyJson = """{"iv":"$AES_IV","key":"$AES_KEY"}"""
            val keys = GuaziCrypto.rsaEncrypt(keyJson, RSA_PUBLIC_KEY)
            if (keys.isEmpty()) return null

            // 3. 签名
            val t = (System.currentTimeMillis() / 1000).toString()
            val signStr =
                "token_id=,token=$token,phone_type=1,request_key=$requestKey,app_id=1,time=$t,keys=$keys$SIGN_SUFFIX"
            val signature = GuaziCrypto.md5Upper(signStr)

            // 4. 构建表单
            val form = FormBody.Builder()
                .add("token", token)
                .add("token_id", "")
                .add("phone_type", "1")
                .add("time", t)
                .add("phone_model", "xiaomi-25031")
                .add("keys", keys)
                .add("request_key", requestKey)
                .add("signature", signature)
                .add("app_id", "1")
                .add("ad_version", "1")
                .build()

            val resp = app.post(targetHost + path, headers = headers(targetHost), requestBody = form, timeout = 10L)
            val respJson = JsonParser.parseString(resp.text).asJsonObject

            if (respJson.has("code") && respJson.get("code").asInt != 200) {
                return null
            }
            val dataSection = respJson.getAsJsonObject("data") ?: return null
            val encryptedResponse = dataSection.get("response_key")?.asString
                ?: return null
            val encryptedKeys = dataSection.get("keys")?.asString ?: return null

            // 5. RSA 解密 keys -> 得到响应 AES key/iv
            val decryptedKeysJson = GuaziCrypto.rsaDecrypt(encryptedKeys, RSA_PRIVATE_KEY)
                ?: return null
            val keyInfo = JsonParser.parseString(decryptedKeysJson).asJsonObject
            val respKey = keyInfo.get("key")?.asString ?: return null
            val respIv = keyInfo.get("iv")?.asString ?: return null

            // 6. AES 解密响应体
            val decrypted = GuaziCrypto.aesDecrypt(encryptedResponse, respKey, respIv)
                ?: return null
            JsonParser.parseString(decrypted).asJsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun Map<String, Any>.toJsonString(): String {
        val obj = JsonObject()
        for ((k, v) in this) {
            when (v) {
                is Number -> obj.addProperty(k, v)
                is Boolean -> obj.addProperty(k, v)
                else -> obj.addProperty(k, v.toString())
            }
        }
        return obj.toString()
    }
}

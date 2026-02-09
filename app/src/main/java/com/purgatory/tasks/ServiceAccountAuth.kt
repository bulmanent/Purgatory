package com.purgatory.tasks

import android.content.Context
import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

object ServiceAccountAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/spreadsheets"
    private const val ASSET_FILE = "service_account.json"

    private val gson = Gson()
    private val httpClient = OkHttpClient()

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiryEpochSec: Long = 0L

    data class ServiceAccountKey(
        val client_email: String,
        val private_key: String,
        val token_uri: String
    )

    data class TokenResponse(
        val access_token: String?,
        val expires_in: Long?
    )

    fun getAccessToken(context: Context): String {
        val now = System.currentTimeMillis() / 1000
        val existing = cachedToken
        if (existing != null && now < tokenExpiryEpochSec - 60) {
            return existing
        }

        val key = loadKey(context)
        val assertion = createJwtAssertion(key, now)
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder()
            .url(key.token_uri)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Token request failed: ${response.code}")
            }
            val parsed = gson.fromJson(response.body?.string().orEmpty(), TokenResponse::class.java)
            val token = parsed.access_token ?: throw IllegalStateException("Missing access token.")
            val expiresIn = parsed.expires_in ?: 3600L
            cachedToken = token
            tokenExpiryEpochSec = now + expiresIn
            return token
        }
    }

    private fun loadKey(context: Context): ServiceAccountKey {
        val json = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
        return gson.fromJson(json, ServiceAccountKey::class.java)
    }

    private fun createJwtAssertion(key: ServiceAccountKey, nowEpochSec: Long): String {
        val headerJson = gson.toJson(mapOf("alg" to "RS256", "typ" to "JWT"))
        val claimsJson = gson.toJson(
            mapOf(
                "iss" to key.client_email,
                "scope" to SCOPE,
                "aud" to key.token_uri,
                "iat" to nowEpochSec,
                "exp" to (nowEpochSec + 3600)
            )
        )
        val header = base64UrlEncode(headerJson.toByteArray())
        val claims = base64UrlEncode(claimsJson.toByteArray())
        val signingInput = "$header.$claims"
        val signature = sign(signingInput, key.private_key)
        return "$signingInput.${base64UrlEncode(signature)}"
    }

    private fun sign(data: String, privateKeyPem: String): ByteArray {
        val privateKey = parsePrivateKey(privateKeyPem)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray())
        return signature.sign()
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded = Base64.getDecoder().decode(cleaned)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    private fun base64UrlEncode(input: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    }
}

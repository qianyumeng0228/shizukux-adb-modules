package com.qym.shizukux.audit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client over the review worker. The admin token travels in `X-Admin-Token`;
 * that is the only authorization the worker accepts.
 */
class AuditApi(private val endpoint: String, private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun base(): String = endpoint.trim().trimEnd('/').ifBlank { AuditStore.DEFAULT_ENDPOINT }

    suspend fun healthz(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${base()}/healthz").get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("连接失败：HTTP ${response.code}")
            text
        }
    }

    suspend fun pending(): List<Submission> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${base()}/pending")
            .header("X-Admin-Token", token)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.code == 401) error("管理员令牌无效（401）")
            if (!response.isSuccessful) error("读取队列失败：HTTP ${response.code}\n$text")
            val array = JSONObject(text).optJSONArray("submissions") ?: JSONArray()
            (0 until array.length()).map { array.getJSONObject(it).toSubmission() }
        }
    }

    suspend fun review(action: String, ticket: String, reason: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("action", action).put("ticket", ticket)
            if (!reason.isNullOrBlank()) payload.put("reason", reason)
            val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("${base()}/review")
                .header("X-Admin-Token", token)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.code == 401) error("管理员令牌无效（401）")
                if (!response.isSuccessful) error("审核失败：HTTP ${response.code}\n$text")
                JSONObject(text)
            }
        }

    /** Reads the catalog through the worker, so an approval shows up without CDN lag. */
    suspend fun published(indexEndpoint: String): List<PublishedModule> = withContext(Dispatchers.IO) {
        val url = indexEndpoint.trim().trimEnd('/') + "/index"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("读取索引失败：HTTP ${response.code}")
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val array = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("modules") ?: JSONArray()
            }
            (0 until array.length()).map { array.getJSONObject(it).toPublished() }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

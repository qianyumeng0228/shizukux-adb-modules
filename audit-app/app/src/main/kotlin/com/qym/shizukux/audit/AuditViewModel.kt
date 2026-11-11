package com.qym.shizukux.audit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AuditViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AuditStore(application)

    var endpoint by mutableStateOf(store.endpoint)
        private set
    var token by mutableStateOf(store.token)
        private set

    var pending by mutableStateOf<List<Submission>>(emptyList())
        private set
    var published by mutableStateOf<List<PublishedModule>>(emptyList())
        private set
    var files by mutableStateOf<List<ModuleFile>>(emptyList())
        private set

    var loadingPending by mutableStateOf(false)
        private set
    var loadingPublished by mutableStateOf(false)
        private set
    var busyTicket by mutableStateOf<String?>(null)
        private set
    var busyModule by mutableStateOf<String?>(null)
        private set
    var testing by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun api() = AuditApi(endpoint, token)

    /** All published ZIPs of one module id, newest first. */
    fun filesFor(id: String): List<ModuleFile> =
        files.filter { it.id == id }.sortedByDescending { it.version.orEmpty() }

    fun saveConfig(newEndpoint: String, newToken: String) {
        store.endpoint = newEndpoint
        store.token = newToken
        endpoint = store.endpoint
        token = store.token
        message = "配置已保存"
    }

    fun dismissNotice() {
        message = null
        error = null
    }

    /** Hits /healthz and then /pending, so a green result also proves the token works. */
    fun testConnection() {
        viewModelScope.launch {
            testing = true
            try {
                val health = api().healthz()
                api().pending()
                message = "连接正常：$health"
                error = null
            } catch (e: Exception) {
                error = e.message ?: "连接失败"
            } finally {
                testing = false
            }
        }
    }

    fun refreshPending() {
        if (token.isBlank()) {
            error = "请先在「设置」里填写管理员令牌"
            return
        }
        viewModelScope.launch {
            loadingPending = true
            try {
                pending = api().pending()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "读取队列失败"
            } finally {
                loadingPending = false
            }
        }
    }

    fun refreshPublished() {
        if (token.isBlank()) {
            error = "请先在「设置」里填写管理员令牌"
            return
        }
        viewModelScope.launch {
            loadingPublished = true
            try {
                published = api().published(endpoint)
                files = api().moduleFiles()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "读取索引失败"
            } finally {
                loadingPublished = false
            }
        }
    }

    /**
     * `version == null` takes the whole module down: catalog entry plus every ZIP.
     * Passing a version deletes that ZIP only, and drops the catalog entry when it is the live one.
     */
    fun unpublish(id: String, version: String?) {
        viewModelScope.launch {
            busyModule = if (version.isNullOrBlank()) id else "$id@$version"
            try {
                val result = api().unpublish(id, version)
                val removedFiles = result.optInt("deletedFiles", 0)
                val indexRemoved = result.optBoolean("indexRemoved", false)
                val fileRemoved = result.optBoolean("fileRemoved", false)
                message = when {
                    version.isNullOrBlank() ->
                        "已下架 $id：删除 $removedFiles 个 ZIP${if (indexRemoved) "，索引已移除" else ""}"
                    indexRemoved -> "已删除 $id v$version，索引条目同时移除"
                    fileRemoved -> "已删除 $id v$version"
                    else -> "已处理 $id v$version"
                }
                error = null
                refreshPublished()
            } catch (e: Exception) {
                error = e.message ?: "下架失败"
            } finally {
                busyModule = null
            }
        }
    }

    /**
     * Saves the catalog entry. `version` in [patch] renames the ZIP server-side, so download
     * links keep working; display fields are rewritten in place.
     */
    fun edit(module: PublishedModule, patch: org.json.JSONObject) {
        viewModelScope.launch {
            busyModule = "${module.id}#edit"
            try {
                val result = api().edit(module.id, module.version, patch)
                val saved = result.optJSONObject("module")
                val renamed = !result.isNull("renamedTo")
                message = "已保存 ${module.displayName}" +
                    if (renamed) "，版本号改为 v${saved?.optString("version").orEmpty()}" else ""
                error = null
                refreshPublished()
            } catch (e: Exception) {
                error = e.message ?: "保存失败"
            } finally {
                busyModule = null
            }
        }
    }

    fun approve(submission: Submission) = submitReview(submission, "approve", null)

    fun reject(submission: Submission, reason: String) = submitReview(submission, "reject", reason)

    private fun submitReview(submission: Submission, action: String, reason: String?) {
        viewModelScope.launch {
            busyTicket = submission.ticket
            try {
                val result = api().review(action, submission.ticket, reason)
                if (action == "approve") {
                    val path = result.optString("publishedPath")
                    message = "已通过：" + submission.displayName + " v" + submission.version +
                        if (path.isBlank()) "" else "，已发布到 " + path
                } else {
                    message = "已驳回：" + submission.displayName + " v" + submission.version
                }
                error = null
                refreshQueueQuietly()
            } catch (e: Exception) {
                error = e.message ?: "审核请求失败"
            } finally {
                busyTicket = null
            }
        }
    }

    private suspend fun refreshQueueQuietly() {
        try {
            pending = api().pending()
        } catch (_: Exception) {
            // The verdict already landed; a stale list is not worth an error banner.
        }
    }
}

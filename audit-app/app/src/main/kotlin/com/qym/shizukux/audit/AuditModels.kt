package com.qym.shizukux.audit

import org.json.JSONObject

/** One queue record, as written by the submit worker. */
data class Submission(
    val ticket: String,
    val id: String,
    val version: String,
    val versionCode: Int?,
    val name: String,
    val author: String?,
    val description: String?,
    val sizeBytes: Long,
    val sha256: String,
    val submittedAt: Long,
    val status: String,
    val issueNumber: Int?,
    val issueUrl: String?,
    val submitterHash: String?
) {
    val displayName: String get() = name.ifBlank { id }
}

/** One published entry, as read from the catalog index. */
data class PublishedModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int?,
    val author: String?,
    val description: String?,
    val downloadUrl: String?,
    val fallbackUrl: String?,
    val sizeBytes: Long,
    val tags: List<String>
) {
    val displayName: String get() = name.ifBlank { id }
}

/** One ZIP actually sitting in `modules/`, with its version parsed out. */
data class ModuleFile(
    val name: String,
    val id: String?,
    val version: String?,
    val sizeBytes: Long,
    val inCatalog: Boolean
)

internal fun JSONObject.toSubmission(): Submission = Submission(
    ticket = optString("ticket"),
    id = optString("id"),
    version = optString("version"),
    versionCode = if (has("versionCode") && !isNull("versionCode")) optInt("versionCode") else null,
    name = optString("name"),
    author = optString("author").takeIf { it.isNotBlank() && it != "null" },
    description = optString("description").takeIf { it.isNotBlank() && it != "null" },
    sizeBytes = optLong("sizeBytes"),
    sha256 = optString("sha256"),
    submittedAt = optLong("submittedAt"),
    status = optString("status", "pending"),
    issueNumber = if (has("issueNumber") && !isNull("issueNumber")) optInt("issueNumber") else null,
    issueUrl = optString("issueUrl").takeIf { it.isNotBlank() && it != "null" },
    submitterHash = optString("submitterHash").takeIf { it.isNotBlank() && it != "null" }
)

internal fun JSONObject.toPublished(): PublishedModule {
    val tags = mutableListOf<String>()
    val array = optJSONArray("tags")
    if (array != null) {
        for (i in 0 until array.length()) tags.add(array.optString(i))
    }
    return PublishedModule(
        id = optString("id"),
        name = optString("name"),
        version = optString("version"),
        versionCode = if (has("versionCode") && !isNull("versionCode")) optInt("versionCode") else null,
        author = optString("author").takeIf { it.isNotBlank() && it != "null" },
        description = optString("description").takeIf { it.isNotBlank() && it != "null" },
        downloadUrl = optString("downloadUrl").takeIf { it.isNotBlank() && it != "null" },
        fallbackUrl = optString("fallbackUrl").takeIf { it.isNotBlank() && it != "null" },
        sizeBytes = optLong("sizeBytes"),
        tags = tags
    )
}

internal fun JSONObject.toModuleFile(): ModuleFile = ModuleFile(
    name = optString("name"),
    id = optString("id").takeIf { it.isNotBlank() && it != "null" },
    version = optString("version").takeIf { it.isNotBlank() && it != "null" },
    sizeBytes = optLong("sizeBytes"),
    inCatalog = optBoolean("inCatalog", true)
)

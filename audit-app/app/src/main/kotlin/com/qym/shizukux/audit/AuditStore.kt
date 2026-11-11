package com.qym.shizukux.audit

import android.content.Context

/** Endpoint and admin token are the only two things this app needs to remember. */
class AuditStore(context: Context) {

    private val prefs = context.getSharedPreferences("audit", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    companion object {
        const val DEFAULT_ENDPOINT = "https://submit.zanzhuwang.cc.cd"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_TOKEN = "token"
    }
}

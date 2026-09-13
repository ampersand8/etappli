package com.nuelto.etappli.location

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.nuelto.etappli.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The one transport for the web services (Places, Routes, Open-Meteo). Fail-soft: every
 * failure — offline, timeout, a 4xx, junk — collapses to null and the caller degrades
 * (straight lines, "Search unavailable"), but each leaves a line under the `Http` tag
 * saying which call and why, or a dead key looks just like no signal.
 */
internal object Http {
    private const val TAG = "Http"
    private val MESSAGE = Regex(""""message"\s*:\s*"([^"]*)"""")

    fun post(url: String, body: String, headers: Map<String, String>): String? = call(url, headers) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? = call(url, headers) {}

    private fun call(url: String, headers: Map<String, String>, send: HttpURLConnection.() -> Unit): String? {
        // Never the query: session tokens and the photo key ride there.
        val where = url.substringBefore('?').substringAfter("://")
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 10_000
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                setRequestProperty("User-Agent", "Etappli/${BuildConfig.VERSION_NAME}")
            }
            try {
                connection.send()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.w(TAG, "$where: HTTP ${connection.responseCode} ${reason(error)}")
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "$where: $it") }.getOrNull()
    }

    /** Google's `error.message` ("API key not valid…"), else the body's first line. */
    private fun reason(error: String): String =
        MESSAGE.find(error)?.groupValues?.get(1) ?: error.lineSequence().firstOrNull().orEmpty().take(200)
}

/**
 * `X-Android-Package` and `X-Android-Cert`: how a web-service call proves it comes from
 * this app, so the key can be restricted to it (GOOGLE_MAPS_SETUP.md). Read once by
 * MapsBackend; empty until then, or on a build with no signing certificate.
 */
internal object AppIdentity {
    @Volatile
    var headers: Map<String, String> = emptyMap()
        private set

    fun read(context: Context) {
        val signers = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners.orEmpty()
        val cert = signers.firstOrNull()?.toByteArray() ?: return
        val sha1 = MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
        headers = mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to sha1)
        // The fingerprint to register on the key — a debug build's differs from Play's.
        Log.i("Http", "${context.packageName} signed $sha1")
    }
}

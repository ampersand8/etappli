package com.nuelto.etappli.location

import com.nuelto.etappli.BuildConfig
import com.nuelto.etappli.domain.SharedPlace
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a `maps.app.goo.gl` short link into the Maps URL it stands for. One HEAD with
 * redirects off: the first Location header already carries the whole answer, and not
 * following it keeps us off the HTML (and its consent interstitial) entirely.
 *
 * Best-effort like every other network call here — null when there is no signal, and the
 * chooser offers a retry.
 */
object ShareLinkResolver {

    suspend fun expand(url: String): String? = withContext(Dispatchers.IO) {
        // Only the hosts the parser vouched for: a redirect decides where a stop lands.
        if (!SharedPlace.isShortLink(url)) return@withContext null
        runCatching {
            (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "Etappli/${BuildConfig.VERSION_NAME}")
                try {
                    getHeaderField("Location")
                } finally {
                    disconnect()
                }
            }
        }.getOrNull()
    }
}

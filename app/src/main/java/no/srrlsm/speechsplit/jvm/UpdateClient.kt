package no.srrlsm.speechsplit.jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.srrlsm.speechsplit.core.GITHUB_REPO
import no.srrlsm.speechsplit.core.ReleaseInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Asks GitHub for the latest release of Speech Split.
 * Shared by the Android and Windows apps. Only the public release info is read;
 * nothing about the user or their speeches is sent.
 */
object UpdateClient {
    const val WINDOWS_FILE = "SpeechSplit-Setup.msi"
    const val ANDROID_FILE = "SpeechSplit.apk"

    suspend fun fetchLatest(repo: String = GITHUB_REPO): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URI.create("https://api.github.com/repos/$repo/releases/latest").toURL()
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "SpeechSplit")
            try {
                if (conn.responseCode != 200) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val assets = json.optJSONArray("assets")
                fun asset(name: String): String? {
                    if (assets == null) return null
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        if (a.optString("name").equals(name, ignoreCase = true)) return a.optString("browser_download_url")
                    }
                    return null
                }
                val tag = json.optString("tag_name")
                if (tag.isBlank()) return@withContext null
                ReleaseInfo(
                    version = tag.removePrefix("v").removePrefix("V"),
                    notes = json.optString("body"),
                    pageUrl = json.optString("html_url"),
                    windowsUrl = asset(WINDOWS_FILE),
                    androidUrl = asset(ANDROID_FILE),
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}

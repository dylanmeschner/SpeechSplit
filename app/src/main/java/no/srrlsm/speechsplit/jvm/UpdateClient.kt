package no.srrlsm.speechsplit.jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.srrlsm.speechsplit.core.GITHUB_REPO
import no.srrlsm.speechsplit.core.ReleaseInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Asks GitHub for the releases of Speech Split.
 * Shared by the Android and Windows apps. Only the public release info is read;
 * nothing about the user or their speeches is sent.
 */
object UpdateClient {
    const val WINDOWS_FILE = "SpeechSplit-Setup.msi"
    const val ANDROID_FILE = "SpeechSplit.apk"

    /** The newest published release. */
    suspend fun fetchLatest(repo: String = GITHUB_REPO): ReleaseInfo? = withContext(Dispatchers.IO) {
        get("https://api.github.com/repos/$repo/releases/latest")?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
    }

    /** The latest [count] releases, newest first (drafts and pre-releases left out). */
    suspend fun fetchReleases(repo: String = GITHUB_REPO, count: Int = 15): List<ReleaseInfo>? = withContext(Dispatchers.IO) {
        val text = get("https://api.github.com/repos/$repo/releases?per_page=$count") ?: return@withContext null
        runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                if (o.optBoolean("draft") || o.optBoolean("prerelease")) null else parse(o)
            }
        }.getOrNull()
    }

    private fun get(url: String): String? = try {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "SpeechSplit")
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun parse(json: JSONObject): ReleaseInfo? {
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
        if (tag.isBlank()) return null
        return ReleaseInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = if (json.isNull("body")) "" else json.optString("body"),  // Android's JSON gives "null" for null
            pageUrl = json.optString("html_url"),
            windowsUrl = asset(WINDOWS_FILE),
            androidUrl = asset(ANDROID_FILE),
            date = if (json.isNull("published_at")) "" else json.optString("published_at").take(10),
        )
    }
}

package com.example.telepromptertg

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater: checks the latest GitHub Release, downloads its .apk and hands it to
 * Android's installer (the user confirms with one tap).
 */
object Updater {

    /** Your public GitHub repository as "username/repository". Edit this before building. */
    const val REPO = "YOUR_GITHUB_USERNAME/YOUR_REPO_NAME"

    data class Release(val version: String, val apkUrl: String, val notes: String)

    fun isConfigured(): Boolean = !REPO.contains("YOUR_")

    /** Blocking network call: run it off the main thread. */
    @Throws(IOException::class)
    fun latestRelease(): Release {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "TeleprompterApp")
            val code = conn.responseCode
            if (code == 404) throw IOException("no release published yet")
            if (code !in 200..299) throw IOException("GitHub returned HTTP $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) throw IOException("the latest release has no .apk file attached")
            return Release(tag, apkUrl, json.optString("body", ""))
        } finally {
            conn.disconnect()
        }
    }

    /** True when [remote] (e.g. "v1.2") is a higher version than [local] (e.g. "1.1"). */
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }

    /**
     * Streams the APK straight into a PackageInstaller session and commits it.
     * Android then shows its own "Update this app?" confirmation.
     * Blocking: run it off the main thread. [onProgress] gets 0..100.
     */
    @Throws(IOException::class)
    fun install(context: Context, release: Release, onProgress: (Int) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        try {
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "TeleprompterApp")
                if (conn.responseCode !in 200..299) throw IOException("download failed (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                installer.openSession(sessionId).use { session ->
                    conn.inputStream.use { input ->
                        session.openWrite("update.apk", 0, if (total > 0) total else -1).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            var n: Int
                            while (input.read(buf).also { n = it } >= 0) {
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                            session.fsync(out)
                        }
                    }
                    val callback = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        Intent(context, InstallResultReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(callback.intentSender)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            try {
                installer.abandonSession(sessionId)
            } catch (_: Exception) {
            }
            if (e is IOException) throw e
            throw IOException(e.message ?: e.javaClass.simpleName)
        }
    }
}
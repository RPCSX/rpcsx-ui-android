package net.rpcsx.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.*
import okio.buffer
import okio.sink
import java.io.File
import java.io.RandomAccessFile
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object GitHub {
    const val server = "https://github.com/"
    const val apiServer = "https://api.github.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("github_cache", Context.MODE_PRIVATE)

        prefs.all.forEach {
            cache.entries[it.key] = Json.decodeFromString(it.value as String)
        }
    }

    @Serializable
    data class Release(
        val name: String,
        val assets: List<Asset> = emptyList()
    )

    @Serializable
    data class Asset(
        val name: String,
        val browser_download_url: String?
    )

    @Serializable
    data class CacheEntry(
        val timestamp: Long,
        val content: String,
    )

    data class Cache(
        val entries: HashMap<String, CacheEntry> = HashMap()
    )

    private val cache = Cache()

    sealed class DownloadStatus {
        data object Success : DownloadStatus()
        data class Error(val message: String?) : DownloadStatus()
    }

    sealed class FetchResult {
        data class Success<T>(val content: T) : FetchResult()
        data class Error(val message: String) : FetchResult()
    }

    sealed class GetResult {
        data class Success(val content: String) : GetResult()
        data class Error(val code: Int, val message: String?) : GetResult()
    }

    private fun getCached(url: String, timestamp: Long): String? {
        val result = cache.entries[url]
        if (result == null || result.timestamp + 1000 * 60 * 10 < timestamp) {
            return null
        }

        return result.content
    }

    suspend fun get(url: String): GetResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        getCached(url, timestamp)?.let { return@withContext GetResult.Success(it) }

        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                return@withContext GetResult.Error(response.code, response.message)
            }

            val cacheEntry = CacheEntry(timestamp, body)
            cache.entries[url] = cacheEntry
            prefs.edit {
                putString(url, Json.encodeToString(CacheEntry.serializer(), cacheEntry))
            }

            GetResult.Success(body)
        } catch (e: IOException) {
            GetResult.Error(-1, e.message)
        }
    }

    suspend fun fetchLatestRelease(repoUrl: String): FetchResult = withContext(Dispatchers.IO) {
        val repoPath = repoUrl.removePrefix(server)
        val apiUrl = "${apiServer}repos/$repoPath/releases/latest"

        when (val response = get(apiUrl)) {
            is GetResult.Error -> FetchResult.Error("Failed to fetch release: ${response.code} ${response.message}")
            is GetResult.Success -> {
                try {
                    FetchResult.Success(json.decodeFromString(Release.serializer(), response.content))
                } catch (e: Exception) {
                    FetchResult.Error("Parsing error: ${e.message}")
                }
            }
        }
    }

    suspend fun fetchReleases(repoUrl: String): FetchResult = withContext(Dispatchers.IO) {
        val repoPath = repoUrl.removePrefix(server)
        val apiUrl = "${apiServer}repos/$repoPath/releases"

        when (val response = get(apiUrl)) {
            is GetResult.Error -> FetchResult.Error("Failed to fetch releases: ${response.code} ${response.message}")
            is GetResult.Success -> {
                try {
                    val releases: List<Release> = json.decodeFromString(ListSerializer(Release.serializer()), response.content)
                    val drivers = releases.map { release ->
                        val assetUrl = release.assets.firstOrNull()?.browser_download_url
                        release.name to assetUrl
                    }
                    FetchResult.Success(drivers)
                } catch (e: Exception) {
                    FetchResult.Error("Parsing error: ${e.message}")
                }
            }
        }
    }

    suspend fun downloadAsset(
        assetUrl: String,
        destinationFile: File,
        progressCallback: (Long, Long) -> Unit,
        threadCount: Int = 4
    ): DownloadStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(assetUrl).head().build()
            val response = client.newCall(request).execute()
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: return@withContext DownloadStatus.Error("Unable to get file size")
            val supportsRange = response.header("Accept-Ranges") == "bytes"
            if (!supportsRange) return@withContext DownloadStatus.Error("Server does not support range requests")

            RandomAccessFile(destinationFile, "rw").setLength(contentLength)

            val chunkSize = contentLength / threadCount
            val totalBytesRead = AtomicLong(0)
            val deferredList = (0 until threadCount).map { i ->
                val start = i * chunkSize
                val end = if (i == threadCount - 1) contentLength - 1 else (start + chunkSize - 1)

                async(Dispatchers.IO) {
                    val partRequest = Request.Builder()
                        .url(assetUrl)
                        .addHeader("Range", "bytes=$start-$end")
                        .build()

                    client.newCall(partRequest).execute().use { partResponse ->
                        if (!partResponse.isSuccessful || partResponse.body == null) {
                            throw IOException("Part $i failed")
                        }

                        val inputStream = partResponse.body!!.byteStream()
                        val raf = RandomAccessFile(destinationFile, "rw")
                        raf.seek(start)

                        val buffer = ByteArray(32 * 1024)
                        var read: Int

                        while (inputStream.read(buffer).also { read = it } != -1) {
                            raf.write(buffer, 0, read)
                            val bytesReadNow = totalBytesRead.addAndGet(read.toLong())
                            progressCallback(bytesReadNow, contentLength)
                        }

                        raf.close()
                    }
                }
            }

            deferredList.awaitAll()
            return@withContext DownloadStatus.Success
        } catch (e: Exception) {
            Log.e("GitHub", "Parallel download failed", e)
            return@withContext DownloadStatus.Error(e.message ?: "Unknown error")
        }
    }
}

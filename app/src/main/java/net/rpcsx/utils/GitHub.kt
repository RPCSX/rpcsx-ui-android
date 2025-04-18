package net.rpcsx.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.*
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        assetUrl: String, destinationFile: File, progressCallback: (Long, Long) -> Unit
    ): DownloadStatus = withContext(Dispatchers.IO) {
        Log.w("GitHub", "Downloading asset $assetUrl")
        val request = Request.Builder().url(assetUrl).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext DownloadStatus.Error("Download failed: ${response.message}")
            }

            val contentLength = response.body!!.contentLength()
            var totalBytes = 0L

            destinationFile.sink().buffer().use { sink ->
                val source = response.body!!.source()
                val buffer = okio.Buffer()
                var read: Long

                while (source.read(buffer, 8192).also { read = it } != -1L) {
                    sink.write(buffer, read)
                    totalBytes += read
                    progressCallback(totalBytes, contentLength)
                }
            }

            if (totalBytes != contentLength && contentLength != -1L) {
                DownloadStatus.Error("Download incomplete")
            } else {
                DownloadStatus.Success
            }
        } catch (e: Exception) {
            Log.e("GitHub", "Error downloading asset", e)
            DownloadStatus.Error(e.message)
        }
    }
}

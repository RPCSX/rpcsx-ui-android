package net.rpcsx.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DriversFetcher {
    suspend fun fetchReleases(
        repoUrl: String, bypassValidation: Boolean = false
    ): GitHub.FetchResult {
        val isValid = bypassValidation || withContext(Dispatchers.IO) {
            try {
                GitHub.get("${GitHub.apiServer}repos/${repoUrl.removePrefix(GitHub.server)}/contents/.adrenoDrivers") is GitHub.GetResult.Success
            } catch (_: Exception) {
                false
            }
        }

        if (!isValid) {
            return GitHub.FetchResult.Error("Provided driver repo url is not valid.")
        }

        return GitHub.fetchReleases(repoUrl)
    }
}

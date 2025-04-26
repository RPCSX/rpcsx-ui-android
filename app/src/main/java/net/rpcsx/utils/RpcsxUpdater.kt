package net.rpcsx.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.rpcsx.RPCSX
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.ui.channels.DevRpcsxChannel
import java.io.File
import kotlin.system.exitProcess


object RpcsxUpdater {
    fun getCurrentVersion(): String? {
        if (RPCSX.activeLibrary.value == null) {
            return null
        }

        return "v" + RPCSX.instance.getVersion().trim().removeSuffix(" Draft").trim() + "-" + GeneralSettings["rpcsx_installed_arch"]
    }

    fun getFileArch(file: File): String? {
        val parts = file.name.removeSuffix(".so").split("_")
        if (parts.size != 3) {
            return null
        }

        return parts[1]
    }
    fun getFileVersion(file: File): String? {
        val parts = file.name.removeSuffix(".so").split("_")
        if (parts.size != 3) {
            return null
        }
        val arch = parts[1]
        val version = parts[2]
        return "$version-$arch"
    }

    fun getArch(): String {
        return GeneralSettings["rpcsx_arch"] as? String ?: "armv8-a"
    }

    fun setArch(arch: String) {
        GeneralSettings["rpcsx_arch"] = arch
    }

    suspend fun checkForUpdate(): String? {
        val url = DevRpcsxChannel // TODO: update once RPCSX has release with android support

        val arch = getArch()
        when (val fetchResult = GitHub.fetchLatestRelease(url)) {
            is GitHub.FetchResult.Success<*> -> {
                val release = fetchResult.content as GitHub.Release
                val releaseVersion = "${release.name}-${arch}"

                if (release.assets.find { it.name == "librpcsx-android-arm64-v8a-${arch}.so" }?.browser_download_url == null) {
                    return null
                }

                if (RPCSX.activeLibrary.value == null) {
                    return releaseVersion
                }

                if (getCurrentVersion() != releaseVersion && releaseVersion != GeneralSettings["rpcsx_bad_version"]) {
                    return releaseVersion
                }
            }
            is GitHub.FetchResult.Error -> {
//                AlertDialogQueue.showDialog("Check For RPCSX Updates Error", fetchResult.message)
            }
        }

        return null
    }

    suspend fun downloadUpdate(destinationDir: File, progressCallback: (Long, Long) -> Unit): File? {
        val url = DevRpcsxChannel // TODO: GeneralSettings["rpcsx_channel"] as String
        val arch = getArch()

        when (val fetchResult = GitHub.fetchLatestRelease(url)) {
            is GitHub.FetchResult.Success<*> -> {
                val release = fetchResult.content as GitHub.Release
                val releaseVersion = "${release.name}-${arch}"
                val releaseAsset = release.assets.find { it.name == "librpcsx-android-arm64-v8a-$arch.so" }

                if (releaseVersion != getCurrentVersion() && releaseAsset?.browser_download_url != null) {
                    val target = File(destinationDir, "librpcsx-android_${arch}_${release.name}.so")

                    if (target.exists()) {
                        return target
                    }

                    val tmp = File(destinationDir, "librpcsx.so.tmp")
                    if (tmp.exists()) {
                        withContext(Dispatchers.IO) {
                            tmp.delete()
                        }
                    }

                    withContext(Dispatchers.IO) {
                        tmp.createNewFile()
                    }

                    tmp.deleteOnExit()

                    when (val downloadStatus = GitHub.downloadAsset(releaseAsset.browser_download_url, tmp, progressCallback)) {
                        is GitHub.DownloadStatus.Success -> {
                            withContext(Dispatchers.IO) {
                                tmp.renameTo(target)
                            }
                            return target
                        }
                        is GitHub.DownloadStatus.Error ->
                            AlertDialogQueue.showDialog("RPCSX Download Error", downloadStatus.message ?: "Unexpected error")
                    }
                }
            }
            is GitHub.FetchResult.Error -> {
                AlertDialogQueue.showDialog("RPCSX Download Error", fetchResult.message)
            }
        }

        return null
    }

    fun installUpdate(context: Context, updateFile: File): Boolean {
        val restart = {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
            mainIntent.setPackage(context.packageName)
            context.startActivity(mainIntent)
            GeneralSettings.sync()
            exitProcess(0)
        }

        val prevLibrary = GeneralSettings["rpcsx_library"] as? String
        val prevArch = GeneralSettings["rpcsx_installed_arch"] as? String
        GeneralSettings["rpcsx_library"] = updateFile.toString()
        GeneralSettings["rpcsx_update_status"] = null
        GeneralSettings["rpcsx_installed_arch"] = getFileArch(updateFile)

        Log.e("RPCSX-UI", "registered update file ${GeneralSettings["rpcsx_library"]}")

        if (prevLibrary == null) {
            restart()
        }

        GeneralSettings["rpcsx_prev_library"] = prevLibrary
        GeneralSettings["rpcsx_prev_installed_arch"] = prevArch
        AlertDialogQueue.showDialog("RPCSX Update", "Restart RPCSX UI to apply change", onConfirm = {
            restart()
        })
        return true
    }
}

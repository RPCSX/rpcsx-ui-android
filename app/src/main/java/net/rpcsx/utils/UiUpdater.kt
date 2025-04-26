package net.rpcsx.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.rpcsx.BuildConfig
import net.rpcsx.dialogs.AlertDialogQueue
import java.io.File

class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val activityIntent =
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

                context.startActivity(activityIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                AlertDialogQueue.showDialog("UI Update Error", msg ?: "Unexpected error")
            }
        }
    }
}

object UiUpdater {
    suspend fun checkForUpdate(context: Context): String? {
        val url = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("ui_channel", "")!!

        when (val fetchResult = GitHub.fetchLatestRelease(url)) {
            is GitHub.FetchResult.Success<*> -> {
                val release = fetchResult.content as GitHub.Release

                if (release.name != BuildConfig.Version && release.assets.find { it.name == "rpcsx-release.apk" }?.browser_download_url != null) {
                    return release.name
                }
            }
            is GitHub.FetchResult.Error -> {
//                AlertDialogQueue.showDialog("Check For UI Updates Error", fetchResult.message)
            }
        }

        return null
    }

    suspend fun downloadUpdate(context: Context, destinationDir: File, progressCallback: (Long, Long) -> Unit): File? {
        val url = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("ui_channel", "")!!

        when (val fetchResult = GitHub.fetchLatestRelease(url)) {
            is GitHub.FetchResult.Success<*> -> {
                val release = fetchResult.content as GitHub.Release
                val releaseAsset = release.assets.find { it.name == "rpcsx-release.apk" }

                if (release.name != BuildConfig.Version && releaseAsset?.browser_download_url != null) {
                    val target = File(destinationDir, "rpcsx-${release.name}.apk")

                    if (target.exists()) {
                        return target
                    }

                    val tmp = File(destinationDir, "rpcsx.tmp.apk")
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
                            AlertDialogQueue.showDialog("UI Update Download Error", downloadStatus.message ?: "Unexpected error")
                    }
                }
            }
            is GitHub.FetchResult.Error -> {
                AlertDialogQueue.showDialog("UI Update Error", fetchResult.message)
            }
        }

        return null
    }

    fun installUpdate(context: Context, updateFile: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            AlertDialogQueue.showDialog(
                "Permission required",
                "Enable install from this source permission to update",
                onConfirm = {
                    val intent: Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(String.format("package:%s", context.packageName).toUri())
                    context.startActivity(intent)
                    installUpdate(context, updateFile)
                })

            return false
        }

        Log.w("UI Update", "going to open input stream")

        val intent = Intent(context, PackageInstallStatusReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3456,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        updateFile.inputStream().use { apkStream ->
            val installer = context.packageManager.packageInstaller
            val length = updateFile.length()

            val params =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)

            installer.openSession(sessionId).use { session ->
                session.openWrite(updateFile.name, 0, length).use { sessionStream ->
                    apkStream.copyTo(sessionStream)
                    session.fsync(sessionStream)
                }

                session.commit(pendingIntent.intentSender)
            }
        }

        return true
    }
}
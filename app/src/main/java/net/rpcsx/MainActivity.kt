package net.rpcsx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.ui.navigation.AppNavHost
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GitHub
import net.rpcsx.utils.RpcsxUpdater
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var unregisterUsbEventListener: () -> Unit
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GeneralSettings.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!RPCSX.initialized) {
            Permission.PostNotifications.requestPermission(this)

            with(getSystemService(NOTIFICATION_SERVICE) as NotificationManager) {
                val channel = NotificationChannel(
                    "rpcsx-progress",
                    "Installation progress",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }

                createNotificationChannel(channel)
            }

            RPCSX.rootDirectory = applicationContext.getExternalFilesDir(null).toString()
            if (!RPCSX.rootDirectory.endsWith("/")) {
                RPCSX.rootDirectory += "/"
            }

            lifecycleScope.launch {
                GameRepository.load()
            }

            FirmwareRepository.load()
            GitHub.initialize(this)

            var rpcsxLibrary = GeneralSettings["rpcsx_library"] as? String
            val rpcsxUpdateStatus = GeneralSettings["rpcsx_update_status"]
            val rpcsxPrevLibrary = GeneralSettings["rpcsx_prev_library"] as? String

            if (rpcsxLibrary != null) {
                if (rpcsxUpdateStatus == false && rpcsxPrevLibrary != null) {
                    GeneralSettings["rpcsx_library"] = rpcsxPrevLibrary
                    GeneralSettings["rpcsx_installed_arch"] = GeneralSettings["rpcsx_prev_installed_arch"]
                    GeneralSettings["rpcsx_prev_installed_arch"] = null
                    GeneralSettings["rpcsx_prev_library"] = null
                    GeneralSettings["rpcsx_bad_version"] = RpcsxUpdater.getFileVersion(File(rpcsxLibrary))
                    GeneralSettings.sync()

                    File(rpcsxLibrary).delete()
                    rpcsxLibrary = rpcsxPrevLibrary

                    AlertDialogQueue.showDialog("RPCSX Update Failed", "Failed to load new version, previous version was restored")
                } else if (rpcsxUpdateStatus == null) {
                    GeneralSettings["rpcsx_update_status"] = false
                    GeneralSettings.sync()
                }

                RPCSX.openLibrary(rpcsxLibrary)
            }

            val nativeLibraryDir =
                packageManager.getApplicationInfo(packageName, 0).nativeLibraryDir
            RPCSX.nativeLibDirectory = nativeLibraryDir

            if (RPCSX.activeLibrary.value != null) {
                RPCSX.instance.initialize(RPCSX.rootDirectory, UserRepository.getUserFromSettings())
                val gpuDriverPath = GeneralSettings["gpu_driver_path"] as? String
                val gpuDriverName = GeneralSettings["gpu_driver_name"] as? String

                if (gpuDriverPath != null && gpuDriverName != null) {
                    RPCSX.instance.setCustomDriver(gpuDriverPath, gpuDriverName, nativeLibraryDir)
                }

                lifecycleScope.launch {
                    UserRepository.load()
                }

                RPCSX.initialized = true

                thread {
                    RPCSX.instance.startMainThreadProcessor()
                }

                thread {
                    RPCSX.instance.processCompilationQueue()
                }

                GeneralSettings["rpcsx_update_status"] = true
                if (rpcsxPrevLibrary != null) {
                    if (rpcsxLibrary != rpcsxPrevLibrary) {
                        File(rpcsxPrevLibrary).delete()
                    }

                    GeneralSettings["rpcsx_prev_library"] = null
                    GeneralSettings["rpcsx_prev_installed_arch"] = null
                    GeneralSettings.sync()
                }
            }

            val updateFile = File(RPCSX.rootDirectory + "cache", "rpcsx-${BuildConfig.Version}.apk")
            if (updateFile.exists()) {
                updateFile.delete()
            }
        }

        setContent {
            RPCSXTheme {
                AppNavHost()
            }
        }

        if (RPCSX.activeLibrary.value != null) {
            unregisterUsbEventListener = listenUsbEvents(this)
        } else {
            unregisterUsbEventListener = {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUsbEventListener()
    }
}

package net.rpcsx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.rpcsx.ui.navigation.AppNavHost
import net.rpcsx.utils.GitHub
import java.io.File
import net.rpcsx.utils.InputBindingPrefs
import net.rpcsx.utils.GeneralSettings
import kotlin.concurrent.thread

private const val ACTION_USB_PERMISSION = "net.rpcsx.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private lateinit var unregisterUsbEventListener: () -> Unit
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        GeneralSettings.init(this)

        RPCSX.rootDirectory = applicationContext.getExternalFilesDir(null).toString()
        if (!RPCSX.rootDirectory.endsWith("/")) {
            RPCSX.rootDirectory += "/"
        }

        if (!RPCSX.initialized) {
            lifecycleScope.launch { GameRepository.load() }
            FirmwareRepository.load()
        }

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

        if (!RPCSX.initialized) {
            GitHub.initialize(this)
            RPCSX.instance.initialize(RPCSX.rootDirectory)
            val nativeLibraryDir = packageManager.getApplicationInfo(packageName, 0).nativeLibraryDir
            RPCSX.instance.settingsSet("Video@@Vulkan@@Custom Driver@@Hook Directory", "\"" + nativeLibraryDir + "\"")
            RPCSX.initialized = true

            thread {
                RPCSX.instance.startMainThreadProcessor()
            }

            thread {
                RPCSX.instance.processCompilationQueue()
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

        unregisterUsbEventListener = listenUsbEvents(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterUsbEventListener()
    }
}

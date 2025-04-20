package net.rpcsx

import android.view.Surface
import androidx.compose.runtime.mutableStateOf

enum class Digital1Flags(val bit: Int)
{
    None(0),
    CELL_PAD_CTRL_SELECT(0x00000001),
    CELL_PAD_CTRL_L3(0x00000002),
    CELL_PAD_CTRL_R3(0x00000004),
    CELL_PAD_CTRL_START(0x00000008),
    CELL_PAD_CTRL_UP(0x00000010),
    CELL_PAD_CTRL_RIGHT(0x00000020),
    CELL_PAD_CTRL_DOWN(0x00000040),
    CELL_PAD_CTRL_LEFT(0x00000080),
    CELL_PAD_CTRL_PS(0x00000100),
}

enum class Digital2Flags(val bit: Int)
{
    None(0),
    CELL_PAD_CTRL_L2(0x00000001),
    CELL_PAD_CTRL_R2(0x00000002),
    CELL_PAD_CTRL_L1(0x00000004),
    CELL_PAD_CTRL_R1(0x00000008),
    CELL_PAD_CTRL_TRIANGLE(0x00000010),
    CELL_PAD_CTRL_CIRCLE(0x00000020),
    CELL_PAD_CTRL_CROSS(0x00000040),
    CELL_PAD_CTRL_SQUARE(0x00000080),
};

enum class EmulatorState {
    Stopped,
    Loading,
    Stopping,
    Running,
    Paused,
    Frozen, // paused but cannot resume
    Ready,
    Starting;

    companion object {
        fun fromInt(value: Int) = EmulatorState.entries.first { it.ordinal == value }
    }
}

enum class BootResult
{
    NoErrors,
    GenericError,
    NothingToBoot,
    WrongDiscLocation,
    InvalidFileOrFolder,
    InvalidBDvdFolder,
    InstallFailed,
    DecryptionError,
    FileCreationError,
    FirmwareMissing,
    UnsupportedDiscType,
    SavestateCorrupted,
    SavestateVersionUnsupported,
    StillRunning,
    AlreadyAdded,
    CurrentlyRestricted;

    companion object {
        fun fromInt(value: Int) = entries.first { it.ordinal == value }
    }
};

class RPCSX {
    fun initialize(rootDir: String): Boolean = true
    fun installFw(fd: Int, progressId: Long): Boolean = true
    fun install(fd: Int, progressId: Long): Boolean = true
    fun installKey(fd: Int, requestId: Long, gamePath: String): Boolean = true
    fun boot(path: String): Int = BootResult.NoErrors.ordinal
    fun surfaceEvent(surface: Surface, event: Int): Boolean = true
    fun usbDeviceEvent(fd: Int, vendorId: Int, productId: Int, event: Int): Boolean = true
    fun processCompilationQueue(): Boolean = true
    fun startMainThreadProcessor(): Boolean = true
    fun overlayPadData(digital1: Int, digital2: Int, leftStickX: Int, leftStickY: Int, rightStickX: Int, rightStickY: Int): Boolean = true
    fun collectGameInfo(rootDir: String, progressId: Long): Boolean = true
    fun systemInfo(): String = "Dummy System Info v1.0"
    fun settingsGet(path: String): String = "\"dummy_setting_value\""
    fun settingsSet(path: String, value: String): Boolean = true
    fun getState(): Int = EmulatorState.Ready.ordinal
    fun kill() {}
    fun resume() {}
    fun openHomeMenu() {}
    fun getTitleId(): String = "DUMMY00001"
    fun supportsCustomDriverLoading(): Boolean = false
    fun isInstallableFile(fd: Int): Boolean = false
    fun getDirInstallPath(sfoFd: Int): String? = "/dummy/path/"
    fun getVersion(): String = "0.0.1-dummy"

    companion object {
        var initialized = false
        val instance = RPCSX()
        var rootDirectory = ""
        var lastPlayedGame = ""
        var activeGame = mutableStateOf<String?>(null)
        var state = mutableStateOf(EmulatorState.Stopped)

        fun boot(path: String): BootResult {
            return BootResult.fromInt(instance.boot(path))
        }

        fun updateState() {
            val newState = EmulatorState.fromInt(instance.getState())
            if (newState != state.value) {
                state.value = newState
            }
        }

        fun getState(): EmulatorState {
            updateState()
            return state.value
        }

        init {
            // Simulate library load only
            try {
                System.loadLibrary("rpcsx-android")
            } catch (e: UnsatisfiedLinkError) {
                println("Dummy mode: native lib not loaded")
            }
        }
    }
}
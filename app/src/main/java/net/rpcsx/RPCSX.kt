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
package net.rpcsx.ui.navigation

import android.net.Uri
import android.util.Log
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import net.rpcsx.EmulatorState
import net.rpcsx.FirmwareRepository
import net.rpcsx.PrecompilerService
import net.rpcsx.PrecompilerServiceAction
import net.rpcsx.ProgressRepository
import net.rpcsx.R
import net.rpcsx.RPCSX
import net.rpcsx.overlay.OverlayEditActivity
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.ui.drivers.GpuDriversScreen
import net.rpcsx.ui.games.GamesScreen
import net.rpcsx.ui.settings.AdvancedSettingsScreen
import net.rpcsx.ui.settings.SettingsScreen
import net.rpcsx.utils.FileUtil
import org.json.JSONObject

@Preview
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateOf(JSONObject(RPCSX.instance.settingsGet(""))) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    AlertDialogQueue.AlertDialog()

    NavHost(
        navController = navController,
        startDestination = "games"
    ) {
        composable(
            route = "games"
        ) {
            GamesDestination(
                navigateToSettings = { navController.navigate("settings") },
                drawerState
            )
        }

        fun unwrapSetting(obj: JSONObject, path: String = "") {
            obj.keys().forEach self@{ key ->
                val item = obj[key]
                val elemPath = "$path@@$key"
                val elemObject = item as? JSONObject
                if (elemObject == null) {
                    Log.e("Main", "element is not object: settings$elemPath, $item")
                    return@self
                }

                if (elemObject.has("type")) {
                    return@self
                }

                Log.e("Main", "registration settings$elemPath")

                composable(
                    route = "settings$elemPath"
                ) {
                    AdvancedSettingsScreen(
                        navigateBack = navController::navigateUp,
                        navigateTo = { navController.navigate(it) },
                        settings = elemObject,
                        path = elemPath
                    )
                }

                unwrapSetting(elemObject, elemPath)
            }
        }

        composable(
            route = "settings@@$"
        ) {
            AdvancedSettingsScreen(
                navigateBack = navController::navigateUp,
                navigateTo = { navController.navigate(it) },
                settings = settings.value,
            )
        }

        composable(
            route = "settings"
        ) {
            SettingsScreen(
                navigateBack = navController::navigateUp,
                navigateTo = { navController.navigate(it) },
            )
        }

        composable(
            route = "drivers"
        ) {
            GpuDriversScreen(
                navigateBack = navController::navigateUp
            )
        }

        unwrapSetting(settings.value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesDestination(
    navigateToSettings: () -> Unit,
    drawerState: androidx.compose.material3.DrawerState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var emulatorState by remember { RPCSX.state }
    val emulatorActiveGame by remember { RPCSX.activeGame }

    val installPkgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) PrecompilerService.start(
                context,
                PrecompilerServiceAction.Install,
                uri
            )
        }
    )

    val installFwLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) PrecompilerService.start(
                context,
                PrecompilerServiceAction.InstallFirmware,
                uri
            )
        }
    )

    val gameFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            uri?.let {
                // TODO: FileUtil.saveGameFolderUri(prefs, it)
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                FileUtil.installPackages(context, it)
            }
        }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(
                            rememberScrollState()
                        )
                ) {
                    Spacer(Modifier.height(12.dp))

                    NavigationDrawerItem(
                        label = {
                            Text(
                                "Firmware: " + (FirmwareRepository.version.value ?: "None")
                            )
                        },
                        selected = false,
                        icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
                        badge = {
                            val progressChannel = FirmwareRepository.progressChannel
                            val progress = ProgressRepository.getItem(progressChannel.value)
                            val progressValue = progress?.value?.value
                            val maxValue = progress?.value?.max
                            Log.e("Main", "Update $progressChannel, $progress")
                            if (progressValue != null && maxValue != null) {
                                if (maxValue.longValue != 0L) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(32.dp),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        progress = {
                                            progressValue.longValue.toFloat() / maxValue.longValue.toFloat()
                                        },
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .width(32.dp)
                                            .height(32.dp),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                            }
                        }, // Placeholder
                        onClick = {
                            if (FirmwareRepository.progressChannel.value == null) {
                                installFwLauncher.launch("*/*")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = navigateToSettings
                    )

                    NavigationDrawerItem(
                        label = { Text("Edit Overlay") },
                        selected = false,
                        icon = { Icon(painter = painterResource(id = R.drawable.ic_show_osc), null) },
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    OverlayEditActivity::class.java
                                )
                            )
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text("System Info") },
                        selected = false,
                        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = {
                            AlertDialogQueue.showDialog(
                                "System Info", 
                                RPCSX.instance.systemInfo(), 
                                confirmText = "Copy", 
                                dismissText = "Cancel", 
                                onConfirm = { 
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("System Info", RPCSX.instance.systemInfo())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            "RPCSX",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) {
                                    drawerState.open()
                                } else {
                                    drawerState.close()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open menu"
                            )
                        }
                    },
                    actions = {
                        if (emulatorActiveGame != null && emulatorState != EmulatorState.Stopped && emulatorState != EmulatorState.Stopping) {
                            IconButton(onClick = {
                                emulatorState = EmulatorState.Stopped
                                RPCSX.instance.kill()
                            }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_stop),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                DropUpFloatingActionButton(installPkgLauncher, gameFolderPickerLauncher)
            },
        ) { innerPadding -> Column(modifier = Modifier.padding(innerPadding)) { GamesScreen() } }
    }
}

@Composable
fun DropUpFloatingActionButton(
    installPkgLauncher: ActivityResultLauncher<String>,
    gameFolderPickerLauncher: ActivityResultLauncher<Uri?>
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.padding(16.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomEnd
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = { installPkgLauncher.launch("*/*"); expanded = false },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_description),
                            contentDescription = "Select Game"
                        )
                    }
                    FloatingActionButton(
                        onClick = { gameFolderPickerLauncher.launch(null); expanded = false },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_folder),
                            contentDescription = "Select Folder"
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { expanded = !expanded }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}

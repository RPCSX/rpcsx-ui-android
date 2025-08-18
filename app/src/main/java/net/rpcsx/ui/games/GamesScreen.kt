package net.rpcsx.ui.games

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import net.rpcsx.BuildConfig
import net.rpcsx.EmulatorState
import net.rpcsx.FirmwareRepository
import net.rpcsx.Game
import net.rpcsx.GameFlag
import net.rpcsx.GameInfo
import net.rpcsx.GameProgress
import net.rpcsx.GameProgressType
import net.rpcsx.GameRepository
import net.rpcsx.ProgressRepository
import net.rpcsx.R
import net.rpcsx.RPCSX
import net.rpcsx.RPCSXActivity
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.utils.FileUtil
import net.rpcsx.utils.RpcsxUpdater
import net.rpcsx.utils.UiUpdater
import java.io.File
import kotlin.concurrent.thread

private fun withAlpha(color: Color, alpha: Float): Color {
    return Color(
        red = color.red, green = color.green, blue = color.blue, alpha = alpha
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameItem(game: Game) {
    val context = LocalContext.current
    val menuExpanded = remember { mutableStateOf(false) }
    val iconExists = remember { mutableStateOf(false) }
    val emulatorState by remember { RPCSX.state }
    val emulatorActiveGame by remember { RPCSX.activeGame }

    val installKeyLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                val fd = descriptor?.parcelFileDescriptor?.fd

                if (fd != null) {
                    val installProgress = ProgressRepository.create(context, context.getString(R.string.license_installation))

                    game.addProgress(GameProgress(installProgress, GameProgressType.Compile))

                    thread(isDaemon = true) {
                        if (!RPCSX.instance.installKey(fd, installProgress, game.info.path)) {
                            try {
                                ProgressRepository.onProgressEvent(installProgress, -1, 0)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        try {
                            descriptor.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    try {
                        descriptor?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

    Column {
        DropdownMenu(
            expanded = menuExpanded.value, onDismissRequest = { menuExpanded.value = false }) {
            if (game.progressList.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded.value = false
                        val deleteProgress = ProgressRepository.create(context, context.getString(R.string.deleting_game))
                        game.addProgress(GameProgress(deleteProgress, GameProgressType.Compile))
                        ProgressRepository.onProgressEvent(deleteProgress, 1, 0L)
                        val path = File(game.info.path)
                        if (path.exists()) {
                            path.deleteRecursively()
                            FileUtil.deleteCache(
                                context,
                                game.info.path.substringAfterLast("/")
                            ) { success ->
                                if (!success) {
                                    AlertDialogQueue.showDialog(
                                        title = context.getString(R.string.unexpected_error),
                                        message = context.getString(R.string.failed_to_delete_game_cache),
                                        confirmText = context.getString(R.string.close),
                                        dismissText = ""
                                    )
                                }
                                ProgressRepository.onProgressEvent(deleteProgress, 100, 100)
                                GameRepository.remove(game)
                            }
                        }
                    }
                )
            }
        }

        Card(
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = click@{
                    if (game.hasFlag(GameFlag.Locked)) {
                        AlertDialogQueue.showDialog(
                            title = context.getString(R.string.missing_key),
                            message = context.getString(R.string.game_require_key),
                            onConfirm = { installKeyLauncher.launch("*/*") },
                            onDismiss = {},
                            confirmText = context.getString(R.string.install_rap_file)
                        )

                        return@click
                    }

                    if (FirmwareRepository.version.value == null) {
                        AlertDialogQueue.showDialog(
                            title = context.getString(R.string.missing_firmware),
                            message = context.getString(R.string.install_firmware_to_continue)
                        )
                    } else if (FirmwareRepository.progressChannel.value != null) {
                        AlertDialogQueue.showDialog(
                            title = context.getString(R.string.missing_firmware),
                            message = context.getString(R.string.wait_until_firmware_install)
                        )
                    } else if (game.info.path != "$" && game.findProgress(
                            arrayOf(
                                GameProgressType.Install, GameProgressType.Remove
                            )
                        ) == null
                    ) {
                        if (game.findProgress(GameProgressType.Compile) != null) {
                            AlertDialogQueue.showDialog(
                                title = context.getString(R.string.game_compiling_not_finished),
                                message = context.getString(R.string.wait_until_game_compile)
                            )
                        } else {
                            GameRepository.onBoot(game)
                            val emulatorWindow = Intent(
                                context, RPCSXActivity::class.java
                            )
                            emulatorWindow.putExtra("path", game.info.path)
                            context.startActivity(emulatorWindow)
                        }
                    }
                }, onLongClick = {
                    if (game.info.name.value != "VSH") {
                        menuExpanded.value = true
                    }
                })
        ) {
            if (game.info.iconPath.value != null && !iconExists.value) {
                if (game.progressList.isNotEmpty()) {
                    val progressId = ProgressRepository.getItem(game.progressList.first().id)
                    if (progressId != null) {
                        val progressValue = progressId.value.value
                        val progressMax = progressId.value.max

                        iconExists.value =
                            (progressMax.longValue != 0L && progressValue.longValue == progressMax.longValue) || File(
                                game.info.iconPath.value!!
                            ).exists()
                    }
                } else {
                    iconExists.value = File(game.info.iconPath.value!!).exists()
                }
            }

            Box(
                modifier = Modifier
                    .height(110.dp)
                    .align(alignment = Alignment.CenterHorizontally)
                    .fillMaxSize()
            ) {
                if (game.info.iconPath.value != null && iconExists.value) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = game.info.iconPath.value,
                            contentScale = if (game.info.name.value == "VSH") ContentScale.Fit else ContentScale.Crop,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )
                    }
                }

                if (game.progressList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(withAlpha(Color.DarkGray, 0.6f))
                    ) {}

                    val progressChannel = game.progressList.first().id
                    val progress = ProgressRepository.getItem(progressChannel)
                    val progressValue = progress?.value?.value
                    val maxValue = progress?.value?.max

                    if (progressValue != null && maxValue != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (maxValue.longValue != 0L) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(64.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    progress = {
                                        progressValue.longValue.toFloat() / maxValue.longValue.toFloat()
                                    },
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(64.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                } else if (emulatorState == EmulatorState.Paused && emulatorActiveGame == game.info.path) {
                    Card(modifier = Modifier.padding(5.dp)) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_play),
                            contentDescription = null
                        )
                    }
                }

                if (game.hasFlag(GameFlag.Locked) || game.hasFlag(GameFlag.Trial)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Card(
                            onClick = {
                                installKeyLauncher.launch("*/*")
                            }) {

                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Game is locked",
                                modifier = Modifier
                                    .size(30.dp)
                                    .padding(7.dp)
                            )
                        }
                    }
                }

//                val name = game.info.name.value
//                if (name != null) {
//                    Row(
//                        verticalAlignment = Alignment.Bottom,
//                        horizontalArrangement = Arrangement.Center,
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        Text(name, textAlign = TextAlign.Center)
//                    }
//                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen() {
    val context = LocalContext.current
    val games = remember { GameRepository.list() }
    val isRefreshing by remember { GameRepository.isRefreshing }
    val state = rememberPullToRefreshState()
    var uiUpdateVersion by remember { mutableStateOf<String?>(null) }
    var uiUpdate by remember { mutableStateOf(false) }
    var uiUpdateProgressValue by remember { mutableLongStateOf(0) }
    var uiUpdateProgressMax by remember { mutableLongStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val rpcsxLibrary by remember { RPCSX.activeLibrary }
    var rpcsxInstallLibraryFailed by remember { mutableStateOf(false) }
    var rpcsxUpdateVersion by remember { mutableStateOf<String?>(null) }
    var rpcsxUpdate by remember { mutableStateOf(false) }
    var rpcsxUpdateProgressValue by remember { mutableLongStateOf(0) }
    var rpcsxUpdateProgressMax by remember { mutableLongStateOf(0) }
    val activeDialogs = remember { AlertDialogQueue.dialogs }

    val gameInProgress = games.find { it.progressList.isNotEmpty() }

    val checkForUpdates = suspend {
        rpcsxUpdateVersion = RpcsxUpdater.checkForUpdate()
        uiUpdateVersion = UiUpdater.checkForUpdate(context)

        if (rpcsxUpdateVersion == null && rpcsxLibrary == null) {
            rpcsxInstallLibraryFailed = true
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdates()
    }

    if (uiUpdateVersion != null && rpcsxUpdateVersion == null && activeDialogs.isEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!uiUpdate) uiUpdateVersion = null },
            title = {
                Text(
                    if (uiUpdate) stringResource(R.string.downloading_ui, uiUpdateVersion!!)
                    else stringResource(R.string.ui_update_available)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiUpdate) {
                        if (uiUpdateProgressMax == 0L) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                { uiUpdateProgressValue / uiUpdateProgressMax.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.current_and_new_version, BuildConfig.Version, uiUpdateVersion!!))
                    }
                }
            },
            confirmButton = {
                if (!uiUpdate) {
                    TextButton(onClick = {
                        uiUpdate = true

                        coroutineScope.launch {
                            val file = UiUpdater.downloadUpdate(
                                context,
                                File("${context.getExternalFilesDir(null)!!.absolutePath}/cache/")
                            ) { value, max ->
                                uiUpdateProgressValue = value
                                uiUpdateProgressMax = max
                            }
                            uiUpdate = false
                            uiUpdateVersion = null

                            if (file != null) {
                                UiUpdater.installUpdate(context, file)
                            }
                        }
                    }) {
                        Text(stringResource(R.string.update))
                    }
                }
            })
    }

    if (rpcsxLibrary == null && rpcsxUpdateVersion == null && !rpcsxUpdate && activeDialogs.isEmpty()) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.missing_rpcsx_lib)) },
            text = { Text(stringResource(R.string.downloading_latest_rpcsx)) },
            confirmButton = {}
        )
    }

    if (rpcsxUpdateVersion != null && activeDialogs.isEmpty()) {
        val startUpdate = {
            rpcsxUpdate = true

            coroutineScope.launch {
                val file = RpcsxUpdater.downloadUpdate(
                    File(context.filesDir.canonicalPath)
                ) { value, max ->
                    rpcsxUpdateProgressValue = value
                    rpcsxUpdateProgressMax = max
                }

                if (file != null) {
                    RpcsxUpdater.installUpdate(context, file)
                } else if (rpcsxLibrary == null) {
                    rpcsxInstallLibraryFailed = true
                }

                rpcsxUpdate = false
                rpcsxUpdateVersion = null
            }
        }

        if (rpcsxLibrary == null) {
            startUpdate()
        }

        AlertDialog(
            onDismissRequest = {
                if (!rpcsxUpdate && rpcsxLibrary != null) rpcsxUpdateVersion = null
            },
            title = {
                Text(
                    if (rpcsxUpdate) stringResource(R.string.downloading_rpcsx, rpcsxUpdateVersion!!)
                    else stringResource(R.string.rpcsx_update_available)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (rpcsxUpdate) {
                        if (rpcsxUpdateProgressMax == 0L) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                { rpcsxUpdateProgressValue / rpcsxUpdateProgressMax.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.current_and_new_version,
                                RpcsxUpdater.getCurrentVersion() ?: stringResource(R.string.none),
                                rpcsxUpdateVersion!!
                            )
                        )
                    }
                }
            },
            confirmButton = {
                if (!rpcsxUpdate) {
                    TextButton(onClick = {
                        startUpdate()
                    }) {
                        Text(stringResource(R.string.update))
                    }
                }
            })
    }

    if (rpcsxInstallLibraryFailed) {
        val installRpcsxLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    rpcsxInstallLibraryFailed = false

                    val target = File(context.filesDir.canonicalPath, "librpcsx_unknown_unknown.so")
                    if (target.exists()) {
                        target.delete()
                    }

                    FileUtil.saveFile(context, uri, target.path)

                    if (RPCSX.instance.getLibraryVersion(target.path) != null) {
                        RpcsxUpdater.installUpdate(context, target)
                    } else {
                        rpcsxInstallLibraryFailed = true
                    }
                }
            }

        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.failed_to_download_rpcsx)) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    rpcsxInstallLibraryFailed = false
                    coroutineScope.launch { checkForUpdates() }
                }) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    installRpcsxLauncher.launch("*/*")
                }) {
                    Text(stringResource(R.string.install_custom_version))
                }
            })
    }

    if (rpcsxLibrary == null) {
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = state,
        onRefresh = {
            if (gameInProgress == null && !isRefreshing) {
                GameRepository.queueRefresh()
            }
        },
        indicator = {
            if (gameInProgress == null) {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp * 0.6f),
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = games.size, key = { index -> games[index].info.path }) { index ->
                GameItem(games[index])
            }
        }
    }
}

@Preview
@Composable
fun GamesScreenPreview() {
    listOf(
        "Minecraft", "Skate 3", "Mirror's Edge", "Demon's Souls"
    ).forEach { x -> GameRepository.addPreview(arrayOf(GameInfo(x, x))) }

    GamesScreen()
}

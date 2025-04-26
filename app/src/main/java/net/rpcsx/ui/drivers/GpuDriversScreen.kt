package net.rpcsx.ui.drivers

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rpcsx.RPCSX
import net.rpcsx.dialogs.AlertDialogQueue
import net.rpcsx.ui.channels.DefaultGpuDriverChannel
import net.rpcsx.ui.settings.components.core.DeletableListItem
import net.rpcsx.utils.DriversFetcher
import net.rpcsx.utils.GitHub
import net.rpcsx.utils.GeneralSettings
import net.rpcsx.utils.GeneralSettings.string
import net.rpcsx.utils.GpuDriverHelper
import net.rpcsx.utils.GpuDriverInstallResult
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpuDriversScreen(navigateBack: () -> Unit) {
    val context = LocalContext.current
    var drivers by remember { mutableStateOf(GpuDriverHelper.getInstalledDrivers(context)) }
    var selectedDriver by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showDriverDialog by remember { mutableStateOf(false) }
    var shouldFetchAndShowDrivers by remember { mutableStateOf(false) }
    var repoUrl by remember { mutableStateOf<String?>(null) }
    var driverToDownload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var shouldDownloadDriver by remember { mutableStateOf(false) }

    val driverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isInstalling = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val result = GpuDriverHelper.installDriver(context, stream)
                        if (result == GpuDriverInstallResult.Success) {
                            val updatedDrivers = GpuDriverHelper.getInstalledDrivers(context)
                            withContext(Dispatchers.Main) {
                                drivers = updatedDrivers
                            }
                        }
                        withContext(Dispatchers.Main) {
                            isInstalling = false
                            snackbarHostState.showSnackbar(
                                message = GpuDriverHelper.resolveInstallResultToString(result),
                                actionLabel = "Dismiss",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GpuDriver", "Error installing driver: ${e.message}")
                }
            }
        }
    }

    selectedDriver = GeneralSettings["selected_gpu_driver"].string("Default")

    if (showDriverDialog) {
        DriverDialog(onDismiss = { showDriverDialog = false }, onInstallClick = {
            driverPickerLauncher.launch("application/zip")
        }, onImportClick = {
            repoUrl = GeneralSettings["gpu_driver_channel"].string(DefaultGpuDriverChannel)
            shouldFetchAndShowDrivers = true
        })
    }

    if (shouldFetchAndShowDrivers) {
        FetchAndShowDrivers(
            repoUrl = repoUrl!!,
            bypassValidation = false,
            onDismiss = { shouldFetchAndShowDrivers = false },
            onDownloadDriver = { url, name ->
                driverToDownload = Pair(url, name)
                shouldDownloadDriver = true
            })
    }

    if (shouldDownloadDriver) {
        DownloadDriver(
            chosenUrl = driverToDownload!!.first,
            chosenName = driverToDownload!!.second,
            onDismiss = {
                shouldDownloadDriver = false
                coroutineScope.launch(Dispatchers.IO) {
                    val updatedDrivers = GpuDriverHelper.getInstalledDrivers(context)
                    withContext(Dispatchers.Main) {
                        drivers = updatedDrivers
                    }
                }
            })
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text(text = "GPU Drivers", fontWeight = FontWeight.Medium) },
            scrollBehavior = topBarScrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = navigateBack
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft, null)
                }
            })
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Select a GPU Driver",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(drivers.entries.toList(), key = { it.key }) { (file, metadata) ->
                    DeletableListItem(onDelete = if (metadata.name == "Default") null else ({
                        coroutineScope.launch(Dispatchers.IO) {
                            if (file.deleteRecursively()) {
                                coroutineScope.launch {
                                    drivers = GpuDriverHelper.getInstalledDrivers(context)
                                }
                            }
                        }
                    })) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val path = if (metadata.name == "Default") "" else file.path

                                    Log.w("Driver", "path $path, internal data dir ${context.filesDir}")
                                    if (!RPCSX.instance.setCustomDriver(path, metadata.libraryName, RPCSX.nativeLibDirectory)) {
                                        AlertDialogQueue.showDialog("Custom Driver Error", "Failed to load selected driver")
                                    } else {
                                        selectedDriver = metadata.label     
                                        GeneralSettings.setValue("selected_gpu_driver", selectedDriver ?: "")
                                        GeneralSettings["gpu_driver_path"] = path
                                        GeneralSettings["gpu_driver_name"] = metadata.libraryName
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (metadata.label == selectedDriver) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = metadata.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (metadata.label == selectedDriver) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = metadata.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (metadata.label == selectedDriver) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showDriverDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInstalling) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isInstalling,
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
                    ) {
                        if (isInstalling) {
                            Text("Installing...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Install Driver"
                            )
                        }
                    }

                }
            }

        }
    }
}

@Composable
fun DriverDialog(
    onDismiss: () -> Unit, onInstallClick: () -> Unit, onImportClick: () -> Unit
) {
    val items = listOf(
        "Download", "Install"
    )
    var selectedItemIndex by remember { mutableIntStateOf(0) }

    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(text = "Choose")
    }, text = {
        Column {
            items.forEachIndexed { index, text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedItemIndex = index }
                        .padding(8.dp)) {
                    RadioButton(
                        selected = selectedItemIndex == index,
                        onClick = { selectedItemIndex = index })
                    Text(text = text, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (selectedItemIndex == 1) {
                onInstallClick()
            } else {
                onImportClick()
            }
            onDismiss()
        }) {
            Text(text = stringResource(android.R.string.ok))
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(android.R.string.cancel))
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetchAndShowDrivers(
    repoUrl: String,
    bypassValidation: Boolean = false,
    onDismiss: () -> Unit,
    onDownloadDriver: (String, String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var fetchResult by remember { mutableStateOf<GitHub.FetchResult?>(null) }
    var fetchedDrivers by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    var chosenIndex by remember { mutableIntStateOf(0) }
    val scrollState = rememberScrollState()
    val hasScrolled = remember { derivedStateOf { scrollState.value > 0 } }

    LaunchedEffect(Unit) {
        val fetchOutput = DriversFetcher.fetchReleases(repoUrl, bypassValidation)
        isLoading = false

        if (fetchOutput is GitHub.FetchResult.Success<*>) {
            fetchedDrivers = fetchOutput.content as List<Pair<String, String?>>
            fetchResult = null
        } else {
            fetchResult = fetchOutput
        }
    }

    fetchResult?.let {
        val errorMessage = when (it) {
            is GitHub.FetchResult.Error -> it.message
            else -> "Something unexpected occurred while fetching $repoUrl drivers"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.ok))
                }
            })
        return
    }

    if (isLoading) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Fetching") }, text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Please wait")
            }
        }, confirmButton = {})
        return
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxHeight = if (isLandscape) 168.dp else 300.dp

    BasicAlertDialog(
        onDismissRequest = onDismiss, content = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Text(
                        "Drivers",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (hasScrolled.value) {
                        HorizontalDivider()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .verticalScroll(scrollState)
                    ) {
                        fetchedDrivers.forEachIndexed { index, driver ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chosenIndex = index }
                                    .padding(vertical = 4.dp, horizontal = 16.dp)) {
                                RadioButton(
                                    selected = chosenIndex == index,
                                    onClick = { chosenIndex = index })
                                Text(text = driver.first, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }

                    if (hasScrolled.value) {
                        HorizontalDivider()
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val chosenDriver = fetchedDrivers[chosenIndex]
                                onDownloadDriver(chosenDriver.second!!, chosenDriver.first)
                                onDismiss()
                            }, modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(text = "Import")
                        }
                    }
                }
            }
        })
}

@Composable
fun DownloadDriver(
    chosenUrl: String, chosenName: String, onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var isIndeterminate by remember { mutableStateOf(true) }
    var downloadCompleted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val driverFile =
                File("${context.getExternalFilesDir(null)!!.absolutePath}/cache/$chosenName.zip")
            if (!driverFile.exists()) driverFile.createNewFile()

            val result =
                GitHub.downloadAsset(
                    chosenUrl, 
                    driverFile,       
                    { downloadedBytes, totalBytes ->
                        if (totalBytes > 0) {
                            isIndeterminate = false
                            progress = downloadedBytes.toFloat() / totalBytes
                        }
                    },
                    4 // threads
                ) 

            if (result is GitHub.DownloadStatus.Success) {
                withContext(Dispatchers.Main) {
                    val installResult = withContext(Dispatchers.IO) {
                        GpuDriverHelper.installDriver(context, FileInputStream(driverFile))
                    }
                    Toast.makeText(
                        context,
                        GpuDriverHelper.resolveInstallResultToString(installResult),
                        Toast.LENGTH_LONG
                    ).show()
                    downloadCompleted = true
                    if (installResult == GpuDriverInstallResult.Success) {
                        onDismiss()
                    }
                }
            } else if (result is GitHub.DownloadStatus.Error) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to import $chosenName: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            }

            driverFile.delete()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isIndeterminate) onDismiss() },
        title = { Text("Downloading") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // we can't set indeterminate explicitly so.
                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!isIndeterminate) {
                    Text(text = "${(progress * 100).toInt()}%")
                }
            }
        },
        confirmButton = {
            if (downloadCompleted) {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        })
}

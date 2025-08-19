package net.rpcsx.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.rpcsx.R
import net.rpcsx.ui.settings.components.core.DeletableListItem


@Composable
fun RepositoryAddDialog(onDismiss: () -> Unit, onAdd: (path: String) -> Unit) {
    var textInputValue by remember { mutableStateOf("https://github.com/") }

    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.enter_repo_url))
    }, text = {
        Column {
            OutlinedTextField(
                value = textInputValue,
                onValueChange = { textInputValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (textInputValue.isNotEmpty()) {
                onAdd(textInputValue)
            }
            onDismiss()
        }) {
            Text(stringResource(R.string.add))
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(android.R.string.cancel))
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateChannelListScreen(
    navigateBack: () -> Unit,
    title: String,
    selected: String?,
    items: List<String>,
    onDelete: (value: String) -> Unit,
    onAdd: (value: String) -> Unit,
    onSelect: (value: String) -> Unit,
    isDeletable: (value: String) -> Boolean = { true },
    actions: @Composable RowScope.() -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showRepositoryAddDialog by remember { mutableStateOf(false) }

    if (showRepositoryAddDialog) {
        RepositoryAddDialog(onDismiss = { showRepositoryAddDialog = false }, onAdd = { url ->
            showRepositoryAddDialog = false

            onAdd(url)
        })
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text(text = title, fontWeight = FontWeight.Medium) },
            scrollBehavior = topBarScrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = navigateBack
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft, null)
                }
            },
            actions = actions
        )
    }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.select_repo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    DeletableListItem(onDelete = if (!isDeletable(item)) null else ({
                        onDelete(item)
                    })) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSelect(item)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (item == selected) {
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
                                    text = item,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (item == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showRepositoryAddDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showRepositoryAddDialog) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !showRepositoryAddDialog,
                        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add, contentDescription = "Add Source"
                        )
                    }
                }
            }

        }
    }
}

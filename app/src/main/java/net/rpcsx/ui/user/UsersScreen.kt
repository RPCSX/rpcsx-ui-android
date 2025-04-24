package net.rpcsx.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import net.rpcsx.EmulatorState
import net.rpcsx.RPCSX
import net.rpcsx.User
import net.rpcsx.UserRepository
import net.rpcsx.dialogs.AlertDialogQueue

@Composable
fun UserItem(
    user: User,
    isActive: Boolean,
    setActive: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clickable(
                enabled = !isActive
            ) {
                setActive()
            }
    ) {
        RadioButton(
            selected = isActive,
            onClick = setActive
        )
        Text(
            text = user.username,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = user.userId,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    navigateBack: () -> Unit
) {
    val users = remember { UserRepository.users }
    val activeUser by remember { UserRepository.activeUser }
    val emulatorState by remember { RPCSX.state }

    LaunchedEffect(Unit) {
        UserRepository.load()
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                title = {
                    Text("Users")
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(8.dp)
            ) {
                items(
                    count = users.size,
                    key = { index -> users.toList()[index].first }
                ) {
                    val user = users.values.elementAt(it)
                    UserItem(
                        user = user,
                        isActive = user.userId == activeUser,
                        setActive = {
                            if (emulatorState != EmulatorState.Stopped) {
                                AlertDialogQueue.showDialog(
                                    title = "Stop Emulator?",
                                    message = "In order to change the user you have to stop the emulator first.\n\nStop the emulator now?",
                                    onConfirm = {
                                        RPCSX.instance.kill()
                                        RPCSX.updateState()
                                        UserRepository.loginUser(user.userId)
                                    }
                                )
                            } else {
                                UserRepository.loginUser(user.userId)
                            }
                        },
                    )
                }
                item {
                    Box(Modifier.height(LocalConfiguration.current.screenHeightDp.dp * 0.4f))
                }
            }
        }
    }
}

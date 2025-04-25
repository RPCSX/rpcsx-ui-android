package net.rpcsx

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.isDigitsOnly
import net.rpcsx.utils.GeneralSettings
import java.io.File
import kotlin.concurrent.thread

data class User(
    val userId: String,
    val userDir: String,
    val username: String
)

private fun toUser(userId: String): User {
    val userDir = File(RPCSX.getHdd0Dir()).resolve("home").resolve(userId)

    var userName = userDir.resolve("localusername").readText()

    if (userName.length > 16) {
        userName = userName.substring(0, 16)
    }

    return User(userId, userDir.path, userName)
}

class UserRepository {
    private val users = mutableStateMapOf<Int, User>()
    private var activeUser = mutableStateOf("")

    companion object {
        private val instance = UserRepository()

        val users = instance.users
        val activeUser = instance.activeUser

        fun getUsername(userId: String): String? {
            return users.values.firstOrNull { it.userId == userId }?.username
        }

        fun load() {
            updateList()
            instance.activeUser.value = getUserFromSettings()
            if (instance.activeUser.value != RPCSX.instance.getUser()) {
                instance.activeUser.value = RPCSX.instance.getUser()
            }
        }

        fun getUserFromSettings(): String {
            return GeneralSettings["active_user"] as? String ?: "00000001"
        }

        private fun updateList() {
            users.clear()
            users.putAll(getUserAccounts())
        }

        private fun checkUser(directory: String): Int {
            return if (directory.isDigitsOnly() && directory.length == 8) {
                directory.toInt()
            } else {
                0
            }
        }

        private fun generateUser(userId: String, userName: String) {
            assert(checkUser(userId) > 0)

            val homeDir = RPCSX.getHdd0Dir() + "home/"
            val userDir = homeDir + userId
            File(homeDir).mkdir()
            File(userDir).mkdir()
            File(userDir, "exdata").mkdir()
            File(userDir, "savedata").mkdir()
            File(userDir, "trophy").mkdir()
            File(userDir, "localusername").writeText(userName)
        }

        fun createUser(username: String) {
            var smallest = 1

            for (user in instance.users) {
                if (user.key > smallest) break
                smallest++
            }

            if (smallest >= 100000000) {
                return
            }

            val nextUserId = "%08d".format(smallest)
            assert(checkUser(nextUserId) > 0)

            if (!validateUsername(username)) {
                return
            }

            generateUser(nextUserId, username)
            updateList()
        }

        fun removeUser(userId: String) {
            if (instance.activeUser.value == userId) return

            val key = checkUser(userId)

            if (key == 0) return

            instance.users[key]?.also {
                File(it.userDir).deleteRecursively()
            }

            updateList()
        }

        fun renameUser(userId: String, username: String) {
            val key = checkUser(userId)
            if (key == 0) return

            val usernameFile = File(RPCSX.getHdd0Dir()).resolve("home").resolve(userId).resolve("localusername")
            if (!validateUsername(username)) {
                return
            }

            usernameFile.writeText(username)
            updateList()
        }

        fun loginUser(userId: String) {
            RPCSX.instance.loginUser(userId)
            instance.activeUser.value = userId
            GeneralSettings.setValue("active_user", userId)
            GameRepository.queueRefresh()
        }

        fun validateUsername(textToValidate: String): Boolean {
            return textToValidate.matches(Regex("^[A-Za-z0-9]{3,16}$"))
        }

        private fun getUserAccounts(): HashMap<Int, User> {
            val userList: HashMap<Int, User> = hashMapOf()

            File(RPCSX.getHdd0Dir()).resolve("home").listFiles()?.let {
                for (userDir in it) {
                    if (!userDir.isDirectory) continue

                    val key = checkUser(userDir.name)

                    if (key == 0) continue

                    if (!userDir.resolve("localusername").isFile) continue

                    userList[key] = toUser(userDir.name)
                }
            }

            return userList
        }
    }
}

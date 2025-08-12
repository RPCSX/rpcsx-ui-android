package net.rpcsx.utils

import android.net.Uri
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.content.ActivityNotFoundException
import android.database.Cursor
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rpcsx.GameInfo
import net.rpcsx.GameRepository
import net.rpcsx.PrecompilerService
import net.rpcsx.PrecompilerServiceAction
import net.rpcsx.ProgressRepository
import net.rpcsx.RPCSX
import net.rpcsx.provider.AppDataDocumentProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import kotlin.concurrent.thread

private data class InstallableFolder(
    val uri: Uri, val targetPath: String
)

object FileUtil {
    fun installPackages(context: Context, rootFolderUri: Uri) {
        thread {
            val workList = mutableListOf<Uri>()
            workList.add(rootFolderUri)

            val batchFiles = mutableListOf<Uri>()
            val batchDirs = mutableListOf<InstallableFolder>()

            while (workList.isNotEmpty()) {
                val currentFolderUri = workList.removeAt(0)

                val paramSfo =
                    uriOpenFile(context, currentFolderUri, "PS3_GAME/PARAM.SFO") ?: uriOpenFile(
                        context, currentFolderUri, "PARAM.SFO"
                    )

                if (paramSfo != null) {
                    val installDir =
                        RPCSX.instance.getDirInstallPath(paramSfo.parcelFileDescriptor.fd)
                    paramSfo.close()

                    if (installDir != null) {
                        batchDirs += InstallableFolder(currentFolderUri, installDir)
                    } else {
                        workList.add(currentFolderUri)
                    }

                    continue
                }

                listFiles(currentFolderUri, context).forEach { item ->
                    if (item.isDirectory) {
                        workList.add(item.uri)
                    } else {
                        batchFiles += item.uri
                    }
                }
            }

            if (batchFiles.isNotEmpty()) {
                PrecompilerService.start(
                    context, PrecompilerServiceAction.Install, ArrayList(batchFiles)
                )
            }

            batchDirs.forEach {
                if (GameRepository.find(it.targetPath) != null) {
                    return@forEach
                }

                val progress = ProgressRepository.create(context, "Installing Directory")
                GameRepository.add(arrayOf(GameInfo("$")), progress)
                copyDirUriToInternalStorage(context, it.uri, it.targetPath, progress)
                RPCSX.instance.collectGameInfo(it.targetPath, -1L)
            }
        }
    }

    fun saveGameFolderUri(prefs: SharedPreferences, uri: Uri) {
        prefs.edit { putString("selected_game_folder", uri.toString()) }
    }

    fun copyDirUriToInternalStorage(
        context: Context, rootFolderUri: Uri, path: String, progressId: Long
    ) {
        val workList = mutableListOf<Pair<Uri, String>>()
        workList.add(Pair(rootFolderUri, path))
        val fileList = mutableListOf<Pair<Uri, String>>()

        while (workList.isNotEmpty()) {
            val currentFolderUriTarget = workList.removeAt(0)
            val currentFolderUri = currentFolderUriTarget.first
            val currentFolderTarget = currentFolderUriTarget.second

            listFiles(currentFolderUri, context).forEach { item ->
                val file = File(currentFolderTarget, item.filename)
                if (item.isDirectory) {
                    file.mkdirs()
                    workList.add(Pair(item.uri, file.path))
                } else {
                    fileList.add(Pair(item.uri, file.path))
                }
            }
        }

        ProgressRepository.onProgressEvent(progressId, 0, fileList.size.toLong())
        var processed = 0L

        fileList.forEach { file ->
            saveFile(context, file.first, file.second)
            ProgressRepository.onProgressEvent(progressId, ++processed, fileList.size.toLong())
        }
    }

    fun saveFile(context: Context, source: Uri, target: String) {
        var bis: BufferedInputStream? = null
        var bos: BufferedOutputStream? = null

        try {
            bis = BufferedInputStream(
                FileInputStream(
                    context.contentResolver.openFileDescriptor(
                        source, "r"
                    )!!.fileDescriptor
                )
            )

            bos = BufferedOutputStream(FileOutputStream(target, false))
            val buf = ByteArray(1024)
            bis.read(buf)

            do {
                bos.write(buf)
            } while (bis.read(buf) != -1)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            bis?.close()
            bos?.close()
        }
    }

    fun uriChild(context: Context, rootUri: Uri, path: String): SimpleDocument? {
        val pathDirectories = path.split("/").toMutableList()
        var uri = rootUri
        val filename = pathDirectories.removeAt(pathDirectories.size - 1)

        while (pathDirectories.isNotEmpty()) {
            val dirName = pathDirectories.removeAt(0)
            val entry = listFiles(uri, context).find { it.filename == dirName }
            if (entry == null || !entry.isDirectory) {
                return null
            }

            uri = entry.uri
        }

        return listFiles(uri, context).find { it.filename == filename }
    }

    fun uriOpenFile(context: Context, rootUri: Uri, path: String): AssetFileDescriptor? {
        val entry = uriChild(context, rootUri, path)

        if (entry == null || entry.isDirectory) {
            return null
        }

        return context.contentResolver.openAssetFileDescriptor(entry.uri, "r")
    }

    fun listFiles(uri: Uri, context: Context): Array<SimpleDocument> {
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        var c: Cursor? = null
        val results: MutableList<SimpleDocument> = ArrayList()
        try {
            val docId = if (isRootTreeUri(uri)) {
                DocumentsContract.getTreeDocumentId(uri)
            } else {
                DocumentsContract.getDocumentId(uri)
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
            c = context.contentResolver.query(childrenUri, columns, null, null, null)
            while (c!!.moveToNext()) {
                val documentId = c.getString(0)
                val documentName = c.getString(1)
                val documentMimeType = c.getString(2)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                val document = SimpleDocument(documentName, documentMimeType, documentUri)
                results.add(document)
            }
        } catch (e: Exception) {
            Log.e("FileUtil", "Cannot list file error: " + e.message)
        } finally {
            c?.close()
        }
        return results.toTypedArray<SimpleDocument>()
    }

    fun isRootTreeUri(uri: Uri): Boolean {
        val paths = uri.pathSegments
        return paths.size == 2 && "tree" == paths[0]
    }

    fun deleteCache(ctx: Context, gameId: String, onComplete: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = File(ctx.getExternalFilesDir(null)!!, "cache/cache/$gameId").deleteRecursively()
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    fun importConfig(ctx: Context, uri: Uri): Boolean {
        return try {
            val docFile = DocumentFile.fromSingleUri(ctx, uri)
            if (docFile == null || (docFile.name?.endsWith(".yml", true) != true)) return false
            val inputStream: InputStream = ctx.contentResolver.openInputStream(uri) ?: return false
            val outputFile: File = ctx.getExternalFilesDir(null)?.resolve("config")?.resolve("config.yml") ?: return false
            val outputStream: OutputStream = outputFile.outputStream()
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportConfig(ctx: Context, uri: Uri): Boolean {
        return try {
            val inputFile = ctx.getExternalFilesDir(null)?.resolve("config")?.resolve("config.yml") ?: return false
            val inputStream: InputStream = inputFile.inputStream()
            val outputStream: OutputStream = ctx.contentResolver.openOutputStream(uri) ?: return false
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
           true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun launchInternalDir(ctx: Context): Boolean {
        if (!ctx.launchBrowseIntent(Intent.ACTION_VIEW)) {
            if (!ctx.launchBrowseIntent()) {
                if (!ctx.launchBrowseIntent(Intent.ACTION_OPEN_DOCUMENT_TREE)) {
                    return false
                }
            }
        }
        return true
    }

    private fun Context.launchBrowseIntent(
        action: String = "android.provider.action.BROWSE"
    ): Boolean {
        return try {
            val intent = Intent(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                data = DocumentsContract.buildRootUri(
                    AppDataDocumentProvider.AUTHORITY, AppDataDocumentProvider.ROOT_ID
                )
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            println("No activity found to handle $action intent")
            false
        }
    } 
}

class SimpleDocument(val filename: String, val mimeType: String, val uri: Uri) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

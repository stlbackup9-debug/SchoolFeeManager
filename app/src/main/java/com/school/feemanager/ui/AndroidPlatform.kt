package com.school.feemanager.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.school.feemanager.data.FeeException
import com.school.feemanager.util.AppRestarter
import com.school.feemanager.util.Printer
import java.io.InputStream
import java.io.OutputStream

/** A document picked with the Storage Access Framework. */
class UriFileRef(private val context: Context, val uri: Uri) : FileRef {
    override val name: String
        get() = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    override fun openInput(): InputStream = context.contentResolver.openInputStream(uri) ?: throw FeeException("Cannot open the selected file")
    override fun openOutput(): OutputStream = context.contentResolver.openOutputStream(uri, "wt") ?: throw FeeException("Cannot write the file")
}

class AndroidPlatform(private val context: Context) : PlatformBridge {
    override fun printHtml(html: String, jobName: String, isReport: Boolean) = Printer.printHtml(context, html, jobName, isReport)
    override fun restartApp() = AppRestarter.restart(context)
}

@Composable
fun rememberFileOpener(onResult: (FileRef) -> Unit): (Array<String>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onResult(UriFileRef(context, uri)) }
    return { types -> launcher.launch(types) }
}

@Composable
fun rememberFileSaver(mimeType: String, onResult: (FileRef) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri -> if (uri != null) onResult(UriFileRef(context, uri)) }
    return { name -> launcher.launch(name) }
}

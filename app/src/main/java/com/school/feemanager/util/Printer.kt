package com.school.feemanager.util

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Prints HTML through Android's print framework. The system dialog lets the user pick a
 * Wi-Fi/Bluetooth printer or "Save as PDF". Must be called with an Activity context.
 */
object Printer {
    // Keep a reference until printing has started, otherwise the WebView can be garbage-collected.
    private var pending: WebView? = null

    fun printHtml(context: Context, html: String, jobName: String, isReport: Boolean) {
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(if (isReport) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A5.asLandscape())
                    .build()
                pm.print(jobName, view.createPrintDocumentAdapter(jobName), attrs)
                pending = null
            }
        }
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        pending = web
    }
}

/** Restarts the app (used after restoring a backup so the new database is opened cleanly). */
object AppRestarter {
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

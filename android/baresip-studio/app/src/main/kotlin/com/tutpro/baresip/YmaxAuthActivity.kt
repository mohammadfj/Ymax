package com.tutpro.baresip

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ymax login gate (launcher): shows the project web login in a WebView, then
 * hands off to the main app after a successful login, a ymax:// deep-link
 * callback, or the manual skip button. Lab-only: accepts self-signed certs
 * for the Ymax LAN hosts.
 */
class YmaxAuthActivity : Activity() {

    private val labHosts = setOf("192.168.250.42", "100.112.160.17")
    private val loginUrl = "https://192.168.250.42/login/"
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.data?.scheme == "ymax") { enterApp(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
        }
        val title = TextView(this).apply {
            text = "Ymax — ورود اپراتور"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(32, 32, 32, 16)
        }
        val skip = Button(this).apply {
            text = "ورود مستقیم به برنامه (بدون وب)"
            setOnClickListener { enterApp() }
        }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (url.startsWith("ymax://")) { enterApp(); return true }
                    return false
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    val host = Uri.parse(view.url ?: "").host ?: ""
                    if (host in labHosts) handler.proceed() else handler.cancel()
                }
                override fun onPageFinished(view: WebView, url: String) {
                    // After the web login the browser leaves /login — hand off then.
                    if (!handedOff && url.startsWith("https://") && !url.contains("/login")) enterApp()
                }
            }
        }
        root.addView(title)
        root.addView(skip)
        root.addView(web, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl(loginUrl)
    }

    private fun enterApp() {
        if (handedOff) return
        handedOff = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

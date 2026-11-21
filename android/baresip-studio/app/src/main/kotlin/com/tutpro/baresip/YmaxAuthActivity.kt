package com.tutpro.baresip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Ymax login gate (launcher).
 * Primary: native operator login (operator1..operator6) that auto-provisions the
 * matching SIP extension (1000..1005 on the lab server) by writing the baresip
 * accounts file BEFORE the service starts.
 * Secondary: "login via web panel" WebView flow (kept for the web-redirect UX).
 * NOTE: lab credentials are embedded for the TEST phase only — production will
 * authenticate against the callcenter-web REST/JWT API.
 */
class YmaxAuthActivity : Activity() {

    private val labHosts = setOf("192.168.250.42", "100.112.160.17")
    private val loginUrl = "https://192.168.250.42/login/"
    private val sipServer = "192.168.250.42"
    private val sipPort = "15060"

    // operatorN -> (extension, sipPassword)
    private val operators = mapOf(
        "operator1" to Pair("1000", "ymax1000"),
        "operator2" to Pair("1001", "ymax1001"),
        "operator3" to Pair("1002", "ymax1002"),
        "operator4" to Pair("1003", "ymax1003"),
        "operator5" to Pair("1004", "ymax1004"),
        "operator6" to Pair("1005", "ymax1005")
    )

    private var handedOff = false
    private lateinit var loginForm: LinearLayout
    private lateinit var webForm: WebView
    private lateinit var userField: EditText
    private lateinit var passField: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.data?.scheme == "ymax") { enterApp(); return }

        val prefs = getSharedPreferences("ymax", Context.MODE_PRIVATE)
        if (prefs.getBoolean("logged_in", false)) { enterApp(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Ymax"
            setTextColor(Color.WHITE); textSize = 26f
            setPadding(32, 60, 32, 8); gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ورود اپراتور"
            setTextColor(Color.parseColor("#8fbf9f")); textSize = 15f
            setPadding(32, 0, 32, 24); gravity = Gravity.CENTER
        }
        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#ff8888")); textSize = 13f
            setPadding(32, 8, 32, 8); gravity = Gravity.CENTER
        }
        userField = EditText(this).apply {
            hint = "نام کاربری (مثلاً operator1)"
            setSingleLine(true)
        }
        passField = EditText(this).apply {
            hint = "رمز عبور"
            setSingleLine(true)
        }
        val loginBtn = Button(this).apply {
            text = "ورود و شروع تماس"
            setOnClickListener { doNativeLogin() }
        }
        val webBtn = Button(this).apply {
            text = "ورود با پنل وب"
            setBackgroundColor(Color.parseColor("#22303a"))
            setOnClickListener { showWeb() }
        }

        loginForm = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 0)
            addView(userField)
            addView(passField)
            addView(loginBtn)
            addView(webBtn)
            addView(statusText)
        }
        webForm = WebView(this).apply {
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
                    if (!handedOff && url.startsWith("https://") && !url.contains("/login")) enterApp()
                }
            }
            visibility = android.view.View.GONE
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(loginForm, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(webForm, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun showWeb() {
        loginForm.visibility = android.view.View.GONE
        webForm.visibility = android.view.View.VISIBLE
        webForm.loadUrl(loginUrl)
    }

    private fun doNativeLogin() {
        val user = userField.text.toString().trim()
        val pass = passField.text.toString()
        val op = operators[user]
        if (op == null || op.second != pass) {
            statusText.text = "نام کاربری یا رمز اشتباه است (operator1..operator6)"
            return
        }
        val ext = op.first
        writeAccount(ext, op.second)
        getSharedPreferences("ymax", Context.MODE_PRIVATE)
            .edit().putBoolean("logged_in", true).putString("operator", user).apply()
        Toast.makeText(this, "خوش آمدی $user — داخلی $ext", Toast.LENGTH_SHORT).show()
        enterApp()
    }

    private fun writeAccount(ext: String, pass: String) {
        val line = "<sip:$ext@$sipServer:$sipPort>;auth_user=\"$ext\";auth_pass=\"$pass\";check_origin=no;regint=60\n"
        openFileOutput("accounts", Context.MODE_PRIVATE).use { it.write(line.toByteArray(Charsets.UTF_8)) }
    }

    private fun enterApp() {
        if (handedOff) return
        handedOff = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

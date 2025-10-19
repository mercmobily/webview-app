package com.mathacademy.secure

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    private val ALLOWED_DOMAIN = "mathacademy.com"
    private val START_URL = "https://www.mathacademy.com/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots (security measure)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MathAcademyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecureWebView(
                        url = START_URL,
                        allowedDomain = ALLOWED_DOMAIN,
                        onBlockedUrl = {
                            Toast.makeText(
                                this,
                                "Access restricted to Math Academy only",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MathAcademyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        content = content
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SecureWebView(
    url: String,
    allowedDomain: String,
    onBlockedUrl: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Handle back button to navigate within WebView
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Configure WebView settings with security hardening
                settings.apply {
                    javaScriptEnabled = true  // Required for modern websites
                    domStorageEnabled = true  // Required for login sessions
                    allowFileAccess = false   // Block file access
                    allowContentAccess = false // Block content providers

                    // Security hardening toggles
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)

                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // Enable Safe Browsing on API 26+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    settings.safeBrowsingEnabled = true
                }

                // Block downloads - prevent escaping to other apps
                setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    onBlockedUrl()
                })

                // Set custom WebViewClient to control navigation
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requestUrl = request?.url.toString()

                        // Only filter top-level (main frame) navigations
                        // Allow iframes/subframes to load (e.g., embedded content)
                        if (request?.isForMainFrame != true) {
                            return false  // Allow subframe navigations
                        }

                        // Check if URL belongs to allowed domain
                        return if (isAllowedUrl(requestUrl, allowedDomain)) {
                            false  // Allow navigation
                        } else {
                            onBlockedUrl()
                            true  // Block navigation
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Disable long-press to prevent URL copying
                        view?.isLongClickable = false
                        view?.setOnLongClickListener { true }
                    }
                }

                // Load the initial page
                loadUrl(url)

                // Store reference for back button handling
                webView = this
            }
        }
    )
}

private fun isAllowedUrl(url: String, allowedDomain: String): Boolean {
    return try {
        val host = java.net.URL(url).host.lowercase()
        host == allowedDomain || host.endsWith(".$allowedDomain")
    } catch (e: Exception) {
        false
    }
}

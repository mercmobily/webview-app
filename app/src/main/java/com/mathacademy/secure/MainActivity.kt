package com.mathacademy.secure

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// Site configuration data class
data class Site(
    val name: String,
    val domain: String,
    val startUrl: String
)

class MainActivity : ComponentActivity() {

    // Available sites list - can be expanded in the future
    private val availableSites = listOf(
        Site(
            name = "Math Academy",
            domain = "mathacademy.com",
            startUrl = "https://www.mathacademy.com/"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots (security measure)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            var selectedSite by remember { mutableStateOf<Site?>(null) }

            MathAcademyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (selectedSite == null) {
                        SiteSelector(
                            sites = availableSites,
                            onSiteSelected = { site ->
                                selectedSite = site
                            }
                        )
                    } else {
                        SecureWebView(
                            url = selectedSite!!.startUrl,
                            allowedDomain = selectedSite!!.domain,
                            onBlockedUrl = {
                                Toast.makeText(
                                    this,
                                    "Access restricted to ${selectedSite!!.name} only",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SiteSelector(
    sites: List<Site>,
    onSiteSelected: (Site) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select a Site",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        sites.forEach { site ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onSiteSelected(site) },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = site.name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = site.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
    var isLoading by remember { mutableStateOf(true) }

    // Handle back button to navigate within WebView
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                setDownloadListener { _, _, _, _, _ ->
                    onBlockedUrl()
                }

                // Set custom WebViewClient to control navigation
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val requestUrl = request?.url.toString()
                        val scheme = request?.url?.scheme?.lowercase()

                        // Block external intent schemes (mailto, tel, sms, etc.)
                        if (scheme != null && scheme !in listOf("http", "https")) {
                            Log.w("SecureWebView", "Blocked external intent: $scheme")
                            Toast.makeText(
                                context,
                                "External links are not allowed",
                                Toast.LENGTH_SHORT
                            ).show()
                            return true  // Block navigation
                        }

                        // Only filter top-level (main frame) navigation
                        // Allow iframes/sub-frame to load (e.g., embedded content)
                        if (request?.isForMainFrame != true) {
                            return false  // Allow sub-frame navigation
                        }

                        // Check if URL belongs to allowed domain
                        return if (isAllowedUrl(requestUrl, allowedDomain)) {
                            false  // Allow navigation
                        } else {
                            onBlockedUrl()
                            true  // Block navigation
                        }
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        isLoading = true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoading = false
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
        })

        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun isAllowedUrl(url: String, allowedDomain: String): Boolean {
    return try {
        val host = java.net.URL(url).host.lowercase()
        host == allowedDomain || host.endsWith(".$allowedDomain")
    } catch (_: Exception) {
        false
    }
}

package com.mathacademy.secure

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main Activity with SwipeRefreshLayout
 * Includes pull-to-refresh gesture and FAB button
 */
class MainActivityWithSwipe : ComponentActivity() {

    // Available sites - managed in Sites.kt
    private val availableSites = Sites.all

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
    var isRefreshing by remember { mutableStateOf(false) }
    val isConnected = rememberConnectivityState()
    val scope = rememberCoroutineScope()

    // Handle back button to navigate within WebView
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // Create custom SwipeRefreshLayout that respects horizontal scrolling
                object : SwipeRefreshLayout(context) {
                    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                    private var downX = 0f
                    private var downY = 0f
                    private var isHorizontalScroll = false

                    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                        when (ev.action) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = ev.x
                                downY = ev.y
                                isHorizontalScroll = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = abs(ev.x - downX)
                                val deltaY = abs(ev.y - downY)

                                // If horizontal movement is greater than vertical, it's a horizontal scroll
                                if (deltaX > touchSlop && deltaX > deltaY) {
                                    isHorizontalScroll = true
                                }

                                // Don't intercept if it's a horizontal scroll
                                if (isHorizontalScroll) {
                                    return false
                                }
                            }
                        }
                        return super.onInterceptTouchEvent(ev)
                    }
                }.apply {
                    // Configure refresh colors to match Material Theme
                    setColorSchemeResources(
                        android.R.color.holo_blue_bright,
                        android.R.color.holo_green_light,
                        android.R.color.holo_orange_light,
                        android.R.color.holo_red_light
                    )

                    // Store reference to the SwipeRefreshLayout for scroll listener
                    val swipeRefreshLayout = this

                    // Create the WebView
                    val webViewInstance = WebView(context).apply {
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

                        // Enable pull-to-refresh only when scrolled to top
                        setOnScrollChangeListener { _, _, scrollY, _, _ ->
                            swipeRefreshLayout.isEnabled = scrollY == 0
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
                                isRefreshing = false  // Stop refresh animation
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

                    // Set up pull-to-refresh handler
                    setOnRefreshListener {
                        isRefreshing = true
                        webViewInstance.reload()
                    }

                    // Add WebView to SwipeRefreshLayout
                    addView(webViewInstance)
                }
            },
            update = { swipeRefreshLayout ->
                // Update refresh state
                swipeRefreshLayout.isRefreshing = isRefreshing
            }
        )

        // Loading indicator
        if (isLoading && !isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Offline indicator - compact banner at very top
        if (!isConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "No internet connection",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Reload button - always visible in bottom right corner
        FloatingActionButton(
            onClick = {
                scope.launch {
                    isRefreshing = true
                    webView?.reload()
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "↻",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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

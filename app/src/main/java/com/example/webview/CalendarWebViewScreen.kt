package com.example.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingExportData(
  val bytes: ByteArray,
  val fileName: String,
)

@Composable
fun CalendarWebViewScreen(
  viewModel: CalendarWebViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  var pendingExport by remember { mutableStateOf<PendingExportData?>(null) }
  var customFullscreenView by remember { mutableStateOf<View?>(null) }
  var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

  // Network connectivity observer
  val networkMonitor = remember(context) { NetworkMonitor(context) }
  val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()

  DisposableEffect(networkMonitor) {
    networkMonitor.start()
    onDispose { networkMonitor.stop() }
  }

  LaunchedEffect(isOnline) {
    val wasOfflineError = uiState.errorState != null
    viewModel.onConnectivityChanged(isOnline)
    if (isOnline && wasOfflineError) {
      webViewRef?.let { wv ->
        if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
          wv.loadUrl(WebViewConfig.HOME_URL)
        } else {
          wv.reload()
        }
      }
    }
  }

  // Zero-permission file upload chooser (Photo Picker / SAF)
  val multiFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      pendingFileCallback?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
      pendingFileCallback = null
    }

  val singleFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      pendingFileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
      pendingFileCallback = null
    }

  // Zero-permission SAF export for blob:/data: downloads (e.g., .ics or JSON backups)
  val saveExportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
      val exportData = pendingExport
      pendingExport = null
      if (destUri != null && exportData != null) {
        coroutineScope.launch {
          val saved =
            withContext(Dispatchers.IO) {
              runCatching {
                  context.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(exportData.bytes)
                  }
                }
                .isSuccess
            }
          snackbarHostState.showSnackbar(
            context.getString(if (saved) R.string.file_saved else R.string.file_save_failed)
          )
        }
      }
    }

  // Handle HTML5 fullscreen bar toggling
  DisposableEffect(customFullscreenView) {
    val activity = context as? Activity
    val window = activity?.window
    if (window != null) {
      val controller = WindowCompat.getInsetsController(window, view)
      if (customFullscreenView != null) {
        controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }
    onDispose {
      val win = (context as? Activity)?.window ?: return@onDispose
      WindowCompat.getInsetsController(win, view).show(WindowInsetsCompat.Type.systemBars())
    }
  }

  // Pause / resume WebView and persist cookies with lifecycle
  DisposableEffect(lifecycleOwner, webViewRef) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
        Lifecycle.Event.ON_PAUSE -> {
          webViewRef?.onPause()
          CookieManager.getInstance().flush()
        }
        Lifecycle.Event.ON_STOP -> CookieManager.getInstance().flush()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Handle Android Back button inside WebView or fullscreen view
  BackHandler(enabled = uiState.canGoBack || customFullscreenView != null) {
    if (customFullscreenView != null) {
      customViewCallback?.onCustomViewHidden()
      customFullscreenView = null
      customViewCallback = null
    } else {
      webViewRef?.let { wv ->
        if (wv.canGoBack()) {
          wv.goBack()
        }
      }
    }
  }

  fun openExternalUri(uri: Uri) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.no_app_for_link))
      }
    }
  }

  fun triggerDataExport(dataUri: String, suggestedName: String) {
    val parsed = parseDataUri(dataUri)
    if (parsed != null) {
      pendingExport = PendingExportData(parsed, suggestedName)
      saveExportLauncher.launch(suggestedName)
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.file_save_failed))
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(innerPadding)
    ) {
      if (customFullscreenView == null) {
        AndroidView(
          modifier = Modifier.fillMaxSize().testTag("calendar_webview"),
          factory = { ctx ->
            createConfiguredWebView(
              context = ctx,
              onPageStarted = { url, canGoBack -> viewModel.onPageStarted(url, canGoBack) },
              onProgressChanged = { progress -> viewModel.onProgressChanged(progress) },
              onPageFinished = { url, canGoBack -> viewModel.onPageFinished(url, canGoBack) },
              onMainFrameError = { desc -> viewModel.onMainFrameError(desc) },
              onCanGoBackChanged = { canGoBack -> viewModel.onCanGoBackChanged(canGoBack) },
              onOpenExternal = { uri -> openExternalUri(uri) },
              onFileChooser = { callback, acceptTypes, allowMultiple ->
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                val mimeTypes =
                  acceptTypes
                    ?.filter { it.isNotBlank() }
                    ?.ifEmpty { listOf("*/*") }
                    ?.toTypedArray() ?: arrayOf("*/*")
                if (allowMultiple) {
                  multiFileLauncher.launch(mimeTypes)
                } else {
                  singleFileLauncher.launch(mimeTypes)
                }
              },
              onDownloadRequested = { url, userAgent, contentDisposition, mimeType ->
                when {
                  url.startsWith("data:") -> {
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    triggerDataExport(url, fileName)
                  }
                  url.startsWith("blob:") -> {
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val safeFileName = fileName.replace("'", "")
                    val safeUrl = url.replace("'", "")
                    webViewRef?.evaluateJavascript(
                      """
                      (function() {
                        fetch('$safeUrl')
                          .then(function(r) { return r.blob(); })
                          .then(function(blob) {
                            var reader = new FileReader();
                            reader.onloadend = function() {
                              if (window.AndroidBlobBridge && reader.result) {
                                window.AndroidBlobBridge.onBlobData(reader.result, '$safeFileName');
                              }
                            };
                            reader.readAsDataURL(blob);
                          });
                      })();
                      """.trimIndent(),
                      null,
                    )
                  }
                  else -> {
                    startHttpDownload(
                      context = ctx,
                      url = url,
                      userAgent = userAgent,
                      contentDisposition = contentDisposition,
                      mimeType = mimeType,
                      onStarted = { fileName ->
                        coroutineScope.launch {
                          snackbarHostState.showSnackbar(
                            ctx.getString(R.string.download_started, fileName)
                          )
                        }
                      },
                      onFallbackOpen = { uri ->
                        coroutineScope.launch {
                          snackbarHostState.showSnackbar(ctx.getString(R.string.download_failed))
                        }
                        openExternalUri(uri)
                      },
                    )
                  }
                }
              },
              onBlobBridgeData = { dataUrl, fileName ->
                coroutineScope.launch { triggerDataExport(dataUrl, fileName) }
              },
              onShowCustomView = { viewToShow, callback ->
                customFullscreenView = viewToShow
                customViewCallback = callback
              },
              onHideCustomView = {
                customFullscreenView = null
                customViewCallback = null
              },
            ).also { wv ->
              webViewRef = wv
              if (networkMonitor.checkCurrentConnectivity()) {
                wv.loadUrl(WebViewConfig.HOME_URL)
              } else {
                viewModel.onConnectivityChanged(false)
              }
            }
          },
          onRelease = { wv ->
            wv.stopLoading()
            wv.removeJavascriptInterface("AndroidBlobBridge")
            wv.destroy()
            if (webViewRef === wv) {
              webViewRef = null
            }
          },
        )
      }

      // Subtle top linear progress bar for in-app navigation
      AnimatedVisibility(
        visible = uiState.isLoading && uiState.hasInitialLoadFinished && uiState.errorState == null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250)),
        modifier = Modifier.align(Alignment.TopCenter),
      ) {
        LinearProgressIndicator(
          progress = { uiState.progress / 100f },
          modifier = Modifier.fillMaxWidth().height(3.dp).testTag("page_progress_indicator"),
          color = MaterialTheme.colorScheme.primary,
          trackColor = Color.Transparent,
        )
      }

      // Branded full-screen splash overlay during initial website load
      AnimatedVisibility(
        visible = !uiState.hasInitialLoadFinished && uiState.errorState == null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.fillMaxSize(),
      ) {
        SplashLoadingOverlay()
      }

      // Full-screen offline / load error overlay
      AnimatedVisibility(
        visible = uiState.errorState != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize(),
      ) {
        val isOffline = uiState.errorState is WebErrorState.Offline || !uiState.isOnline
        WebErrorOverlay(
          isOffline = isOffline,
          onRetry = {
            viewModel.onRetryRequested()
            val wv = webViewRef
            if (wv != null) {
              if (wv.url.isNullOrBlank() || wv.url == "about:blank") {
                wv.loadUrl(WebViewConfig.HOME_URL)
              } else {
                wv.reload()
              }
            }
          },
          onOpenInBrowser = { openExternalUri(Uri.parse(uiState.currentUrl)) },
        )
      }

      // HTML5 fullscreen custom view container
      customFullscreenView?.let { fsView ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { fsView },
          )
        }
      }
    }
  }
}

@Composable
private fun SplashLoadingOverlay() {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("splash_loading_overlay"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_calendar_launcher_1794834601410),
        contentDescription = stringResource(R.string.cd_app_logo),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.loading_calendar),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(28.dp))
      CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        strokeWidth = 3.dp,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun WebErrorOverlay(
  isOffline: Boolean,
  onRetry: () -> Unit,
  onOpenInBrowser: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize().testTag("error_overlay"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(88.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isOffline) Icons.Rounded.WifiOff else Icons.Rounded.CloudOff,
              contentDescription = stringResource(R.string.cd_offline_icon),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(44.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
          text = stringResource(if (isOffline) R.string.offline_title else R.string.error_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = stringResource(if (isOffline) R.string.offline_message else R.string.error_message),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
          onClick = onRetry,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .height(52.dp)
              .testTag("retry_button"),
          shape = RoundedCornerShape(16.dp),
          contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.cd_retry_icon),
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(R.string.retry_button),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
          onClick = onOpenInBrowser,
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .height(52.dp)
              .testTag("open_browser_button"),
          shape = RoundedCornerShape(16.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = stringResource(R.string.open_in_browser),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
  context: Context,
  onPageStarted: (String?, Boolean) -> Unit,
  onProgressChanged: (Int) -> Unit,
  onPageFinished: (String?, Boolean) -> Unit,
  onMainFrameError: (String?) -> Unit,
  onCanGoBackChanged: (Boolean) -> Unit,
  onOpenExternal: (Uri) -> Unit,
  onFileChooser: (ValueCallback<Array<Uri>>, Array<String>?, Boolean) -> Unit,
  onDownloadRequested: (String, String, String, String) -> Unit,
  onBlobBridgeData: (String, String) -> Unit,
  onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
  onHideCustomView: () -> Unit,
): WebView {
  val webView = WebView(context)
  webView.layoutParams =
    ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT,
    )
  webView.setBackgroundColor(AndroidColor.TRANSPARENT)
  webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
  webView.isVerticalScrollBarEnabled = false
  webView.isHorizontalScrollBarEnabled = false

  with(webView.settings) {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    useWideViewPort = true
    loadWithOverviewMode = true
    cacheMode = WebSettings.LOAD_DEFAULT
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    mediaPlaybackRequiresUserGesture = false
    allowFileAccess = false
    allowContentAccess = true
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    setSupportMultipleWindows(true)
    javaScriptCanOpenWindowsAutomatically = true
  }

  val cookieManager = CookieManager.getInstance()
  cookieManager.setAcceptCookie(true)
  cookieManager.setAcceptThirdPartyCookies(webView, true)

  webView.addJavascriptInterface(
    object {
      @JavascriptInterface
      fun onBlobData(dataUrl: String, fileName: String) {
        onBlobBridgeData(dataUrl, fileName)
      }
    },
    "AndroidBlobBridge",
  )

  webView.webViewClient =
    object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        return when (WebViewConfig.classifyUrl(url)) {
          UrlAction.LoadInWebView -> false
          UrlAction.OpenExternal -> {
            onOpenExternal(Uri.parse(url))
            true
          }
          UrlAction.BlockUnsafe -> true
        }
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url, view?.canGoBack() == true)
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()
        onPageFinished(url, view?.canGoBack() == true)
      }

      override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onCanGoBackChanged(view?.canGoBack() == true)
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
      ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
          onMainFrameError(error?.description?.toString())
        }
      }
    }

  webView.webChromeClient =
    object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
      }

      override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
      ): Boolean {
        if (filePathCallback == null) return false
        val allowMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
        onFileChooser(filePathCallback, fileChooserParams?.acceptTypes, allowMultiple)
        return true
      }

      override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
      ): Boolean {
        if (!isUserGesture || resultMsg == null || view == null) return false
        val tempWebView = WebView(view.context)
        tempWebView.webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              tempView: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val url = request?.url?.toString()
              if (url != null) {
                when (WebViewConfig.classifyUrl(url)) {
                  UrlAction.LoadInWebView -> view.loadUrl(url)
                  UrlAction.OpenExternal -> onOpenExternal(Uri.parse(url))
                  UrlAction.BlockUnsafe -> Unit
                }
              }
              tempView?.destroy()
              return true
            }
          }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = tempWebView
        resultMsg.sendToTarget()
        return true
      }

      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null && callback != null) {
          onShowCustomView(view, callback)
        }
      }

      override fun onHideCustomView() {
        onHideCustomView()
      }
    }

  webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
    onDownloadRequested(url, userAgent, contentDisposition, mimeType)
  }

  return webView
}

private fun startHttpDownload(
  context: Context,
  url: String,
  userAgent: String,
  contentDisposition: String,
  mimeType: String,
  onStarted: (String) -> Unit,
  onFallbackOpen: (Uri) -> Unit,
) {
  val uri = Uri.parse(url)
  val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
  val result = runCatching {
    val request =
      DownloadManager.Request(uri).apply {
        setMimeType(mimeType)
        addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
          addRequestHeader("Cookie", cookie)
        }
        setTitle(fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
          setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
      }
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.enqueue(request)
  }
  if (result.isSuccess) {
    onStarted(fileName)
  } else {
    onFallbackOpen(uri)
  }
}

private fun parseDataUri(dataUri: String): ByteArray? {
  val commaIndex = dataUri.indexOf(',')
  if (commaIndex == -1) return null
  val header = dataUri.substring(0, commaIndex)
  val payload = dataUri.substring(commaIndex + 1)
  return runCatching {
    if (header.contains(";base64", ignoreCase = true)) {
      Base64.decode(payload, Base64.DEFAULT)
    } else {
      URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
    }
  }.getOrNull()
}

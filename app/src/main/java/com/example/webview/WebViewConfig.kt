package com.example.webview

import android.net.Uri

sealed interface UrlAction {
  data object LoadInWebView : UrlAction
  data object OpenExternal : UrlAction
  data object BlockUnsafe : UrlAction
}

object WebViewConfig {
  const val HOME_URL = "https://calendar-5ce.pages.dev"
  const val ALLOWED_HOST = "calendar-5ce.pages.dev"

  private val SAFE_EXTERNAL_SCHEMES = setOf("http", "https", "tel", "mailto", "geo", "sms", "intent")

  fun isInternalHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val normalized = host.lowercase()
    return normalized == ALLOWED_HOST || normalized.endsWith(".$ALLOWED_HOST")
  }

  fun classifyUrl(rawUrl: String?): UrlAction {
    if (rawUrl.isNullOrBlank()) return UrlAction.BlockUnsafe
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return UrlAction.BlockUnsafe
    val scheme = uri.scheme?.lowercase() ?: return UrlAction.BlockUnsafe

    return when (scheme) {
      "https" -> {
        if (isInternalHost(uri.host)) UrlAction.LoadInWebView else UrlAction.OpenExternal
      }
      "http" -> UrlAction.OpenExternal
      "about", "blob", "data" -> UrlAction.LoadInWebView
      in SAFE_EXTERNAL_SCHEMES -> UrlAction.OpenExternal
      else -> UrlAction.OpenExternal
    }
  }
}

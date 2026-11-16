package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.webview.CalendarWebViewModel
import com.example.webview.UrlAction
import com.example.webview.WebErrorState
import com.example.webview.WebViewConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Calendar`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Calendar", appName)
  }

  @Test
  fun `internal host urls stay inside WebView`() {
    assertEquals(UrlAction.LoadInWebView, WebViewConfig.classifyUrl("https://calendar-5ce.pages.dev"))
    assertEquals(
      UrlAction.LoadInWebView,
      WebViewConfig.classifyUrl("https://calendar-5ce.pages.dev/events/today"),
    )
    assertEquals(
      UrlAction.LoadInWebView,
      WebViewConfig.classifyUrl("https://sub.calendar-5ce.pages.dev/"),
    )
  }

  @Test
  fun `external and system scheme urls open externally`() {
    assertEquals(UrlAction.OpenExternal, WebViewConfig.classifyUrl("https://example.com"))
    assertEquals(UrlAction.OpenExternal, WebViewConfig.classifyUrl("http://calendar-5ce.pages.dev"))
    assertEquals(UrlAction.OpenExternal, WebViewConfig.classifyUrl("mailto:test@example.com"))
    assertEquals(UrlAction.OpenExternal, WebViewConfig.classifyUrl("tel:+1234567890"))
    assertEquals(UrlAction.BlockUnsafe, WebViewConfig.classifyUrl(""))
  }

  @Test
  fun `viewModel transitions between loading, loaded, and offline states`() {
    val vm = CalendarWebViewModel()
    assertTrue(vm.uiState.value.isLoading)
    assertFalse(vm.uiState.value.hasInitialLoadFinished)

    vm.onConnectivityChanged(false)
    assertEquals(WebErrorState.Offline, vm.uiState.value.errorState)

    vm.onConnectivityChanged(true)
    vm.onRetryRequested()
    assertNull(vm.uiState.value.errorState)

    vm.onPageFinished(WebViewConfig.HOME_URL, canGoBack = false)
    assertFalse(vm.uiState.value.isLoading)
    assertTrue(vm.uiState.value.hasInitialLoadFinished)
  }
}

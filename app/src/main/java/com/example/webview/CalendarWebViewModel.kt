package com.example.webview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface WebErrorState {
  data object Offline : WebErrorState
  data class LoadFailed(val description: String?) : WebErrorState
}

data class WebViewUiState(
  val currentUrl: String = WebViewConfig.HOME_URL,
  val progress: Int = 0,
  val isLoading: Boolean = true,
  val hasInitialLoadFinished: Boolean = false,
  val canGoBack: Boolean = false,
  val isOnline: Boolean = true,
  val errorState: WebErrorState? = null,
)

class CalendarWebViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(WebViewUiState())
  val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

  fun onConnectivityChanged(online: Boolean) {
    _uiState.update { state ->
      val updatedError =
        if (!online && !state.hasInitialLoadFinished) {
          WebErrorState.Offline
        } else {
          state.errorState
        }
      state.copy(isOnline = online, errorState = updatedError)
    }
  }

  fun onPageStarted(url: String?, canGoBack: Boolean) {
    _uiState.update {
      it.copy(
        currentUrl = url ?: it.currentUrl,
        isLoading = true,
        canGoBack = canGoBack,
        errorState = null,
      )
    }
  }

  fun onProgressChanged(progress: Int) {
    _uiState.update { state ->
      val initialDone = state.hasInitialLoadFinished || (progress >= 90 && state.errorState == null)
      state.copy(
        progress = progress.coerceIn(0, 100),
        isLoading = progress < 100,
        hasInitialLoadFinished = initialDone,
      )
    }
  }

  fun onPageFinished(url: String?, canGoBack: Boolean) {
    _uiState.update { state ->
      state.copy(
        currentUrl = url ?: state.currentUrl,
        progress = 100,
        isLoading = false,
        hasInitialLoadFinished = state.errorState == null || state.hasInitialLoadFinished,
        canGoBack = canGoBack,
      )
    }
  }

  fun onMainFrameError(description: String?) {
    _uiState.update { state ->
      val error =
        if (!state.isOnline) WebErrorState.Offline else WebErrorState.LoadFailed(description)
      state.copy(
        isLoading = false,
        errorState = error,
      )
    }
  }

  fun onCanGoBackChanged(canGoBack: Boolean) {
    _uiState.update { it.copy(canGoBack = canGoBack) }
  }

  fun onRetryRequested() {
    _uiState.update { state ->
      state.copy(
        isLoading = true,
        progress = 0,
        errorState = if (state.isOnline) null else WebErrorState.Offline,
      )
    }
  }
}

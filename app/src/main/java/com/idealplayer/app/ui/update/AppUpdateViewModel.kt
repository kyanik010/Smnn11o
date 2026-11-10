package com.idealplayer.app.ui.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.update.AppUpdateCheckResult
import com.idealplayer.app.core.update.AppUpdateInfo
import com.idealplayer.app.data.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class AppUpdateUiState(
    val update: AppUpdateInfo? = null,
    val isVisible: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val errorMessage: String? = null,
    val needsInstallPermission: Boolean = false
)

sealed interface AppUpdateEvent {
    data class OpenIntent(val intent: Intent) : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val _events = Channel<AppUpdateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var hasChecked = false
    private var downloadedApk: File? = null

    fun checkForUpdate() {
        if (hasChecked) return
        hasChecked = true

        viewModelScope.launch {
            _state.update { it.copy(isChecking = true, errorMessage = null) }
            try {
                when (val result = appUpdateRepository.checkForUpdate()) {
                    is AppUpdateCheckResult.Available -> {
                        _state.update {
                            it.copy(
                                update = result.update,
                                isVisible = true,
                                isChecking = false
                            )
                        }
                    }
                    AppUpdateCheckResult.NotAvailable,
                    AppUpdateCheckResult.Disabled -> {
                        _state.update { it.copy(isChecking = false) }
                    }
                }
            } catch (e: Exception) {
                Timber.w("App update check failed: %s", e.javaClass.simpleName)
                _state.update { it.copy(isChecking = false) }
            }
        }
    }

    fun dismiss() {
        val update = _state.value.update
        if (update?.isMandatory == true || _state.value.isDownloading) return
        _state.update { it.copy(isVisible = false) }
    }

    fun downloadAndInstall() {
        val update = _state.value.update ?: return
        if (_state.value.isDownloading) return

        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isDownloading = true,
                        downloadProgress = 0,
                        errorMessage = null,
                        needsInstallPermission = false
                    )
                }

                val apkFile = downloadedApk ?: appUpdateRepository.downloadApk(update) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }.also { downloadedApk = it }

                _state.update { it.copy(isDownloading = false, downloadProgress = 100) }
                requestInstall(apkFile)
            } catch (e: Exception) {
                // Download/verification errors can retain the update URL. Show a generic
                // recoverable message instead of exposing a source or signing detail.
                Timber.e("App update download failed: %s", e.javaClass.simpleName)
                _state.update {
                    it.copy(
                        isDownloading = false,
                        errorMessage = "Update failed"
                    )
                }
            }
        }
    }

    fun retryInstallAfterPermission() {
        val apkFile = downloadedApk ?: return downloadAndInstall()
        requestInstall(apkFile)
    }

    private fun requestInstall(apkFile: File) {
        viewModelScope.launch {
            if (!appUpdateRepository.canRequestPackageInstalls()) {
                _state.update { it.copy(needsInstallPermission = true) }
                _events.send(AppUpdateEvent.OpenIntent(appUpdateRepository.createUnknownAppSourcesIntent()))
                return@launch
            }

            _state.update { it.copy(needsInstallPermission = false) }
            _events.send(AppUpdateEvent.OpenIntent(appUpdateRepository.createInstallIntent(apkFile)))
        }
    }
}

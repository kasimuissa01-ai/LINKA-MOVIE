package com.example.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.remote.AppUpdateInfo
import com.example.data.remote.AppUpdateService
import com.example.data.remote.UpdateDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppUpdateViewModel(
    private val appUpdateService: AppUpdateService = AppUpdateService()
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _showBottomAlert = MutableStateFlow(true)
    val showBottomAlert: StateFlow<Boolean> = _showBottomAlert.asStateFlow()

    val downloadState: StateFlow<UpdateDownloadState> = appUpdateService.downloadState

    val currentAppVersion: String
        get() = try {
            BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            "1.0.0"
        }

    init {
        // Automatically check for updates on startup
        checkForUpdates(silent = true)
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val info = appUpdateService.checkForUpdates(currentAppVersion)
                _updateInfo.value = info
                if (info.isUpdateAvailable) {
                    _showBottomAlert.value = true
                }
            } catch (e: Exception) {
                // Ignore network errors on background check
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun dismissBottomAlert() {
        _showBottomAlert.value = false
    }

    fun showBottomAlertAgain() {
        _showBottomAlert.value = true
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun openUpdateDialog() {
        _showUpdateDialog.value = true
    }

    fun startDownloadAndInstall(context: Context) {
        val info = _updateInfo.value ?: return
        if (info.downloadUrl.isBlank()) return

        viewModelScope.launch {
            appUpdateService.downloadAndInstallApk(
                context = context,
                downloadUrl = info.downloadUrl,
                versionTag = info.latestVersion
            )
        }
    }

    fun retryInstall(context: Context) {
        val state = downloadState.value
        if (state is UpdateDownloadState.ReadyToInstall) {
            appUpdateService.installApk(context, state.apkFile)
        }
    }
}

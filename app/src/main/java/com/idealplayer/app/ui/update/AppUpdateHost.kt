package com.idealplayer.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idealplayer.app.R
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2DialogShell
import com.idealplayer.app.ui.components.a2.A2ProgressIndicator
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppUpdateHost(
    isTv: Boolean,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppUpdateEvent.OpenIntent -> context.startActivity(event.intent)
            }
        }
    }

    val update = state.update ?: return
    if (!state.isVisible) return

    AppUpdateDialog(
        state = state,
        isTv = isTv,
        currentVersionLabel = stringResource(R.string.update_current_version, com.idealplayer.app.BuildConfig.VERSION_NAME),
        targetVersionLabel = stringResource(R.string.update_target_version, update.versionName),
        onDismiss = viewModel::dismiss,
        onUpdate = viewModel::downloadAndInstall,
        onRetryInstall = viewModel::retryInstallAfterPermission
    )
}

@Composable
private fun AppUpdateDialog(
    state: AppUpdateUiState,
    isTv: Boolean,
    currentVersionLabel: String,
    targetVersionLabel: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onRetryInstall: () -> Unit
) {
    val update = state.update ?: return
    val updateButtonFocusRequester = remember { FocusRequester() }
    val dismissButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(update.versionCode, update.isMandatory, state.isDownloading) {
        // Dialog content is attached asynchronously. Deferring one frame prevents an
        // uninitialized FocusRequester when an update becomes visible during navigation.
        // Optional updates deliberately start on the safe dismissal action; mandatory
        // updates have no dismissal action and therefore start on Update.
        androidx.compose.runtime.withFrameNanos { }
        if (!state.isDownloading) {
            val requester = if (update.isMandatory) {
                updateButtonFocusRequester
            } else {
                dismissButtonFocusRequester
            }
            runCatching { requester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !update.isMandatory && !state.isDownloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(if (isTv) A2Spacing.xxl else A2Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            val title = if (update.isMandatory) {
                stringResource(R.string.update_required_title)
            } else {
                stringResource(R.string.update_available_title)
            }
            val progressLabel = stringResource(R.string.update_downloading, state.downloadProgress)
            A2DialogShell(
                title = title,
                message = "$currentVersionLabel  •  $targetVersionLabel",
                icon = Icons.Filled.SystemUpdate,
                stateDescription = title,
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = A2Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(A2Spacing.md)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (isTv) 180.dp else 120.dp)
                                .verticalScroll(rememberScrollState()),
                            text = update.releaseNotes.ifBlank {
                                stringResource(R.string.update_default_message)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextSecondary
                        )

                        if (state.isDownloading) {
                            A2ProgressIndicator(
                                contentDescription = progressLabel,
                                stateDescription = progressLabel,
                                label = progressLabel,
                                valueLabel = "%${state.downloadProgress}",
                                progress = state.downloadProgress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        state.errorMessage?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IdealPlayerColors.Error
                            )
                        }

                        if (state.needsInstallPermission) {
                            Text(
                                text = stringResource(R.string.update_install_permission_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = IdealPlayerColors.Warning
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!update.isMandatory) {
                            A2ActionButton(
                                text = stringResource(R.string.update_later),
                                onClick = onDismiss,
                                enabled = !state.isDownloading,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(dismissButtonFocusRequester),
                                variant = A2ActionVariant.Secondary
                            )
                        }
                        A2ActionButton(
                            text = if (state.needsInstallPermission) {
                                stringResource(R.string.update_retry_install)
                            } else {
                                stringResource(R.string.update_now)
                            },
                            onClick = if (state.needsInstallPermission) onRetryInstall else onUpdate,
                            enabled = !state.isDownloading,
                            loading = state.isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(updateButtonFocusRequester)
                        )
                    }
                }
            )
        }
    }
}

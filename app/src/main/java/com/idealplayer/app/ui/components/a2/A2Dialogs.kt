package com.idealplayer.app.ui.components.a2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

/**
 * Reusable visual shell for A2 dialogs. Dismissal policy, business state and all displayed copy
 * remain owned by the call site.
 */
@Composable
fun A2DialogShell(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    contentDescription: String? = null,
    stateDescription: String = title,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (isTv) 720.dp else 560.dp)
            .semantics(mergeDescendants = false) {
                paneTitle = title
                this.stateDescription = stateDescription
                contentDescription?.let { this.contentDescription = it }
            },
        shape = A2Shape.extraLarge,
        color = IdealPlayerColors.Surface,
        contentColor = IdealPlayerColors.TextPrimary,
        tonalElevation = 0.dp,
        shadowElevation = if (isTv) 24.dp else 12.dp
    ) {
        Column(
            modifier = Modifier.padding(if (isTv) A2Spacing.xl else A2Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = IdealPlayerColors.Primary,
                    modifier = Modifier.size(maxOf(dimens.iconSize, 32.dp))
                )
                Spacer(Modifier.height(A2Spacing.md))
            }
            Text(
                text = title,
                color = IdealPlayerColors.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Spacer(Modifier.height(A2Spacing.sm))
                Text(
                    text = message,
                    color = IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            content()
            Spacer(Modifier.height(A2Spacing.lg))
            actions()
        }
    }
}

/** Confirmation dialog with optional destructive treatment and explicit dismissal policy. */
@Composable
fun A2ConfirmationDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    confirmContentDescription: String? = null,
    dismissContentDescription: String? = null,
    stateDescription: String = title,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    requestInitialConfirmFocus: Boolean = true
) {
    if (!visible) return

    val confirmFocusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        if (requestInitialConfirmFocus) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { confirmFocusRequester.requestFocus() }
            }
        }
        A2DialogShell(
            title = title,
            message = message,
            icon = icon,
            iconContentDescription = iconContentDescription,
            stateDescription = stateDescription,
            modifier = modifier.padding(A2Spacing.md),
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    A2ActionButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Ghost,
                        contentDescription = dismissContentDescription
                    )
                    A2ActionButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(confirmFocusRequester),
                        variant = if (destructive) {
                            A2ActionVariant.Destructive
                        } else {
                            A2ActionVariant.Primary
                        },
                        contentDescription = confirmContentDescription
                    )
                }
            }
        )
    }
}

/** Permission prompt specialization; permission requests themselves remain at the call site. */
@Composable
fun A2PermissionDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String = title,
    confirmContentDescription: String? = null,
    dismissContentDescription: String? = null
) {
    A2ConfirmationDialog(
        visible = visible,
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        icon = Icons.Filled.Security,
        iconContentDescription = null,
        confirmContentDescription = confirmContentDescription,
        dismissContentDescription = dismissContentDescription,
        stateDescription = stateDescription
    )
}

/** PIN dialog shell; verification, retry limits and parental-control policy remain caller-owned. */
@Composable
fun A2PinDialog(
    visible: Boolean,
    title: String,
    message: String?,
    pin: String,
    onPinChange: (String) -> Unit,
    pinContentDescription: String,
    pinStateDescription: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    digitCount: Int = 4,
    errorMessage: String? = null,
    confirmEnabled: Boolean = true,
    stateDescription: String = title,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
) {
    if (!visible) return

    val pinFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { pinFocusRequester.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        A2DialogShell(
            title = title,
            message = message,
            icon = Icons.Filled.Security,
            stateDescription = stateDescription,
            modifier = modifier.padding(A2Spacing.md),
            content = {
                Spacer(Modifier.height(A2Spacing.lg))
                A2PinInput(
                    pin = pin,
                    onPinChange = onPinChange,
                    contentDescription = pinContentDescription,
                    stateDescription = pinStateDescription,
                    digitCount = digitCount,
                    errorMessage = errorMessage,
                    focusRequester = pinFocusRequester
                )
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Ghost
                    )
                    A2ActionButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = confirmEnabled
                    )
                }
            }
        )
    }
}

/** Determinate/indeterminate update or synchronization dialog with explicit mandatory policy. */
@Composable
fun A2ProgressDialog(
    visible: Boolean,
    title: String,
    message: String,
    progressContentDescription: String,
    progressStateDescription: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    progressValueLabel: String? = null,
    dismissLabel: String? = null,
    icon: ImageVector = Icons.Filled.SystemUpdate,
    iconContentDescription: String? = null,
    stateDescription: String = title,
    dismissOnBackPress: Boolean = dismissLabel != null,
    dismissOnClickOutside: Boolean = dismissLabel != null
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false
        )
    ) {
        A2DialogShell(
            title = title,
            message = message,
            icon = icon,
            iconContentDescription = iconContentDescription,
            stateDescription = stateDescription,
            modifier = modifier.padding(A2Spacing.md),
            content = {
                Spacer(Modifier.height(A2Spacing.lg))
                A2ProgressIndicator(
                    contentDescription = progressContentDescription,
                    progress = progress,
                    valueLabel = progressValueLabel,
                    stateDescription = progressStateDescription,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            actions = {
                if (dismissLabel != null) {
                    A2ActionButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        variant = A2ActionVariant.Secondary
                    )
                }
            }
        )
    }
}

@Preview(name = "Mobile destructive", group = "A2 dialogs", widthDp = 390, heightDp = 520)
@Composable
private fun A2DialogMobilePreview() {
    IdealPlayerTheme {
        A2DialogShell(
            title = "Oynatma listesini silmek istiyor musunuz?",
            message = "Bu işlem listeyi, eşitlenen kanalları ve program rehberi verilerini bu cihazdan kalıcı olarak kaldırır.",
            icon = Icons.Filled.DeleteForever,
            stateDescription = "Silme onayı",
            modifier = Modifier
                .background(IdealPlayerColors.OverlayDark)
                .padding(A2Spacing.md),
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = "Vazgeç",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Ghost
                    )
                    A2ActionButton(
                        text = "Listeyi kalıcı olarak sil",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Destructive
                    )
                }
            }
        )
    }
}

@Preview(name = "PIN shell", group = "A2 dialogs", widthDp = 390, heightDp = 560)
@Composable
private fun A2PinDialogPreview() {
    IdealPlayerTheme {
        A2DialogShell(
            title = "Kilitli içeriği aç",
            message = "Devam etmek için ebeveyn denetimi PIN kodunu girin.",
            icon = Icons.Filled.Security,
            stateDescription = "PIN doğrulama",
            modifier = Modifier.padding(A2Spacing.md),
            content = {
                Spacer(Modifier.height(A2Spacing.lg))
                A2PinInput(
                    pin = "19",
                    onPinChange = {},
                    contentDescription = "Ebeveyn denetimi PIN kodu",
                    stateDescription = "4 haneden 2 hane girildi"
                )
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = "Vazgeç",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Ghost
                    )
                    A2ActionButton(
                        text = "Kilidi aç",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }
}

@Preview(name = "TV information", group = "A2 dialogs", widthDp = 960, heightDp = 540)
@Composable
private fun A2DialogTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2DialogShell(
            title = "Uygulama güncellemesi hazır",
            message = "Yeni sürüm oynatıcı kararlılığı ve televizyon kumandası odak iyileştirmeleri içeriyor.",
            icon = Icons.Filled.Info,
            stateDescription = "Güncelleme bilgisi",
            modifier = Modifier.padding(A2Spacing.xxl),
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.md)
                ) {
                    A2ActionButton(
                        text = "Daha sonra",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Secondary
                    )
                    A2ActionButton(
                        text = "Şimdi güncelle",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }
}

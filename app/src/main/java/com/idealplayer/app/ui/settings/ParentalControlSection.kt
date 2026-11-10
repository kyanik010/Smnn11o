package com.idealplayer.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.idealplayer.app.R
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.security.ParentalControlManager
import com.idealplayer.app.ui.components.rememberTvFocusVisualState
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2BooleanSettingRow
import com.idealplayer.app.ui.components.a2.A2DialogShell
import com.idealplayer.app.ui.components.a2.A2PinDialog
import com.idealplayer.app.ui.components.a2.A2PinInput
import com.idealplayer.app.ui.components.a2.A2SelectorSettingRow

// ─────────────────────────────────────────────────────────────────────────────
// Parental Control Settings Section
// Plug into SettingsContent as a SettingsSection block.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParentalControlSection(
    parentalControlManager: ParentalControlManager,
    isTv: Boolean
) {
    // Observed state — re-read on each recomposition triggered by refresh key
    var refreshKey by remember { mutableIntStateOf(0) }
    val isPinSet by remember(refreshKey) { mutableStateOf(parentalControlManager.isPinSet) }
    val isChildLock by remember(refreshKey) { mutableStateOf(parentalControlManager.isChildLockEnabled) }
    val whitelistCategories by remember(refreshKey) { mutableStateOf(parentalControlManager.getWhitelistedCategories()) }

    // Dialog states
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var verifyPinPurpose by remember { mutableStateOf(VerifyPurpose.TOGGLE_LOCK) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showClearPinDialog by remember { mutableStateOf(false) }
    val childLockFocusRequester = remember { FocusRequester() }
    val pinActionFocusRequester = remember { FocusRequester() }
    val deletePinFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    var restoreFocusTarget by remember { mutableStateOf<ParentalFocusTarget?>(null) }
    val categoryLabels = localizedCategoryLabels()

    LaunchedEffect(
        isTv,
        showSetPinDialog,
        showVerifyPinDialog,
        showCategorySheet,
        showClearPinDialog,
        restoreFocusTarget
    ) {
        val target = restoreFocusTarget
        if (
            isTv &&
            target != null &&
            !showSetPinDialog &&
            !showVerifyPinDialog &&
            !showCategorySheet &&
            !showClearPinDialog
        ) {
            // The dismissed dialog/sheet remains in composition for one frame. Waiting avoids
            // requesting focus from a node that has not yet re-entered the TV focus tree.
            withFrameNanos { }
            val requester = when (target) {
                ParentalFocusTarget.CHILD_LOCK -> childLockFocusRequester
                ParentalFocusTarget.PIN_ACTION -> pinActionFocusRequester
                ParentalFocusTarget.DELETE_PIN -> deletePinFocusRequester
                ParentalFocusTarget.CATEGORIES -> categoryFocusRequester
            }
            runCatching { requester.requestFocus() }
            restoreFocusTarget = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)) {

        // The whole row owns focus and toggling; the nested switch remains a passive visual.
        A2BooleanSettingRow(
            title = stringResource(R.string.a2_child_lock),
            checked = isChildLock,
            onCheckedChange = {
                restoreFocusTarget = ParentalFocusTarget.CHILD_LOCK
                if (!isPinSet) {
                    showSetPinDialog = true
                } else {
                    verifyPinPurpose = VerifyPurpose.TOGGLE_LOCK
                    showVerifyPinDialog = true
                }
            },
            stateDescription = stringResource(
                if (isChildLock) R.string.a2_state_on else R.string.a2_state_off
            ),
            description = when {
                !isPinSet -> stringResource(R.string.a2_parental_pin_not_set)
                isChildLock -> stringResource(R.string.a2_parental_lock_active)
                else -> stringResource(R.string.a2_state_inactive)
            },
            icon = if (isChildLock) Icons.Filled.Lock else Icons.Filled.LockOpen,
            modifier = Modifier.then(
                if (isTv) Modifier.focusRequester(childLockFocusRequester) else Modifier
            )
        )

        // ── PIN controls ─────────────────────────────────────────────────────
        if (isPinSet) {
            ParentalActionRow(
                icon = Icons.Filled.Pin,
                label = stringResource(R.string.a2_change_pin),
                isTv = isTv,
                focusRequester = pinActionFocusRequester,
                onClick = {
                    restoreFocusTarget = ParentalFocusTarget.PIN_ACTION
                    showSetPinDialog = true
                }
            )
            ParentalActionRow(
                icon = Icons.Filled.DeleteForever,
                label = stringResource(R.string.a2_delete_pin),
                isDanger = true,
                isTv = isTv,
                focusRequester = deletePinFocusRequester,
                onClick = {
                    restoreFocusTarget = ParentalFocusTarget.DELETE_PIN
                    showClearPinDialog = true
                }
            )
        } else {
            ParentalActionRow(
                icon = Icons.Filled.Pin,
                label = stringResource(R.string.a2_set_pin),
                isTv = isTv,
                focusRequester = pinActionFocusRequester,
                onClick = {
                    restoreFocusTarget = ParentalFocusTarget.PIN_ACTION
                    showSetPinDialog = true
                }
            )
        }

        // ── Whitelist categories ──────────────────────────────────────────────
        ParentalActionRow(
            icon = Icons.Filled.Category,
            label = stringResource(R.string.a2_allowed_categories),
            subtitle = if (whitelistCategories.isEmpty()) {
                stringResource(R.string.a2_no_categories_selected)
            } else {
                whitelistCategories.joinToString(", ") { category ->
                    categoryLabels[category.lowercase()] ?: category
                }
            },
            isTv = isTv,
            focusRequester = categoryFocusRequester,
            onClick = {
                restoreFocusTarget = ParentalFocusTarget.CATEGORIES
                showCategorySheet = true
            }
        )
    }

    // ── Dialogs / Sheets ─────────────────────────────────────────────────────

    if (showSetPinDialog) {
        SetPinDialog(
            isTv = isTv,
            onConfirm = { newPin ->
                parentalControlManager.setPin(newPin)
                refreshKey++
                showSetPinDialog = false
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showVerifyPinDialog) {
        VerifyPinDialog(
            isTv = isTv,
            title = when (verifyPinPurpose) {
                VerifyPurpose.TOGGLE_LOCK -> stringResource(
                    if (isChildLock) {
                        R.string.a2_verify_pin_disable_lock
                    } else {
                        R.string.a2_verify_pin_enable_lock
                    }
                )
                VerifyPurpose.CLEAR_PIN -> stringResource(R.string.a2_verify_pin_delete)
            },
            onVerify = { pin ->
                val ok = parentalControlManager.verifyPin(pin)
                if (ok) {
                    when (verifyPinPurpose) {
                        VerifyPurpose.TOGGLE_LOCK -> {
                            parentalControlManager.setChildLock(!isChildLock)
                            refreshKey++
                        }
                        VerifyPurpose.CLEAR_PIN -> {
                            parentalControlManager.clearPin()
                            // Deleting the PIN removes the focused Delete row from composition.
                            // Restore the replacement "Set PIN" action instead of requesting a
                            // stale requester after the dialog closes.
                            restoreFocusTarget = ParentalFocusTarget.PIN_ACTION
                            refreshKey++
                        }
                    }
                }
                ok
            },
            onDismiss = { showVerifyPinDialog = false }
        )
    }

    if (showClearPinDialog) {
        val cancelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(isTv) {
            if (isTv) {
                withFrameNanos { }
                runCatching { cancelFocusRequester.requestFocus() }
            }
        }
        Dialog(
            onDismissRequest = { showClearPinDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            A2DialogShell(
                title = stringResource(R.string.a2_delete_pin),
                message = stringResource(R.string.a2_delete_pin_message),
                icon = Icons.Filled.Warning,
                stateDescription = stringResource(R.string.a2_delete_pin_state),
                modifier = Modifier.padding(A2Spacing.md),
                actions = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                    ) {
                        A2ActionButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showClearPinDialog = false },
                            variant = A2ActionVariant.Ghost,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(cancelFocusRequester)
                        )
                        A2ActionButton(
                            text = stringResource(R.string.a2_action_yes_delete),
                            onClick = {
                                showClearPinDialog = false
                                verifyPinPurpose = VerifyPurpose.CLEAR_PIN
                                showVerifyPinDialog = true
                            },
                            variant = A2ActionVariant.Destructive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            )
        }
    }

    if (showCategorySheet) {
        CategoryWhitelistSheet(
            isTv = isTv,
            whitelisted = whitelistCategories,
            onToggle = { cat, allowed ->
                if (allowed) parentalControlManager.addToWhitelist(cat)
                else parentalControlManager.removeFromWhitelist(cat)
                refreshKey++
            },
            onDismiss = { showCategorySheet = false }
        )
    }
}

// ─── PIN purpose enum ────────────────────────────────────────────────────────
private enum class VerifyPurpose { TOGGLE_LOCK, CLEAR_PIN }

private enum class ParentalFocusTarget { CHILD_LOCK, PIN_ACTION, DELETE_PIN, CATEGORIES }

// ─── Set PIN Dialog ──────────────────────────────────────────────────────────
@Composable
private fun SetPinDialog(
    isTv: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val pinLengthError = stringResource(R.string.a2_pin_four_digits_error)
    val pinMismatchError = stringResource(R.string.a2_pin_mismatch_error)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        A2DialogShell(
            title = stringResource(R.string.a2_set_pin),
            message = stringResource(R.string.a2_set_pin_message),
            icon = Icons.Filled.Lock,
            stateDescription = stringResource(R.string.a2_set_pin_state),
            modifier = Modifier.padding(A2Spacing.md),
            content = {
                Spacer(Modifier.height(A2Spacing.lg))
                A2PinInput(
                    pin = pin,
                    onPinChange = {
                        pin = it
                        error = null
                    },
                    contentDescription = stringResource(R.string.a2_new_parental_pin_description),
                    stateDescription = pluralStringResource(
                        R.plurals.a2_pin_progress,
                        pin.length,
                        pin.length
                    ),
                    errorMessage = error?.takeIf { pin.length != 4 },
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(A2Spacing.md))
                A2PinInput(
                    pin = confirmPin,
                    onPinChange = {
                        confirmPin = it
                        error = null
                    },
                    contentDescription = stringResource(R.string.a2_confirm_parental_pin_description),
                    stateDescription = pluralStringResource(
                        R.plurals.a2_pin_confirm_progress,
                        confirmPin.length,
                        confirmPin.length
                    ),
                    errorMessage = error?.takeIf { pin.length == 4 },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        variant = A2ActionVariant.Ghost,
                        modifier = Modifier.weight(1f)
                    )
                    A2ActionButton(
                        text = stringResource(R.string.save),
                        onClick = {
                            when {
                                pin.length != 4 -> error = pinLengthError
                                pin != confirmPin -> error = pinMismatchError
                                else -> onConfirm(pin)
                            }
                        },
                        enabled = pin.length == 4 && confirmPin.length == 4,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }
    LaunchedEffect(isTv) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }
}

// ─── Verify PIN Dialog ───────────────────────────────────────────────────────
@Composable
private fun VerifyPinDialog(
    isTv: Boolean,
    title: String,
    onVerify: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    A2PinDialog(
        visible = true,
        title = title,
        message = stringResource(R.string.a2_verify_pin_message),
        pin = pin,
        onPinChange = {
            pin = it
            error = false
        },
        pinContentDescription = stringResource(R.string.a2_parental_pin_description),
        pinStateDescription = if (error) {
            stringResource(R.string.a2_incorrect_pin_state)
        } else {
            pluralStringResource(R.plurals.a2_pin_progress, pin.length, pin.length)
        },
        errorMessage = if (error) stringResource(R.string.a2_incorrect_pin) else null,
        confirmLabel = stringResource(R.string.a2_action_verify),
        dismissLabel = stringResource(R.string.cancel),
        confirmEnabled = pin.length == 4,
        stateDescription = title,
        onConfirm = {
            val ok = onVerify(pin)
            if (ok) onDismiss() else error = true
        },
        onDismiss = onDismiss
    )
}

// ─── Category Whitelist Bottom Sheet ─────────────────────────────────────────
private val DEFAULT_CATEGORIES = listOf(
    "Haber", "Spor", "Belgesel", "Çocuk", "Eğitim", "Müzik", "Film", "Dizi", "Genel"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun CategoryWhitelistSheet(
    isTv: Boolean,
    whitelisted: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val categoryLabels = localizedCategoryLabels()
    val categoryRequesters = remember {
        DEFAULT_CATEGORIES.associateWith { FocusRequester() }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            withFrameNanos { }
            categoryRequesters[DEFAULT_CATEGORIES.firstOrNull()]?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    val categoryContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Category, contentDescription = null, tint = IdealPlayerColors.Primary)
                Text(
                    stringResource(R.string.a2_allowed_categories),
                    style = MaterialTheme.typography.titleMedium,
                    color = IdealPlayerColors.TextPrimary
                )
            }
            Text(
                stringResource(R.string.a2_allowed_categories_description),
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            DEFAULT_CATEGORIES.forEachIndexed { index, cat ->
                val isAllowed = whitelisted.contains(cat.lowercase())
                val interactionSource = remember(cat) { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val focusState = rememberTvFocusVisualState(
                    isFocused = isTv && isFocused,
                    isSelected = isAllowed,
                    defaultSurface = IdealPlayerColors.CardBackground,
                    selectedSurface = IdealPlayerColors.SurfaceSelected,
                    focusedSurface = IdealPlayerColors.SurfaceFocus
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimens.touchTargetMin)
                        .graphicsLayer {
                            scaleX = focusState.scale
                            scaleY = focusState.scale
                            shadowElevation = focusState.shadowElevation.toPx()
                            spotShadowColor = focusState.glowColor
                            ambientShadowColor = focusState.glowColor
                        }
                        .clip(A2Shape.medium)
                        .background(focusState.backgroundColor)
                        .border(
                            width = focusState.borderWidth,
                            color = focusState.borderColor,
                            shape = A2Shape.medium
                        )
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRequester(categoryRequesters.getValue(cat))
                                    .focusProperties {
                                        up = DEFAULT_CATEGORIES.getOrNull(index - 1)
                                            ?.let(categoryRequesters::getValue)
                                            ?: FocusRequester.Cancel
                                        down = DEFAULT_CATEGORIES.getOrNull(index + 1)
                                            ?.let(categoryRequesters::getValue)
                                            ?: FocusRequester.Cancel
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current
                        ) { onToggle(cat, !isAllowed) }
                        .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        categoryLabels[cat.lowercase()] ?: cat,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IdealPlayerColors.TextPrimary
                    )
                    Checkbox(
                        checked = isAllowed,
                        // The row is the one focusable D-pad target. Keeping the checkbox
                        // passive prevents a duplicate, visually ambiguous focus stop.
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = IdealPlayerColors.Secondary,
                            checkmarkColor = IdealPlayerColors.TextOnPrimary,
                            uncheckedColor = IdealPlayerColors.TextSecondary
                        )
                    )
                }
            }
        }
    }

    if (isTv) {
        // A TV dialog isolates focus from the side drawer. A bottom sheet is retained for
        // touch devices, where its gesture behaviour is expected.
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 760.dp),
                shape = A2Shape.extraLarge,
                color = IdealPlayerColors.Surface,
                tonalElevation = 0.dp,
                shadowElevation = 24.dp
            ) {
                categoryContent()
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = IdealPlayerColors.Surface,
            tonalElevation = 0.dp
        ) {
            categoryContent()
        }
    }
}

@Composable
private fun localizedCategoryLabels(): Map<String, String> = mapOf(
    "haber" to stringResource(R.string.a2_category_news),
    "spor" to stringResource(R.string.a2_category_sports),
    "belgesel" to stringResource(R.string.a2_category_documentary),
    "çocuk" to stringResource(R.string.a2_category_children),
    "eğitim" to stringResource(R.string.a2_category_education),
    "müzik" to stringResource(R.string.a2_category_music),
    "film" to stringResource(R.string.a2_category_movie),
    "dizi" to stringResource(R.string.a2_category_series),
    "genel" to stringResource(R.string.a2_category_general)
)

// ─── Small action row helper ──────────────────────────────────────────────────
@Composable
private fun ParentalActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String? = null,
    isDanger: Boolean = false,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    if (isDanger) {
        A2ActionButton(
            text = label,
            onClick = onClick,
            icon = icon,
            variant = A2ActionVariant.Destructive,
            modifier = Modifier
                .fillMaxWidth()
                .then(requesterModifier)
        )
    } else {
        A2SelectorSettingRow(
            title = label,
            selectedValue = subtitle.orEmpty(),
            stateDescription = subtitle ?: label,
            icon = icon,
            onClick = onClick,
            modifier = Modifier.then(requesterModifier)
        )
    }
}

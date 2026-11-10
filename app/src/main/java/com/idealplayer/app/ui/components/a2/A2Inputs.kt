package com.idealplayer.app.ui.components.a2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme

/**
 * Standard A2 outlined field. Labels, supporting/error copy and accessibility descriptions are
 * passed in so this reusable layer never owns production strings.
 */
@Composable
fun A2TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconContentDescription: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    stateDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            contentDescription?.let { this.contentDescription = it }
            stateDescription?.let { this.stateDescription = it }
        },
        enabled = enabled,
        readOnly = readOnly,
        label = { Text(text = label) },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    color = IdealPlayerColors.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = leadingIconContentDescription,
                    tint = IdealPlayerColors.TextSecondary
                )
            }
        },
        trailingIcon = trailingContent,
        supportingText = (errorMessage ?: supportingText)?.let {
            {
                Text(
                    text = it,
                    color = if (errorMessage != null) IdealPlayerColors.Error else IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        isError = errorMessage != null,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = A2Shape.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = IdealPlayerColors.TextPrimary,
            unfocusedTextColor = IdealPlayerColors.TextPrimary,
            disabledTextColor = IdealPlayerColors.TextTertiary,
            errorTextColor = IdealPlayerColors.TextPrimary,
            focusedContainerColor = IdealPlayerColors.SurfaceVariant,
            unfocusedContainerColor = IdealPlayerColors.SurfaceVariant,
            disabledContainerColor = IdealPlayerColors.SurfaceVariant.copy(alpha = 0.56f),
            errorContainerColor = IdealPlayerColors.SurfaceVariant,
            cursorColor = IdealPlayerColors.Primary,
            errorCursorColor = IdealPlayerColors.Error,
            focusedBorderColor = IdealPlayerColors.FocusBorder,
            unfocusedBorderColor = IdealPlayerColors.CardBorder,
            disabledBorderColor = IdealPlayerColors.Disabled,
            errorBorderColor = IdealPlayerColors.Error,
            focusedLabelColor = IdealPlayerColors.Primary,
            unfocusedLabelColor = IdealPlayerColors.TextSecondary,
            disabledLabelColor = IdealPlayerColors.TextTertiary,
            errorLabelColor = IdealPlayerColors.Error
        )
    )
}

/** Search-specific wrapper that preserves the A2 field geometry and platform target sizes. */
@Composable
fun A2SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorMessage: String? = null,
    clearContentDescription: String,
    contentDescription: String? = null,
    stateDescription: String? = null,
    enabled: Boolean = true,
    onSearch: (() -> Unit)? = null
) {
    A2TextField(
        value = query,
        onValueChange = onQueryChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        supportingText = supportingText,
        errorMessage = errorMessage,
        leadingIcon = Icons.Filled.Search,
        leadingIconContentDescription = null,
        trailingContent = if (query.isNotEmpty()) {
            {
                A2IconButton(
                    icon = Icons.Filled.Clear,
                    contentDescription = clearContentDescription,
                    onClick = { onQueryChange("") },
                    enabled = enabled
                )
            }
        } else {
            null
        },
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() })
    )
}

@Preview(name = "Mobile fields", group = "A2 inputs", widthDp = 390)
@Composable
private fun A2InputMobilePreview() {
    IdealPlayerTheme {
        var query by remember { mutableStateOf("Bilim kurgu") }
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            A2SearchField(
                query = query,
                onQueryChange = { query = it },
                label = "Ara",
                placeholder = "Film, dizi veya canlı kanal ara",
                supportingText = "Tüm içerik kaynaklarında aranır",
                clearContentDescription = "Aramayı temizle",
                modifier = Modifier.fillMaxWidth()
            )
            A2TextField(
                value = "https://örnek.invalid/çok-uzun-kullanıcı-listesi",
                onValueChange = {},
                label = "Oynatma listesi bağlantısı",
                errorMessage = "Bağlantı doğrulanamadı; adresi denetleyip yeniden deneyin.",
                stateDescription = "Hatalı bağlantı",
                leadingIcon = Icons.Filled.Link,
                modifier = Modifier.fillMaxWidth()
            )
            A2TextField(
                value = "Salt okunur kaynak",
                onValueChange = {},
                label = "Devre dışı alan",
                stateDescription = "Düzenlenemez",
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Tablet long Turkish", group = "A2 inputs", widthDp = 720)
@Composable
private fun A2InputTabletPreview() {
    IdealPlayerTheme {
        A2TextField(
            value = "Evdeki televizyon için oluşturduğum kişisel ve oldukça uzun oynatma listesi",
            onValueChange = {},
            label = "Liste adı",
            supportingText = "Bu ad yalnızca cihazınızda gösterilir ve içerik sağlayıcınızla paylaşılmaz.",
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.lg)
                .fillMaxWidth()
        )
    }
}

@Preview(name = "TV target", group = "A2 inputs", widthDp = 960, heightDp = 220)
@Composable
private fun A2InputTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2SearchField(
            query = "",
            onQueryChange = {},
            label = "Televizyonda ara",
            placeholder = "Film, dizi, kanal veya program adı",
            clearContentDescription = "Aramayı temizle",
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl)
                .fillMaxWidth()
        )
    }
}

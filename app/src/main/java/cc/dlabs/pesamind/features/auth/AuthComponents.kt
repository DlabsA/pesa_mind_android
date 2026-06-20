package cc.dlabs.pesamind.features.auth

// Features/Auth/Components/AuthComponents.kt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared text field ─────────────────────────────────────────────────────────

/**
 * Reusable auth text field with leading icon, per-field error label,
 * optional password visibility toggle, and full keyboard action wiring.
 *
 * Keyboard notes:
 *  - Pass ImeAction.Next  → caller supplies onNext inside keyboardActions
 *  - Pass ImeAction.Go    → caller supplies onGo  inside keyboardActions
 *  - The field handles its own imeAction rendering; the screen controls focus
 *    routing via FocusRequester and the keyboardActions lambda.
 */
@Composable
fun AuthTextField(
    value:                String,
    onValueChange:        (String) -> Unit,
    label:                String,
    placeholder:          String,
    leadingIcon:          ImageVector,
    modifier:             Modifier              = Modifier,
    errorMessage:         String?               = null,
    trailingIcon:         ImageVector?          = null,
    onTrailingIconClick:  (() -> Unit)?         = null,
    visualTransformation: VisualTransformation  = VisualTransformation.None,
    keyboardOptions:      KeyboardOptions       = KeyboardOptions.Default,
    keyboardActions:      KeyboardActions       = KeyboardActions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier.padding(bottom = 6.dp),
        )

        OutlinedTextField(
            value                = value,
            onValueChange        = onValueChange,
            placeholder          = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface) },
            leadingIcon          = {
                Icon(
                    imageVector        = leadingIcon,
                    contentDescription = null,
                    modifier           = Modifier.size(20.dp),
                    tint               = if (errorMessage != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            },
            trailingIcon = trailingIcon?.let {
                {
                    IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                        Icon(
                            imageVector        = it,
                            contentDescription = if (visualTransformation == VisualTransformation.None)
                                "Hide password" else "Show password",
                            modifier           = Modifier.size(20.dp),
                            tint               = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            isError              = errorMessage != null,
            visualTransformation = visualTransformation,
            keyboardOptions      = keyboardOptions,
            keyboardActions      = keyboardActions,
            singleLine           = true,
            shape                = RoundedCornerShape(12.dp),
            modifier             = Modifier.fillMaxWidth(),
        )

        // Per-field inline error — mirrors Swift's `error: vm.emailError`
        if (errorMessage != null) {
            Text(
                text     = errorMessage,
                color    = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

// ── Error banner ──────────────────────────────────────────────────────────────

/**
 * Full-width error card — mirrors Swift's ErrorBanner.
 * Shown for server/network errors, hidden for per-field validation.
 */
@Composable
fun ErrorBanner(message: String) {
    Surface(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(10.dp),
        color     = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onErrorContainer,
                modifier           = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text      = message,
                color     = MaterialTheme.colorScheme.onErrorContainer,
                fontSize  = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
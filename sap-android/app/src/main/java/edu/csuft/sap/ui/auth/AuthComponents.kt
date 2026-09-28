package edu.csuft.sap.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 登录与注册沿用同一套输入框颜色，跟随用户设置的主题色。 */
@Composable
internal fun authFieldColors() = MaterialTheme.colorScheme.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.65f),
        disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.4f),
        errorContainerColor = colors.error.copy(alpha = 0.035f),
        focusedLeadingIconColor = colors.primary,
        unfocusedLeadingIconColor = colors.onSurfaceVariant,
    )
}

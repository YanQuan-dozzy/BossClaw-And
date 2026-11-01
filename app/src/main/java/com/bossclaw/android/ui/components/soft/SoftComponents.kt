package com.bossclaw.android.ui.components.soft

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bossclaw.android.ui.theme.BossClawElevation
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawShadow
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark
import com.bossclaw.android.ui.theme.softShadow

enum class SoftButtonVariant { Primary, Secondary, Ghost }

enum class SoftShadowTint { Neutral, Brand }

/**
 * 柔和卡片：白底 + 大圆角 + 低透明度彩色阴影。
 * 可点击时只做轻微下沉，不做 Android 上无意义的悬停位移。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shadowTint: SoftShadowTint = SoftShadowTint.Neutral,
    contentPadding: Dp = BossClawSpacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalBossClawDark.current
    val shape = RoundedCornerShape(BossClawRadius.card)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = tween(BossClawMotion.PRESS, easing = BossClawMotion.Standard),
        label = "soft-card-press"
    )
    val tint = when (shadowTint) {
        SoftShadowTint.Brand -> BossClawShadow.brand(dark)
        SoftShadowTint.Neutral -> BossClawShadow.neutral(dark)
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .softShadow(BossClawElevation.card, shape, tint)
            .background(MaterialTheme.colorScheme.surface, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SoftButtonVariant = SoftButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BossClawRadius.button)
    val container = when (variant) {
        SoftButtonVariant.Primary -> colors.primary
        SoftButtonVariant.Secondary -> colors.surfaceContainerHigh
        SoftButtonVariant.Ghost -> colors.primaryContainer
    }
    val contentColor = when (variant) {
        SoftButtonVariant.Primary -> colors.onPrimary
        SoftButtonVariant.Secondary -> colors.onSurface
        SoftButtonVariant.Ghost -> colors.onPrimaryContainer
    }
    val shadowColor = when (variant) {
        SoftButtonVariant.Primary -> BossClawShadow.brand(dark)
        SoftButtonVariant.Secondary -> BossClawShadow.neutral(dark)
        SoftButtonVariant.Ghost -> Color.Transparent
    }
    val elevation = when (variant) {
        SoftButtonVariant.Primary -> BossClawElevation.raised
        SoftButtonVariant.Secondary -> BossClawElevation.card
        SoftButtonVariant.Ghost -> BossClawElevation.flat
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickEnabled = enabled && !loading
    val scale by animateFloatAsState(
        targetValue = if (pressed && clickEnabled) 0.98f else 1f,
        animationSpec = tween(BossClawMotion.PRESS),
        label = "soft-button-press"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .softShadow(elevation, shape, shadowColor)
            .background(container, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = clickEnabled,
                onClick = onClick
            )
            .heightIn(min = BossClawSpacing.controlHeight)
            .padding(horizontal = BossClawSpacing.md, vertical = BossClawSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(modifier = Modifier.width(BossClawSpacing.xs))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(BossClawSpacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 柔和输入框：默认填充底、无描边，聚焦时转白底并叠加低透明度光晕。
 */
@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BossClawRadius.control)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (focused) 4.dp else 0.dp,
                color = if (focused) colors.primary.copy(alpha = 0.16f) else Color.Transparent,
                shape = shape
            ),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = shape,
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        label = label?.let { text -> { Text(text) } },
        placeholder = placeholder?.let { hint -> { Text(hint) } },
        supportingText = supportingText?.let { hint -> { Text(hint) } },
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surfaceVariant,
            disabledContainerColor = colors.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = colors.primary,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant
        )
    )
}

@Composable
fun SoftChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primaryContainer else colors.surfaceVariant
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant

    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .background(container, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = BossClawSpacing.md, vertical = BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

/** 圆形图标底：柔和色调 10% 填充，用于空态、权限行和列表头像位。 */
@Composable
fun SoftIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

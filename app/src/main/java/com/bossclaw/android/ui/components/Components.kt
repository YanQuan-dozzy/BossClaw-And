package com.bossclaw.android.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bossclaw.android.R
import com.bossclaw.android.core.TimeUtils
import com.bossclaw.android.model.FitLevel
import com.bossclaw.android.model.LogLine
import com.bossclaw.android.ui.components.soft.SoftIconBadge
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawElevation
import com.bossclaw.android.ui.theme.BossClawGradients
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawShadow
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.LocalBossClawDark
import com.bossclaw.android.ui.theme.softShadow

fun fitColor(level: FitLevel): Color = when (level) {
    FitLevel.STRONG -> BossClawColors.Teal
    FitLevel.MATCH -> BossClawColors.Amber
    FitLevel.CAUTIOUS -> BossClawColors.Muted
    FitLevel.UNFIT -> BossClawColors.Danger
}

fun levelColor(level: String): Color = when (level) {
    LogLine.LEVEL_SUCCESS -> BossClawColors.Success
    LogLine.LEVEL_WARN -> BossClawColors.Amber
    LogLine.LEVEL_ERROR -> BossClawColors.Danger
    else -> BossClawColors.Muted
}

/** Token 计数统一按 K / M 展示，避免出现 128000 这类长数字挤占版面。 */
fun tokenLabel(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1_000 -> "${value / 1_000}K"
    else -> value.toString()
}

fun openSystemSettings(context: Context, intent: Intent) {
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** 页面背景：品牌色雾 + 极细颗粒，让大面积底色不再是一块死平的色块。 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val dark = LocalBossClawDark.current
    val base = if (dark) BossClawColors.DarkBackground else BossClawColors.Paper
    val grainColor = if (dark) Color.White.copy(alpha = 0.045f) else Color(0xFF14211F).copy(alpha = 0.035f)
    val fogStrength = if (dark) 0.24f else 0.11f
    Canvas(modifier = modifier.fillMaxSize()) {
        // 先铺主题底色，避免应用主题与系统窗口背景不一致时露出黑底。
        drawRect(color = base)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BossClawColors.Teal.copy(alpha = fogStrength), Color.Transparent),
                center = Offset(size.width * 0.08f, -size.height * 0.04f),
                radius = size.width * 1.15f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    BossClawColors.Amber.copy(alpha = if (dark) 0.10f else 0.07f),
                    Color.Transparent
                ),
                center = Offset(size.width * 1.02f, size.height * 1.02f),
                radius = size.width * 1.1f
            )
        )
        // 确定性散点颗粒：不闪烁、开销可控，只负责提供一点纸面质感。
        var seed = 0x5EED1234L
        repeat(760) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val fx = ((seed ushr 16) and 0xFFFF).toFloat() / 65535f
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val fy = ((seed ushr 16) and 0xFFFF).toFloat() / 65535f
            drawCircle(
                color = grainColor,
                radius = 1.1f,
                center = Offset(fx * size.width, fy * size.height)
            )
        }
    }
}

@Composable
fun BossClawCard(
    modifier: Modifier = Modifier,
    padding: Dp = BossClawSpacing.card,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = LocalBossClawDark.current
    val shape = RoundedCornerShape(BossClawRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(BossClawMotion.snappy())
            .softShadow(
                elevation = if (emphasized) BossClawElevation.raised else BossClawElevation.card,
                shape = shape,
                color = if (emphasized) BossClawShadow.brand(dark) else BossClawShadow.neutral(dark)
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // 品牌圆点替代原来的竖条，保持识别度但去掉硬边。
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        CircleShape
                    )
            )
            Column(modifier = Modifier.padding(start = BossClawSpacing.sm)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
        trailing()
    }
}

@Composable
fun FitChip(level: FitLevel) {
    val color = fitColor(level)
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun StatusPill(text: String, color: Color, pulse: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "status-pill")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = BossClawMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status-pill-alpha"
    )
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = if (pulse) color.copy(alpha = alpha) else color
            ) {}
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** 带滚动数字统计卡，数值变化时用动效过渡，避免数字突然跳变。 */
@Composable
fun StatTile(
    label: String,
    value: String,
    suffix: String = "",
    accent: Color? = null,
    modifier: Modifier = Modifier,
    numericValue: Int? = null,
    hint: String? = null
) {
    val dark = LocalBossClawDark.current
    val colors = MaterialTheme.colorScheme
    val tint = accent ?: colors.primary
    val shape = RoundedCornerShape(BossClawRadius.control)
    Column(
        modifier = modifier
            .softShadow(BossClawElevation.card, shape, BossClawShadow.neutral(dark))
            .background(colors.surface, shape)
            .padding(BossClawSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (numericValue != null) {
                val animated by animateIntAsState(
                    targetValue = numericValue,
                    animationSpec = tween(BossClawMotion.SLOW, easing = BossClawMotion.EmphasizedDecel),
                    label = "stat-count"
                )
                Text(
                    text = animated.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = tint
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = tint
                )
            }
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = BossClawSpacing.xxs)
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = BossClawSpacing.xxs)
            )
        }
    }
}

/** 空状态：使用导出的矢量插画并做轻微浮动，替代单调的圆形图标。 */
@Composable
fun EmptyState(
    icon: ImageVector? = null,
    title: String,
    message: String,
    image: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = BossClawSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (image) {
            Image(
                painter = painterResource(R.drawable.img_empty_radar),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 168.dp, height = 118.dp)
            )
        } else if (icon != null) {
            SoftIconBadge(
                icon = icon,
                size = 72.dp,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = BossClawSpacing.md)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                top = BossClawSpacing.xs,
                start = BossClawSpacing.xl,
                end = BossClawSpacing.xl
            )
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            SoftIconBadge(icon = icon, size = 36.dp)
            Spacer(modifier = Modifier.width(BossClawSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 权限/环境状态小格：一个圆点 + 标题 + 状态文案。 */
@Composable
fun StatusCell(
    title: String,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val color = if (ready) BossClawColors.Success else BossClawColors.Danger
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(BossClawRadius.control))
            .clip(RoundedCornerShape(BossClawRadius.control))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = BossClawSpacing.xs, horizontal = BossClawSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = color) {}
        Column(modifier = Modifier.padding(start = BossClawSpacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (ready) "已就绪" else "待开启",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/** 简易日志行，供首页摘要与记录页共用。 */
@Composable
fun CompactLogRow(source: String, message: String, level: String, timestamp: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(levelColor(level))
        )
        Text(
            text = "$source · $message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 9.dp)
        )
        Text(
            text = TimeUtils.formatTime(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 1.dp)
        )
    }
}

/**
 * 下拉选择器：替换横向滑动、或选项铺不满一行的小按钮。
 * 选项较多或文案较长时不再挤出屏幕，也避免用户左右滑找选项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BossClawDropdown(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second ?: "未选择"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        TextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            supportingText = hint?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(BossClawRadius.control),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (value == selectedValue) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * 数值输入：替代只能选固定档位的下拉或写死的滑杆读数，允许用户直接键入个性化数值。
 * 输入过程中保留文本，失焦时再按区间归一，避免「刚输入第一位就被抬高到下限」。
 */
@Composable
fun BossClawNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
    placeholder: String = ""
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    var syncedValue by remember { mutableStateOf(value) }

    // 非输入态才跟随外部值（滑杆拖动、其他入口更新）刷新文本，编辑中的光标不会被重置。
    LaunchedEffect(value, focused) {
        if (!focused && value != syncedValue) {
            text = value.toString()
            syncedValue = value
        }
    }

    TextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(9)
            text = digits
            val parsed = digits.toIntOrNull()
            if (parsed != null && parsed in minValue..maxValue) {
                syncedValue = parsed
                onValueChange(parsed)
            }
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(BossClawRadius.control),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) {
                    syncedValue = value
                } else {
                    val clamped = (text.toIntOrNull() ?: value).coerceIn(minValue, maxValue)
                    text = clamped.toString()
                    if (clamped != value) {
                        syncedValue = clamped
                        onValueChange(clamped)
                    }
                }
            }
    )
}

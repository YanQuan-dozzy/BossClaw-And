package com.bossclaw.android.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 8dp 间距体系：页面、卡片、列表统一从这里取值，避免散落的硬编码数值。 */
object BossClawSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    /** 语义别名，页面层优先用这些名字。 */
    val screen = lg
    val card = lg
    val cardGap = sm
    val sectionGap = xl
    val row = md
    val controlHeight = 48.dp
}

/** 圆角上限 28dp：再大就会削弱工具类应用的可靠感。 */
object BossClawRadius {
    val chip = 10.dp
    val control = 16.dp
    val button = 20.dp
    val card = 24.dp
    val hero = 28.dp
    val pill = 999.dp
}

/** 阴影分级：列表用色差分层，只有重点卡片、浮层和 HUD 需要阴影。 */
object BossClawElevation {
    val flat = 0.dp
    val card = 2.dp
    val raised = 6.dp
    val floating = 12.dp
}

object BossClawShadow {
    fun neutral(dark: Boolean): Color =
        if (dark) Color(0x1F000000) else Color(0x141B2422)

    fun brand(dark: Boolean): Color =
        if (dark) Color(0x1F5FD1C8) else Color(0x240B7A75)
}

/**
 * 彩色柔和阴影。API 28 以下会回落为普通阴影，所以页面不能只靠阴影分层，
 * 必须同时保留 surface 与 surfaceContainer 的色差。
 */
fun Modifier.softShadow(elevation: Dp, shape: Shape, color: Color): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = color,
        spotColor = color
    )

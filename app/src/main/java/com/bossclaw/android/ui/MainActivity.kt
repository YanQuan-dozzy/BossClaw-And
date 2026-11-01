package com.bossclaw.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.bossclaw.android.R
import com.bossclaw.android.agent.EngineState
import com.bossclaw.android.ui.components.AppBackdrop
import com.bossclaw.android.ui.navigation.AppKey
import com.bossclaw.android.ui.navigation.Routes
import com.bossclaw.android.ui.navigation.navSections
import com.bossclaw.android.ui.navigation.tabRoutes
import com.bossclaw.android.ui.navigation.workTabs
import com.bossclaw.android.ui.screens.AutomationSettingsScreen
import com.bossclaw.android.ui.screens.ChatScreen
import com.bossclaw.android.ui.screens.InsightsScreen
import com.bossclaw.android.ui.screens.JobsScreen
import com.bossclaw.android.ui.screens.ModelSettingsScreen
import com.bossclaw.android.ui.screens.PermissionsScreen
import com.bossclaw.android.ui.screens.RecordsScreen
import com.bossclaw.android.ui.screens.ResumeScreen
import com.bossclaw.android.ui.screens.SettingsScreen
import com.bossclaw.android.ui.screens.SkillsSettingsScreen
import com.bossclaw.android.ui.screens.WorkbenchScreen
import com.bossclaw.android.ui.theme.BossClawColors
import com.bossclaw.android.ui.theme.BossClawElevation
import com.bossclaw.android.ui.theme.BossClawGradients
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.BossClawRadius
import com.bossclaw.android.ui.theme.BossClawShadow
import com.bossclaw.android.ui.theme.BossClawSpacing
import com.bossclaw.android.ui.theme.BossClawTheme
import com.bossclaw.android.ui.theme.LocalBossClawDark
import com.bossclaw.android.ui.theme.softShadow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BossClawViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BossClawViewModel::class.java]
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            BossClawTheme(darkTheme = darkTheme) {
                // 系统栏图标跟随应用主题，而不是系统主题，避免浅色应用配深色图标。
                SideEffect {
                    val barStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                }
                val snackbarHostState = remember { SnackbarHostState() }
                val message by viewModel.message.collectAsStateWithLifecycle()
                LaunchedEffect(message) {
                    val current = message
                    if (current != null) {
                        snackbarHostState.showSnackbar(current)
                        viewModel.consumeMessage()
                    }
                }
                BossClawAppContent(viewModel, snackbarHostState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refreshPermissions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BossClawAppContent(viewModel: BossClawViewModel, snackbarHostState: SnackbarHostState) {
    val backStack = rememberNavBackStack(AppKey(Routes.WORKBENCH))
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = (backStack.lastOrNull() as? AppKey)?.route ?: Routes.WORKBENCH
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // 底部 Tab 切换时回到根，再压入目标页；二级页面直接入栈。
    fun open(route: String) {
        if (route == currentRoute) return
        if (route in tabRoutes) {
            while (backStack.size > 1) backStack.removeLastOrNull()
        }
        backStack.add(AppKey(route))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 600dp 以上改用侧边导航，避免平板与折叠屏上的横向空间浪费。
        val wideLayout = maxWidth >= 600.dp

        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BossClawDrawer(
                currentRoute = currentRoute,
                engineLabel = engineState.label,
                runModeLabel = settings.runMode.label,
                onSelect = { route ->
                    scope.launch { drawerState.close() }
                    if (route != currentRoute) open(route)
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层放在 Scaffold 之下，顶栏与页面内容都透明，氛围可以贯穿全屏。
            AppBackdrop()
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    BossClawTopBar(
                        engineState = engineState,
                        onMenu = { scope.launch { drawerState.open() } },
                        onStatusClick = {
                            if (currentRoute != Routes.WORKBENCH) open(Routes.WORKBENCH)
                        }
                    )
                },
                bottomBar = {
                    if (!wideLayout) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    horizontal = BossClawSpacing.lg,
                                    vertical = BossClawSpacing.sm
                                )
                        ) {
                            BossClawFloatingNavBar(
                                currentRoute = currentRoute,
                                onSelect = { route ->
                                    if (route != currentRoute) open(route)
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (wideLayout) {
                        BossClawNavRail(
                            currentRoute = currentRoute,
                            onSelect = { route ->
                                if (route != currentRoute) open(route)
                            }
                        )
                    }
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.weight(1f),
                        onBack = {
                            if (backStack.size > 1) backStack.removeLastOrNull()
                        },
                        transitionSpec = {
                            (
                                fadeIn(tween(220)) + slideInHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedDecel
                                    ),
                                    initialOffsetX = { it / 9 }
                                )
                                ) togetherWith (
                                fadeOut(tween(160)) + slideOutHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedAccel
                                    ),
                                    targetOffsetX = { -it / 14 }
                                )
                                )
                        },
                        popTransitionSpec = {
                            (
                                fadeIn(tween(220)) + slideInHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedDecel
                                    ),
                                    initialOffsetX = { -it / 9 }
                                )
                                ) togetherWith (
                                fadeOut(tween(160)) + slideOutHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedAccel
                                    ),
                                    targetOffsetX = { it / 14 }
                                )
                                )
                        },
                        predictivePopTransitionSpec = { _ ->
                            (
                                fadeIn(tween(220)) + slideInHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedDecel
                                    ),
                                    initialOffsetX = { -it / 9 }
                                )
                                ) togetherWith (
                                fadeOut(tween(160)) + slideOutHorizontally(
                                    animationSpec = tween(
                                        BossClawMotion.BASE,
                                        easing = BossClawMotion.EmphasizedAccel
                                    ),
                                    targetOffsetX = { it / 14 }
                                )
                                )
                        },
                        entryProvider = entryProvider {
                            entry<AppKey> { key ->
                                when (key.route) {
                                    Routes.WORKBENCH -> WorkbenchScreen(
                                        viewModel,
                                        onNavigate = { route -> open(route) }
                                    )
                                    Routes.INSIGHTS -> InsightsScreen(
                                        viewModel,
                                        onOpenRecords = { open(Routes.RECORDS) }
                                    )
                                    Routes.JOBS -> JobsScreen(viewModel)
                                    Routes.CHAT -> ChatScreen(viewModel)
                                    Routes.RESUME -> ResumeScreen(viewModel)
                                    Routes.RECORDS -> RecordsScreen(viewModel)
                                    Routes.PERMISSIONS -> PermissionsScreen(viewModel)
                                    Routes.AUTOMATION -> AutomationSettingsScreen(viewModel)
                                    Routes.MODEL -> ModelSettingsScreen(viewModel)
                                    Routes.SKILLS -> SkillsSettingsScreen(viewModel)
                                    Routes.SETTINGS -> SettingsScreen(
                                        viewModel,
                                        onNavigate = { route -> open(route) }
                                    )
                                    else -> WorkbenchScreen(
                                        viewModel,
                                        onNavigate = { route -> open(route) }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BossClawTopBar(
    engineState: EngineState,
    onMenu: () -> Unit,
    onStatusClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = "打开侧栏")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "BossClaw",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        actions = {
            EngineStatusChip(state = engineState, onClick = onStatusClick)
            Spacer(modifier = Modifier.size(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
private fun BossClawFloatingNavBar(currentRoute: String, onSelect: (String) -> Unit) {
    val dark = LocalBossClawDark.current
    val shape = RoundedCornerShape(BossClawRadius.hero)
    // 大字号下隐藏文字标签，只留图标，避免四项文字挤在一起被截断。
    val largeFont = LocalDensity.current.fontScale >= 1.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .softShadow(BossClawElevation.floating, shape, BossClawShadow.brand(dark))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape)
            .padding(BossClawSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        workTabs.forEach { entry ->
            val selected = currentRoute == entry.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(BossClawRadius.button))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(entry.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(21.dp)
                )
                if (!largeFont) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BossClawNavRail(currentRoute: String, onSelect: (String) -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .padding(vertical = BossClawSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BossClawSpacing.xs)
    ) {
        workTabs.forEach { entry ->
            val selected = currentRoute == entry.route
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(BossClawRadius.button))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(entry.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = entry.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
                if (!largeFont) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStatusChip(state: EngineState, onClick: () -> Unit) {
    val color = when (state) {
        EngineState.RUNNING -> BossClawColors.Success
        EngineState.PAUSED -> BossClawColors.Amber
        EngineState.RISK_STOP -> BossClawColors.Danger
        EngineState.IDLE -> BossClawColors.Muted
    }
    val transition = rememberInfiniteTransition(label = "engine-chip")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == EngineState.RUNNING) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = BossClawMotion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "engine-chip-alpha"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.13f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color.copy(alpha = alpha), CircleShape)
            )
            Text(
                text = state.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun BossClawDrawer(
    currentRoute: String,
    engineLabel: String,
    runModeLabel: String,
    onSelect: (String) -> Unit
) {
    val dark = LocalBossClawDark.current
    ModalDrawerSheet(
        // 侧栏固定占屏宽 60%，其余部分留给页面内容与手势返回区域
        modifier = Modifier.fillMaxWidth(0.6f),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BossClawGradients.hero(dark))
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "BossClaw",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawerStat(label = "引擎", value = engineLabel)
                Spacer(modifier = Modifier.size(18.dp))
                DrawerStat(label = "模式", value = runModeLabel)
            }
        }

        Column(
            modifier = Modifier
                .padding(top = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            navSections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp)
                )
                section.entries.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.label, style = MaterialTheme.typography.titleMedium) },
                        selected = currentRoute == entry.route,
                        onClick = { onSelect(entry.route) },
                        icon = {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 1.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Color.White
        )
    }
}

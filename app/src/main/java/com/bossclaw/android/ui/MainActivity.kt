package com.bossclaw.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bossclaw.android.R
import com.bossclaw.android.agent.EngineState
import com.bossclaw.android.ui.components.AppBackdrop
import com.bossclaw.android.ui.navigation.Routes
import com.bossclaw.android.ui.navigation.navSections
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
import com.bossclaw.android.ui.theme.BossClawGradients
import com.bossclaw.android.ui.theme.BossClawMotion
import com.bossclaw.android.ui.theme.BossClawTheme
import com.bossclaw.android.ui.theme.LocalBossClawDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: BossClawViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BossClawViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            BossClawTheme(darkTheme = darkTheme) {
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
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.WORKBENCH
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BossClawDrawer(
                currentRoute = currentRoute,
                engineLabel = engineState.label,
                runModeLabel = settings.runMode.label,
                onSelect = { route ->
                    scope.launch { drawerState.close() }
                    if (route != currentRoute) navController.navigateTo(route)
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
                            if (currentRoute != Routes.WORKBENCH) navController.navigateTo(Routes.WORKBENCH)
                        }
                    )
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.WORKBENCH,
                    modifier = Modifier.padding(innerPadding),
                    enterTransition = {
                        fadeIn(tween(200)) + slideInHorizontally(
                            animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedDecel),
                            initialOffsetX = { it / 9 }
                        )
                    },
                    exitTransition = {
                        fadeOut(tween(150)) + slideOutHorizontally(
                            animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedAccel),
                            targetOffsetX = { -it / 14 }
                        )
                    },
                    popEnterTransition = {
                        fadeIn(tween(200)) + slideInHorizontally(
                            animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedDecel),
                            initialOffsetX = { -it / 9 }
                        )
                    },
                    popExitTransition = {
                        fadeOut(tween(150)) + slideOutHorizontally(
                            animationSpec = tween(BossClawMotion.BASE, easing = BossClawMotion.EmphasizedAccel),
                            targetOffsetX = { it / 14 }
                        )
                    }
                ) {
                    composable(Routes.WORKBENCH) {
                        WorkbenchScreen(viewModel, onNavigate = { route -> navController.navigateTo(route) })
                    }
                    composable(Routes.INSIGHTS) {
                        InsightsScreen(viewModel, onOpenRecords = { navController.navigateTo(Routes.RECORDS) })
                    }
                    composable(Routes.JOBS) { JobsScreen(viewModel) }
                    composable(Routes.CHAT) { ChatScreen(viewModel) }
                    composable(Routes.RESUME) { ResumeScreen(viewModel) }
                    composable(Routes.RECORDS) { RecordsScreen(viewModel) }
                    composable(Routes.PERMISSIONS) { PermissionsScreen(viewModel) }
                    composable(Routes.AUTOMATION) { AutomationSettingsScreen(viewModel) }
                    composable(Routes.MODEL) { ModelSettingsScreen(viewModel) }
                    composable(Routes.SKILLS) { SkillsSettingsScreen(viewModel) }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(viewModel, onNavigate = { route -> navController.navigateTo(route) })
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(Routes.WORKBENCH) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
        modifier = Modifier.widthIn(max = 316.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BossClawGradients.hero(dark))
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand_mark),
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "BossClaw",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "求职助理 · 本地运行",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
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
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "v1.0.0 · 数据仅保存在本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

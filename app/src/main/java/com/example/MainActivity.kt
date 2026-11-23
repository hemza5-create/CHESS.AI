package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AnalyzerScreen
import com.example.ui.screens.UciConsoleScreen
import com.example.ui.screens.VisionScannerScreen
import com.example.ui.theme.ChessVisionTheme
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.MonoFontFamily

const val ROUTE_ANALYZER = "analyzer"
const val ROUTE_VISION = "vision"
const val ROUTE_UCI = "uci"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessVisionTheme(darkTheme = true) {
                ChessVisionApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessVisionApp(
    viewModel: ChessVisionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_ANALYZER

    val destinations = listOf(
        NavDestinationItem(
            route = ROUTE_ANALYZER,
            labelRes = R.string.nav_analyzer,
            selectedIcon = Icons.Rounded.GridOn,
            unselectedIcon = Icons.Outlined.GridOn,
            testTag = "nav_analyzer_tab"
        ),
        NavDestinationItem(
            route = ROUTE_VISION,
            labelRes = R.string.nav_vision,
            selectedIcon = Icons.Rounded.CenterFocusWeak,
            unselectedIcon = Icons.Outlined.CenterFocusWeak,
            testTag = "nav_vision_tab"
        ),
        NavDestinationItem(
            route = ROUTE_UCI,
            labelRes = R.string.nav_uci,
            selectedIcon = Icons.Rounded.Terminal,
            unselectedIcon = Icons.Outlined.Terminal,
            testTag = "nav_uci_tab"
        )
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row {
                            Text(
                                text = stringResource(R.string.brand_title),
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " • UCI",
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        destinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_ANALYZER
                    ) {
                        composable(ROUTE_ANALYZER) {
                            AnalyzerScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                        composable(ROUTE_VISION) {
                            VisionScannerScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onAppliedToBoard = { navigateTo(ROUTE_ANALYZER) }
                            )
                        }
                        composable(ROUTE_UCI) {
                            UciConsoleScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.tassetbook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.ui.TAssetBookTheme
import com.tassetbook.app.ui.screens.AddAssetScreen
import com.tassetbook.app.ui.screens.AssetsScreen
import com.tassetbook.app.ui.screens.EstimateTradeScreen
import com.tassetbook.app.ui.screens.ExportConfigScreen
import com.tassetbook.app.ui.screens.MineScreen
import com.tassetbook.app.ui.screens.StatisticsScreen
import com.tassetbook.app.ui.screens.StockManageScreen
import com.tassetbook.app.ui.screens.TradeOperationScreen
import com.tassetbook.app.ui.screens.TradeRecordScreen

object Routes {
    const val ASSETS = "assets"
    const val STATISTICS = "statistics"
    const val MINE = "mine"
    const val ADD_ASSET = "addAsset"
    const val TRADE_OP = "tradeOperation"
    const val TRADE_RECORD = "tradeRecord"
    const val ESTIMATE = "estimate"
    const val EXPORT = "exportConfig"
    const val STOCK_MANAGE = "stockManage"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepository.init(applicationContext.filesDir)
        setContent {
            TAssetBookTheme {
                AppRoot()
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabItem(Routes.ASSETS, "股票", Icons.Filled.TrendingUp),
    TabItem(Routes.STATISTICS, "统计", Icons.Filled.BarChart),
    TabItem(Routes.MINE, "我的", Icons.Filled.Person)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == currentRoute?.substringBefore('?') }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute?.startsWith(tab.route) == true
                        NavigationBarItem(
                            selected = selected && (tab.route != Routes.ASSETS || currentRoute == Routes.ASSETS),
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.ASSETS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ASSETS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.ASSETS) {
                AssetsScreen(
                    onAddAsset = { navController.navigate(Routes.ADD_ASSET) },
                    onEditAsset = { id -> navController.navigate("${Routes.ADD_ASSET}?assetId=$id") },
                    onTradeOp = { assetId, assetName, status ->
                        navController.navigate("${Routes.TRADE_OP}?assetId=$assetId&assetName=$assetName&tradeStatus=$status&source=asset")
                    },
                    onTradeRecord = { assetId, assetName ->
                        navController.navigate("${Routes.TRADE_RECORD}?assetId=$assetId&assetName=$assetName")
                    },
                    onEstimate = { assetId, assetName ->
                        navController.navigate("${Routes.ESTIMATE}?assetId=$assetId&assetName=$assetName")
                    },
                    onManageStocks = { navController.navigate(Routes.STOCK_MANAGE) }
                )
            }
            composable(Routes.STOCK_MANAGE) {
                StockManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATISTICS) {
                StatisticsScreen()
            }
            composable(Routes.MINE) {
                MineScreen(
                    onExport = { navController.navigate(Routes.EXPORT) }
                )
            }
            composable(
                "${Routes.ADD_ASSET}?assetId={assetId}",
                arguments = listOf(navArgument("assetId") { nullable = true; type = NavType.StringType; defaultValue = null })
            ) { entry ->
                AddAssetScreen(
                    assetId = entry.arguments?.getString("assetId"),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "${Routes.TRADE_OP}?assetId={assetId}&assetName={assetName}&tradeStatus={tradeStatus}&tradeId={tradeId}&source={source}&isEdit={isEdit}",
                arguments = listOf(
                    navArgument("assetId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("assetName") { type = NavType.StringType; defaultValue = "" },
                    navArgument("tradeStatus") { type = NavType.StringType; defaultValue = "" },
                    navArgument("tradeId") { nullable = true; type = NavType.StringType; defaultValue = null },
                    navArgument("source") { type = NavType.StringType; defaultValue = "asset" },
                    navArgument("isEdit") { type = NavType.StringType; defaultValue = "false" }
                )
            ) { entry ->
                TradeOperationScreen(
                    assetId = entry.arguments?.getString("assetId").orEmpty(),
                    assetName = entry.arguments?.getString("assetName").orEmpty(),
                    tradeStatusArg = entry.arguments?.getString("tradeStatus").orEmpty(),
                    tradeId = entry.arguments?.getString("tradeId"),
                    source = entry.arguments?.getString("source") ?: "asset",
                    isEdit = entry.arguments?.getString("isEdit") == "true",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "${Routes.TRADE_RECORD}?assetId={assetId}&assetName={assetName}",
                arguments = listOf(
                    navArgument("assetId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("assetName") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                TradeRecordScreen(
                    assetId = entry.arguments?.getString("assetId").orEmpty(),
                    assetName = entry.arguments?.getString("assetName").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEditTrade = { assetId, assetName, tradeId, status ->
                        navController.navigate("${Routes.TRADE_OP}?assetId=$assetId&assetName=$assetName&tradeStatus=$status&tradeId=$tradeId&source=tradeRecord&isEdit=true")
                    },
                    onSupplement = { assetId, assetName, tradeId, status ->
                        navController.navigate("${Routes.TRADE_OP}?assetId=$assetId&assetName=$assetName&tradeStatus=$status&tradeId=$tradeId&source=tradeRecord&isEdit=false")
                    }
                )
            }
            composable(
                "${Routes.ESTIMATE}?assetId={assetId}&assetName={assetName}",
                arguments = listOf(
                    navArgument("assetId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("assetName") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                EstimateTradeScreen(
                    assetId = entry.arguments?.getString("assetId").orEmpty(),
                    assetName = entry.arguments?.getString("assetName").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.EXPORT) {
                ExportConfigScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

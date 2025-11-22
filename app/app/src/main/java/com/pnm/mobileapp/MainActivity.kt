package com.pnm.mobileapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.pnm.mobileapp.data.api.HubApiService
import com.pnm.mobileapp.data.database.AppDatabase
import com.pnm.mobileapp.data.model.Slip
import com.pnm.mobileapp.ui.dialog.CreateSlipDialog
import com.pnm.mobileapp.ui.component.BottomNavigationBar
import com.pnm.mobileapp.ui.screen.GenerateWalletScreen
import com.pnm.mobileapp.ui.screen.HistoryScreen
import com.pnm.mobileapp.ui.screen.HomeScreen
import com.pnm.mobileapp.ui.screen.HubScreen
import com.pnm.mobileapp.ui.screen.MerchantScreen
import com.pnm.mobileapp.ui.screen.ProfileScreen
import com.pnm.mobileapp.ui.screen.SettingsScreen
import com.pnm.mobileapp.ui.screen.UserScreen
import com.pnm.mobileapp.ui.theme.MobileAppTheme
import com.pnm.mobileapp.ui.viewmodel.AppViewModel
import com.pnm.mobileapp.ui.viewmodel.MerchantViewModel
import com.pnm.mobileapp.util.Constants
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

enum class UserRole {
    USER, MERCHANT, HUB
}

class MainActivity : FragmentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var hubApiService: HubApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pnm_database"
        )
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.HUB_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        hubApiService = retrofit.create(HubApiService::class.java)

        setContent {
            MobileAppTheme {
                MainScreen(database, hubApiService, this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    database: AppDatabase,
    hubApiService: HubApiService,
    activity: FragmentActivity
) {
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf(UserRole.USER) }
    var showSlipDialog by remember { mutableStateOf<Pair<Slip, String>?>(null) }
    val navController = rememberNavController()
    val appViewModel: AppViewModel = remember { AppViewModel(context, hubApiService) }
    val merchantViewModel = remember {
        // Pass merchant's Ethereum address from their wallet (for receiving payments)
        val merchantEthAddress = appViewModel.wallet.value?.ethAddress
        // Also pass user's Ethereum address as payerEthAddress to fix old vouchers that don't have ethAddress set
        // This assumes the user is redeeming their own vouchers, or we need to get it from the wallet
        val payerEthAddress = appViewModel.wallet.value?.ethAddress // User's own address
        MerchantViewModel(database.pendingSlipDao(), hubApiService, merchantEthAddress, payerEthAddress)
    }
    
    // Check if wallet exists
    val wallet by appViewModel.wallet.collectAsState()
    var hasCheckedWallet by remember { mutableStateOf(false) }
    
    // Wait for wallet to be loaded
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300) // Give time for wallet to load
        hasCheckedWallet = true
    }

    // Determine start destination based on wallet existence
    val startDestination = if (!hasCheckedWallet) {
        "loading"
    } else if (wallet == null) {
        "generate_wallet"
    } else {
        "home"
    }

    // Track current bottom nav route
    var currentBottomNavRoute by remember { mutableStateOf("home") }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("loading") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        composable("generate_wallet") {
            GenerateWalletScreen(
                viewModel = appViewModel,
                activity = activity,
                onWalletGenerated = {
                    navController.navigate("home") {
                        popUpTo("generate_wallet") { inclusive = true }
                    }
                }
            )
        }
        
        // Main screens with bottom navigation
        composable("home") {
            currentBottomNavRoute = "home"
            MainContentScreen(
                selectedRole = selectedRole,
                viewModel = appViewModel,
                merchantViewModel = merchantViewModel,
                hubApiService = hubApiService,
                onShowSlipDialog = { slip, voucherJson ->
                    showSlipDialog = Pair(slip, voucherJson)
                },
                activity = activity,
                currentRoute = currentBottomNavRoute,
                onNavigate = { route ->
                    currentBottomNavRoute = route
                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                onPayClick = {
                    // Pay button opens create slip flow
                    // Navigate to home if not already there, then trigger pay
                    if (currentBottomNavRoute != "home") {
                        navController.navigate("home") {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    }
                    // The pay action will be handled by HomeScreen
                }
            )
        }
        
        composable("profile") {
            currentBottomNavRoute = "profile"
            MainContentScreen(
                selectedRole = selectedRole,
                viewModel = appViewModel,
                merchantViewModel = merchantViewModel,
                hubApiService = hubApiService,
                onShowSlipDialog = { slip, voucherJson ->
                    showSlipDialog = Pair(slip, voucherJson)
                },
                activity = activity,
                currentRoute = currentBottomNavRoute,
                onNavigate = { route ->
                    currentBottomNavRoute = route
                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                onPayClick = {}
            )
        }
        
        composable("history") {
            currentBottomNavRoute = "history"
            MainContentScreen(
                selectedRole = selectedRole,
                viewModel = appViewModel,
                merchantViewModel = merchantViewModel,
                hubApiService = hubApiService,
                onShowSlipDialog = { slip, voucherJson ->
                    showSlipDialog = Pair(slip, voucherJson)
                },
                activity = activity,
                currentRoute = currentBottomNavRoute,
                onNavigate = { route ->
                    currentBottomNavRoute = route
                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                onPayClick = {}
            )
        }
        
        composable("settings") {
            currentBottomNavRoute = "settings"
            MainContentScreen(
                selectedRole = selectedRole,
                viewModel = appViewModel,
                merchantViewModel = merchantViewModel,
                hubApiService = hubApiService,
                onShowSlipDialog = { slip, voucherJson ->
                    showSlipDialog = Pair(slip, voucherJson)
                },
                activity = activity,
                currentRoute = currentBottomNavRoute,
                onNavigate = { route ->
                    currentBottomNavRoute = route
                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                onPayClick = {},
                onRoleSelected = { role ->
                    selectedRole = role
                }
            )
        }
        
        // Legacy routes for backward compatibility
        composable("user") {
            navController.navigate("home") {
                popUpTo("user") { inclusive = true }
            }
        }
        
        composable("merchant") {
            navController.navigate("home") {
                popUpTo("merchant") { inclusive = true }
            }
        }
        
        composable("hub") {
            navController.navigate("home") {
                popUpTo("hub") { inclusive = true }
            }
        }
    }

    showSlipDialog?.let { (slip, voucherJson) ->
        CreateSlipDialog(
            slip = slip,
            voucherJson = voucherJson,
            onDismiss = { showSlipDialog = null }
        )
    }
}

@Composable
fun MainContentScreen(
    selectedRole: UserRole,
    viewModel: AppViewModel,
    merchantViewModel: MerchantViewModel,
    hubApiService: HubApiService,
    onShowSlipDialog: (Slip, String) -> Unit,
    activity: FragmentActivity,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onPayClick: () -> Unit,
    onRoleSelected: ((UserRole) -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentRoute) {
            "home" -> {
                when (selectedRole) {
                    UserRole.USER -> HomeScreen(
                        viewModel = viewModel,
                        onShowSlipDialog = onShowSlipDialog,
                        activity = activity
                    )
                    UserRole.MERCHANT -> {
                        val wallet by viewModel.wallet.collectAsState()
                        MerchantScreen(
                            viewModel = merchantViewModel,
                            merchantEthAddress = wallet?.ethAddress,
                            payerEthAddress = wallet?.ethAddress, // For fixing old vouchers - assume user is redeeming their own vouchers
                            onScanQR = { }
                        )
                    }
                    UserRole.HUB -> HubScreen(hubApiService = hubApiService)
                }
            }
            "history" -> HistoryScreen(viewModel = viewModel)
            "profile" -> ProfileScreen(viewModel = viewModel)
            "settings" -> SettingsScreen(
                currentRole = selectedRole,
                onRoleSelected = { role ->
                    onRoleSelected?.invoke(role)
                }
            )
        }
        
        // Bottom navigation bar
        BottomNavigationBar(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            onPayClick = onPayClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

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
import com.pnm.mobileapp.ui.screen.GenerateWalletScreen
import com.pnm.mobileapp.ui.screen.HubScreen
import com.pnm.mobileapp.ui.screen.MerchantScreen
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
        ).build()

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
    val appViewModel: AppViewModel = remember { AppViewModel(context) }
    val merchantViewModel = remember {
        MerchantViewModel(database.pendingSlipDao(), hubApiService)
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
        "user"
    }

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
                    navController.navigate("user") {
                        popUpTo("generate_wallet") { inclusive = true }
                    }
                }
            )
        }
        
        composable("user") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("PNM") },
                        actions = {
                            RoleToggle(
                                selectedRole = selectedRole,
                                onRoleSelected = { role ->
                                    selectedRole = role
                                    when (role) {
                                        UserRole.USER -> navController.navigate("user") {
                                            popUpTo("user")
                                            launchSingleTop = true
                                        }
                                        UserRole.MERCHANT -> navController.navigate("merchant") {
                                            popUpTo("user")
                                            launchSingleTop = true
                                        }
                                        UserRole.HUB -> navController.navigate("hub") {
                                            popUpTo("user")
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    UserScreen(
                        viewModel = appViewModel,
                        onShowSlipDialog = { slip, voucherJson ->
                            showSlipDialog = Pair(slip, voucherJson)
                        },
                        activity = activity
                    )
                }
            }
        }
        
        composable("merchant") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("PNM") },
                        actions = {
                            RoleToggle(
                                selectedRole = selectedRole,
                                onRoleSelected = { role ->
                                    selectedRole = role
                                    when (role) {
                                        UserRole.USER -> navController.navigate("user") {
                                            popUpTo("merchant")
                                            launchSingleTop = true
                                        }
                                        UserRole.MERCHANT -> navController.navigate("merchant") {
                                            popUpTo("merchant")
                                            launchSingleTop = true
                                        }
                                        UserRole.HUB -> navController.navigate("hub") {
                                            popUpTo("merchant")
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    MerchantScreen(
                        viewModel = merchantViewModel,
                        onScanQR = { }
                    )
                }
            }
        }
        
        composable("hub") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("PNM") },
                        actions = {
                            RoleToggle(
                                selectedRole = selectedRole,
                                onRoleSelected = { role ->
                                    selectedRole = role
                                    when (role) {
                                        UserRole.USER -> navController.navigate("user") {
                                            popUpTo("hub")
                                            launchSingleTop = true
                                        }
                                        UserRole.MERCHANT -> navController.navigate("merchant") {
                                            popUpTo("hub")
                                            launchSingleTop = true
                                        }
                                        UserRole.HUB -> navController.navigate("hub") {
                                            popUpTo("hub")
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    )
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    HubScreen(hubApiService = hubApiService)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleToggle(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterChip(
            selected = selectedRole == UserRole.USER,
            onClick = { onRoleSelected(UserRole.USER) },
            label = { Text("USER") }
        )
        FilterChip(
            selected = selectedRole == UserRole.MERCHANT,
            onClick = { onRoleSelected(UserRole.MERCHANT) },
            label = { Text("MERCHANT") }
        )
        FilterChip(
            selected = selectedRole == UserRole.HUB,
            onClick = { onRoleSelected(UserRole.HUB) },
            label = { Text("HUB") }
        )
    }
}

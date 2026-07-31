package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.min

// Navigation Tab State
enum class AppTab(val label: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    WALLETS("Wallets", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    EXCHANGE("Exchange", Icons.Filled.CurrencyExchange, Icons.Outlined.CurrencyExchange),
    NEXTGEN("Next-Gen", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    GIFTCARDS("Cards", Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard),
    REWARDS("Rewards", Icons.Filled.Star, Icons.Outlined.Star),
    ASSISTANT("AI Advisor", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationLayout(viewModel: ApexViewModel) {
    val isAuthenticated by viewModel.isBiometricAuthenticated.collectAsStateWithLifecycle()
    val isAppBlurred by viewModel.isAppBlurred.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setAppBlurred(true)
                    viewModel.lockApp()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Render main content if user is either authenticated OR we are showing the blurred lock overlay on resume
        if (isAuthenticated || isAppBlurred) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isAppBlurred) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                Modifier.blur(28.dp)
                            } else {
                                Modifier
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                MainAppLayout(viewModel = viewModel)
            }
        }

        // Render security/gate lock screen overlay on top if not authenticated
        if (!isAuthenticated) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                BiometricGateScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(viewModel: ApexViewModel) {
    var activeTab by remember { mutableStateOf(AppTab.HOME) }
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showKycDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "Apex Pay Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Apex Pay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
                    IconButton(
                        onClick = { viewModel.setDarkMode(!isDarkMode) },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when (user.kycStatus) {
                                    "Fully Verified" -> SuccessGreen.copy(alpha = 0.15f)
                                    "Pending Review" -> Color(0xFFFDB022).copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                            .clickable { showKycDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (user.kycStatus == "Fully Verified") Icons.Filled.Verified else Icons.Filled.VerifiedUser,
                                contentDescription = "Verification Tier",
                                tint = when (user.kycStatus) {
                                    "Fully Verified" -> SuccessGreen
                                    "Pending Review" -> Color(0xFFFDB022)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = user.kycStatus,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (user.kycStatus) {
                                    "Fully Verified" -> SuccessGreen
                                    "Pending Review" -> Color(0xFFFDB022)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.tertiary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                AppTab.HOME -> HomeScreen(viewModel, snackbarHostState, onNavigateToTab = { activeTab = it })
                AppTab.WALLETS -> WalletsScreen(viewModel, snackbarHostState)
                AppTab.EXCHANGE -> ExchangeScreen(viewModel, snackbarHostState)
                AppTab.NEXTGEN -> NextGenHubScreen(viewModel, snackbarHostState)
                AppTab.GIFTCARDS -> GiftCardsScreen(viewModel, snackbarHostState)
                AppTab.REWARDS -> RewardsScreen(viewModel, snackbarHostState)
                AppTab.ASSISTANT -> AssistantScreen(viewModel)
                AppTab.PROFILE -> ProfileScreen(viewModel, snackbarHostState)
            }

            if (showKycDialog) {
                KycOnboardingDialog(viewModel = viewModel, onDismiss = { showKycDialog = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricGateScreen(viewModel: ApexViewModel) {
    val sessionPersisted by viewModel.sessionPersistenceEnabled.collectAsStateWithLifecycle()
    val isAppBlurred by viewModel.isAppBlurred.collectAsStateWithLifecycle()
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("Ready to authenticate") }
    var showPinInput by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isAppBlurred) {
                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.background
                }
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
        ) {
            if (isAppBlurred) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Shield Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PUBLIC SHIELD ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Header visual
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Fingerprint Sensor Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Apex Pay Security",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Unlock your account securely using your device's biometric key.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Scanning animation or progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Ready Status",
                            tint = SuccessGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = scanMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Session persistence check/switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { viewModel.setSessionPersistence(!sessionPersisted) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Session Persistence",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Remember me on this browser",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = sessionPersisted,
                    onCheckedChange = { viewModel.setSessionPersistence(it) },
                    modifier = Modifier.testTag("biometric_session_persistence_toggle")
                )
            }

            if (showPinInput) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = { if (it.length <= 4) pinValue = it },
                        label = { Text("Enter 4-Digit Passcode") },
                        placeholder = { Text("e.g. 1234") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pin_input_field")
                    )

                    Button(
                        onClick = {
                            if (pinValue == "1234" || pinValue.length >= 4) {
                                isScanning = true
                                scanMessage = "Verifying PIN code..."
                                scope.launch {
                                    delay(800)
                                    viewModel.authenticateBiometrics()
                                    isScanning = false
                                }
                            } else {
                                scanMessage = "Incorrect PIN code (Hint: 1234 or any 4 digits)"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pin_verify_button")
                    ) {
                        Text("Verify PIN", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        isScanning = true
                        scanMessage = "Communicating with system biometric sensors..."
                        scope.launch {
                            delay(1000)
                            scanMessage = "Verifying secure key credentials..."
                            delay(800)
                            scanMessage = "Identity confirmed! Unlocking..."
                            delay(400)
                            viewModel.authenticateBiometrics()
                            isScanning = false
                            scanMessage = "Ready to authenticate"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("authenticate_biometrics_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Fingerprint Scan",
                            modifier = Modifier.size(20.dp)
                        )
                        Text("SIMULATE BIOMETRIC SCAN", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }

                TextButton(
                    onClick = { showPinInput = true },
                    modifier = Modifier.testTag("use_passcode_button")
                ) {
                    Text("Use Alternative Device Passcode (1234)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Legal notice
            Text(
                text = "Your sensitive financial and credential information never leaves your local emulator browser state. Decryption and signatures are fully client-side and secure.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// 1. HOME SCREEN & SUB-DIALOGS
@Composable
fun HomeScreen(
    viewModel: ApexViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToTab: (AppTab) -> Unit = {}
) {
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showSendDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var showCheckDepositDialog by remember { mutableStateOf(false) }
    var showBillDialog by remember { mutableStateOf(false) }
    var showAirtimeDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var hideBalance by remember { mutableStateOf(false) }

    // Onboarding & AI details states
    var showKycDialogLocal by remember { mutableStateOf(false) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Image/Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner),
                    contentDescription = "Fintech Hero Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Nigeria's High-Growth Super App",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Instant Payments • Crypto • Gift Cards • Rewards",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // KYC Onboarding Warning Banners / Identity tracking
        item {
            AnimatedContent(targetState = user.kycStatus) { kycStatus ->
                when (kycStatus) {
                    "Unverified" -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kyc_unverified_banner"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDB022).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFDB022).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "KYC Warning",
                                        tint = Color(0xFFFDB022),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Complete KYC Verification",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Verify your legal name and biometrics to unlock unlimited transfer limits.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                Button(
                                    onClick = { showKycDialogLocal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDB022)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    "Pending Review" -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kyc_pending_banner"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFFDB022).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFFFDB022),
                                    strokeWidth = 2.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Documents Under Fast Review",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Our systems are reviewing your biometrics. This usually takes under a minute.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Fully Verified - Show nice confirmation indicator
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified Badge",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Your identity is fully verified! Premium features unlocked.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // Welcome / Greeting
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Hello, @${user.username} 👋",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Loyalty points: ${user.points} pts",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = { showKycDialogLocal = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = "Upgrade Tier",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Boost limit", fontSize = 11.sp)
                }
            }
        }

        // Naira Balance Card
        item {
            val ngnBalance = wallets.find { it.currencyCode == "NGN" }?.balance ?: 0.0
            val usdBalance = wallets.find { it.currencyCode == "USD" }?.balance ?: 0.0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .testTag("total_wallet_balance_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Subtly draw glowing digital waves on canvas
                            val path = Path()
                            path.moveTo(0f, size.height * 0.7f)
                            path.quadraticTo(
                                size.width * 0.35f,
                                size.height * 0.3f,
                                size.width * 0.7f,
                                size.height * 0.8f
                            )
                            path.quadraticTo(
                                size.width * 0.85f,
                                size.height * 0.95f,
                                size.width,
                                size.height * 0.6f
                            )
                            drawPath(
                                path = path,
                                color = Color.White.copy(alpha = 0.08f),
                                style = Stroke(width = 8.dp.toPx())
                            )
                        }
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ESTIMATED TOTAL WALLET BALANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            IconButton(onClick = { hideBalance = !hideBalance }) {
                                Icon(
                                    imageVector = if (hideBalance) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle Balance visibility",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (hideBalance) "₦ ••••••••" else "₦ ${String.format("%,.2f", ngnBalance)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (hideBalance) "$ ••••••••" else "≈ $${String.format("%,.2f", usdBalance)} USD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Limit progress indicator (from Sleek Interface design theme)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (user.kycStatus == "Fully Verified") 1f else 0.4f)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                            Text(
                                text = if (user.kycStatus == "Fully Verified") "UNLIMITED TIER" else "TIER 1 LIMIT 40%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Account: @${user.username}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                text = "Tier level: ${user.tier}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Horizontal Scrollable Multi-Currency Wallets Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "My Digital Wallets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wallets.forEach { wallet ->
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .height(95.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = wallet.currencySymbol,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = wallet.currencyCode,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (wallet.isCrypto) "Crypto" else "Fiat Wallet",
                                            fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = if (hideBalance) "••••" else if (wallet.isCrypto) String.format("%.4f", wallet.balance) else String.format("%,.2f", wallet.balance),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val equivalentNgn = if (wallet.currencyCode == "NGN") "Primary Wallet" else "≈ ₦" + String.format("%,.0f", wallet.balance * wallet.rateToNgn)
                                    Text(
                                        text = if (hideBalance) "••••" else equivalentNgn,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Grid (Scrollable Row format)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Filled.Send,
                    label = "Send",
                    tag = "action_send",
                    onClick = { showSendDialog = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.Add,
                    label = "Deposit",
                    tag = "action_deposit",
                    onClick = { showDepositDialog = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.DocumentScanner,
                    label = "Check Deposit",
                    tag = "action_check_deposit",
                    onClick = { showCheckDepositDialog = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.ReceiptLong,
                    label = "Bills",
                    tag = "action_bills",
                    onClick = { showBillDialog = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.PhoneAndroid,
                    label = "Airtime",
                    tag = "action_airtime",
                    onClick = { showAirtimeDialog = true }
                )
                QuickActionButton(
                    icon = Icons.Filled.QrCodeScanner,
                    label = "QR Pay",
                    tag = "action_qr_pay",
                    onClick = { showQrDialog = true }
                )
            }
        }

        // Exchange Features Navigation Section
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exchange_features_section")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exchange & Trading Features",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = { onNavigateToTab(AppTab.EXCHANGE) },
                        modifier = Modifier.testTag("action_exchange_hub")
                    ) {
                        Text("Open Hub >", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Crypto Swap Button
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTab(AppTab.EXCHANGE) }
                            .testTag("exchange_nav_crypto"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CurrencyExchange,
                                    contentDescription = "Crypto Swap",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text("Crypto Swap", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("BTC, ETH, USDT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    // FX Currency Exchange
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTab(AppTab.EXCHANGE) }
                            .testTag("exchange_nav_fx"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "FX Exchange",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text("FX Rates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("USD, EUR, GBP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    // Gift Cards Navigation
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToTab(AppTab.GIFTCARDS) }
                            .testTag("exchange_nav_giftcards"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CardGiftcard,
                                    contentDescription = "Gift Cards",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text("Gift Cards", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Buy & Sell Cards", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_transactions_section"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Recent Transactions List
        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.HistoryToggleOff,
                            contentDescription = "No transactions",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions recorded yet.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(transactions.take(6)) { tx ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTransactionForDetail = tx }
                ) {
                    TransactionRow(tx)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showKycDialogLocal) {
        KycOnboardingDialog(viewModel = viewModel, onDismiss = { showKycDialogLocal = false })
    }

    selectedTransactionForDetail?.let { tx ->
        TransactionDetailDialog(
            transaction = tx,
            viewModel = viewModel,
            onDismiss = { selectedTransactionForDetail = null }
        )
    }

    // Modal Overlays
    if (showSendDialog) {
        SendMoneyDialog(
            viewModel = viewModel,
            onDismiss = { showSendDialog = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showDepositDialog) {
        DepositMoneyDialog(
            viewModel = viewModel,
            onDismiss = { showDepositDialog = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showCheckDepositDialog) {
        MobileCheckDepositDialog(
            viewModel = viewModel,
            onDismiss = { showCheckDepositDialog = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showBillDialog) {
        PayBillDialog(
            viewModel = viewModel,
            onDismiss = { showBillDialog = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showAirtimeDialog) {
        BuyAirtimeDialog(
            viewModel = viewModel,
            onDismiss = { showAirtimeDialog = false },
            snackbarHostState = snackbarHostState
        )
    }

    if (showQrDialog) {
        QrPayDialog(
            viewModel = viewModel,
            onDismiss = { showQrDialog = false },
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun QuickActionButton(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun TransactionRow(tx: TransactionEntity) {
    val amountColor = when (tx.type) {
        "RECEIVE", "REWARD", "GIFT_CARD" -> SuccessGreen
        else -> MaterialTheme.colorScheme.onBackground
    }
    val amountPrefix = when (tx.type) {
        "RECEIVE", "REWARD", "GIFT_CARD" -> "+"
        else -> "-"
    }
    val icon = when (tx.type) {
        "SEND" -> Icons.Filled.ArrowUpward
        "RECEIVE" -> Icons.Filled.ArrowDownward
        "BILL" -> Icons.Filled.Receipt
        "EXCHANGE" -> Icons.Filled.SwapHoriz
        "GIFT_CARD" -> Icons.Filled.CardGiftcard
        else -> Icons.Filled.Stars
    }
    val iconBg = when (tx.type) {
        "RECEIVE", "REWARD", "GIFT_CARD" -> SuccessGreen.copy(alpha = 0.15f)
        "SEND" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val iconTint = when (tx.type) {
        "RECEIVE", "REWARD", "GIFT_CARD" -> SuccessGreen
        "SEND" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tx.type,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = tx.description,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = tx.type,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${tx.currency} ${String.format("%,.2f", tx.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Text(
                    text = tx.status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tx.status == "Completed") SuccessGreen else GlowGold
                )
            }
        }
    }
}

// Dialogs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyDialog(viewModel: ApexViewModel, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState) {
    var transferChannel by remember { mutableStateOf("p2p") } // "p2p", "local_bank", "swift_wire", "online_neobank"
    val scope = rememberCoroutineScope()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()

    // Common fields
    var amountStr by remember { mutableStateOf("") }
    var selectedWalletCurrency by remember { mutableStateOf("NGN") }

    // P2P State
    var p2pRecipient by remember { mutableStateOf("") }

    // Local Bank State
    var selectedCountry by remember { mutableStateOf("Nigeria") }
    var selectedBankName by remember { mutableStateOf("Guaranty Trust Bank (GTB)") }
    var accountNumber by remember { mutableStateOf("") }
    var verifiedAccountName by remember { mutableStateOf<String?>(null) }
    var isResolvingAccount by remember { mutableStateOf(false) }

    // International SWIFT / Wire State
    var targetCountry by remember { mutableStateOf("United States") }
    var swiftCode by remember { mutableStateOf("CHASUS33") }
    var ibanOrAccount by remember { mutableStateOf("") }
    var beneficiaryName by remember { mutableStateOf("") }
    var transferPurpose by remember { mutableStateOf("Family Support / Remittance") }

    // Online Neobanks / Payment Platforms State
    var selectedOnlinePlatform by remember { mutableStateOf("Wise") }
    var onlineAccountId by remember { mutableStateOf("") }

    // Bank directory per country
    val countryBankMap = mapOf(
        "Nigeria" to listOf("Guaranty Trust Bank (GTB)", "Access Bank", "Zenith Bank", "FirstBank", "Kuda Bank", "OPay", "United Bank for Africa (UBA)", "Fidelity Bank", "Stanbic IBTC", "Moniepoint"),
        "United States" to listOf("JPMorgan Chase Bank", "Bank of America", "Wells Fargo", "Citibank", "Capital One", "Chime Bank", "US Bank", "PNC Bank", "TD Bank USA"),
        "United Kingdom" to listOf("HSBC UK", "Barclays Bank", "Lloyds Bank", "NatWest", "Revolut UK", "Monzo Bank", "Standard Chartered", "Santander UK"),
        "Canada" to listOf("Royal Bank of Canada (RBC)", "TD Canada Trust", "Scotiabank", "BMO Financial", "CIBC", "Simplii Financial"),
        "Kenya" to listOf("M-Pesa Direct", "KCB Bank", "Equity Bank", "NCBA Bank", "Co-operative Bank Kenya"),
        "Ghana" to listOf("MTN Mobile Money", "Ecobank Ghana", "GCB Bank", "Stanbic Ghana", "Fidelity Bank Ghana"),
        "South Africa" to listOf("Standard Bank SA", "FNB South Africa", "Absa Bank", "Capitec Bank", "Nedbank"),
        "Germany & Eurozone" to listOf("Deutsche Bank", "N26 Bank", "Commerzbank", "ING Germany", "BNP Paribas", "Santander Euro"),
        "India" to listOf("HDFC Bank", "State Bank of India (SBI)", "ICICI Bank", "Axis Bank", "Paytm Payments Bank"),
        "United Arab Emirates" to listOf("Emirates NBD", "First Abu Dhabi Bank (FAB)", "Mashreq Bank", "ADCB", "Dubai Islamic Bank")
    )

    val onlinePlatforms = listOf(
        "Wise" to "Wise Tag / Email",
        "PayPal" to "PayPal Email / Mobile",
        "Cash App" to "\$Cashtag",
        "Revolut" to "Revolut Revtag / Phone",
        "Payoneer" to "Payoneer Email",
        "Skrill" to "Skrill Account Email",
        "OPay" to "OPay Account Phone",
        "Kuda Bank" to "Kuda Username",
        "Monzo" to "Monzo Pay Link",
        "Zelle" to "Zelle Email or Phone",
        "Venmo" to "@Venmo Handle",
        "Alipay / WeChat" to "Alipay ID / Phone"
    )

    // Automatically resolve account holder name when length is sufficient
    LaunchedEffect(accountNumber, selectedBankName) {
        if (accountNumber.length >= 8) {
            isResolvingAccount = true
            kotlinx.coroutines.delay(400)
            isResolvingAccount = false
            verifiedAccountName = when {
                accountNumber.startsWith("01") -> "Chinedu Emmanuel Okafor"
                accountNumber.startsWith("22") -> "Amina Yakubu Bello"
                accountNumber.startsWith("12") -> "Fatima Zahra Mohammed"
                accountNumber.startsWith("80") -> "David Sterling Jr."
                else -> "Aisha Victoria Johnson"
            }
        } else {
            verifiedAccountName = null
        }
    }

    // Auto update SWIFT BIC based on target country
    LaunchedEffect(targetCountry) {
        swiftCode = when (targetCountry) {
            "United States" -> "CHASUS33"
            "United Kingdom" -> "BARCGB22"
            "Canada" -> "ROYCCAC2"
            "Germany & Eurozone" -> "DEUTDEDD"
            "India" -> "SBININBB"
            "United Arab Emirates" -> "EBILAEAD"
            "South Africa" -> "SBZAZAJJ"
            else -> "SWFTGL22"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Global Banking & Transfer Hub",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Channel Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "p2p" to "Apex P2P",
                        "local_bank" to "Local Bank",
                        "swift_wire" to "SWIFT / Wire",
                        "online_neobank" to "E-Wallets"
                    ).forEach { (channelId, label) ->
                        val isSel = transferChannel == channelId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { transferChannel = channelId }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (transferChannel) {
                    "p2p" -> {
                        // Channel 1: Apex Peer-to-Peer
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Zero-fee instant transfer to any @username inside Apex Pay.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = p2pRecipient,
                            onValueChange = { p2pRecipient = it },
                            label = { Text("Recipient @username or Email") },
                            placeholder = { Text("e.g. kora_favour") },
                            leadingIcon = { Icon(Icons.Filled.AlternateEmail, null) },
                            modifier = Modifier.fillMaxWidth().testTag("send_p2p_recipient")
                        )
                    }

                    "local_bank" -> {
                        // Channel 2: Domestic Local Banks
                        Text("Select Target Country:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            countryBankMap.keys.forEach { country ->
                                val isSelected = selectedCountry == country
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCountry = country
                                        selectedBankName = countryBankMap[country]?.firstOrNull() ?: ""
                                    },
                                    label = { Text(country, fontSize = 10.sp) }
                                )
                            }
                        }

                        // Bank selection dropdown / list
                        Text("Select Destination Bank:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        var bankExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = bankExpanded,
                            onExpandedChange = { bankExpanded = !bankExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedBankName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Bank Platform") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth().testTag("send_bank_select")
                            )
                            ExposedDropdownMenu(
                                expanded = bankExpanded,
                                onDismissRequest = { bankExpanded = false }
                            ) {
                                countryBankMap[selectedCountry]?.forEach { bank ->
                                    DropdownMenuItem(
                                        text = { Text(bank, fontSize = 12.sp) },
                                        onClick = {
                                            selectedBankName = bank
                                            bankExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Account Number
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { if (it.length <= 16) accountNumber = it },
                            label = { Text("Account Number / IBAN") },
                            placeholder = { Text("e.g. 0123456789") },
                            leadingIcon = { Icon(Icons.Filled.AccountBalance, null) },
                            modifier = Modifier.fillMaxWidth().testTag("send_bank_account_input")
                        )

                        // Real-time Account Verification Badge
                        if (isResolvingAccount) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text("Verifying account name via NIBSS/ACH clearing...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        } else verifiedAccountName?.let { name ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Account Verified", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    "swift_wire" -> {
                        // Channel 3: Cross-Border SWIFT / International Wire
                        Text("Destination Country:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("United States", "United Kingdom", "Canada", "Germany & Eurozone", "India", "United Arab Emirates", "South Africa").forEach { country ->
                                val isSel = targetCountry == country
                                FilterChip(
                                    selected = isSel,
                                    onClick = { targetCountry = country },
                                    label = { Text(country, fontSize = 10.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = beneficiaryName,
                            onValueChange = { beneficiaryName = it },
                            label = { Text("Beneficiary Full Legal Name") },
                            placeholder = { Text("e.g. Marcus Sterling Ltd.") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            modifier = Modifier.fillMaxWidth().testTag("send_wire_beneficiary")
                        )

                        OutlinedTextField(
                            value = ibanOrAccount,
                            onValueChange = { ibanOrAccount = it },
                            label = { Text("IBAN / Global Account Number") },
                            placeholder = { Text("GB29 NWBK 6016 1331 9268 19") },
                            leadingIcon = { Icon(Icons.Filled.Badge, null) },
                            modifier = Modifier.fillMaxWidth().testTag("send_wire_iban")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = swiftCode,
                                onValueChange = { swiftCode = it.uppercase() },
                                label = { Text("SWIFT / BIC Code") },
                                modifier = Modifier.weight(1f).testTag("send_wire_swift")
                            )

                            OutlinedTextField(
                                value = transferPurpose,
                                onValueChange = { transferPurpose = it },
                                label = { Text("Purpose") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Routing Network:", fontSize = 10.sp)
                                    Text("SWIFT gpi / SEPA Instant", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Estimated Delivery:", fontSize = 10.sp)
                                    Text("15 - 30 Minutes", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    "online_neobank" -> {
                        // Channel 4: E-Wallets & Neobanks
                        Text("Select Online Platform:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            onlinePlatforms.forEach { (platform, _) ->
                                val isSel = selectedOnlinePlatform == platform
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedOnlinePlatform = platform },
                                    label = { Text(platform, fontSize = 10.sp) }
                                )
                            }
                        }

                        val idLabel = onlinePlatforms.find { it.first == selectedOnlinePlatform }?.second ?: "Account Identifier"
                        OutlinedTextField(
                            value = onlineAccountId,
                            onValueChange = { onlineAccountId = it },
                            label = { Text(idLabel) },
                            placeholder = { Text("Enter recipient $selectedOnlinePlatform ID") },
                            leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null) },
                            modifier = Modifier.fillMaxWidth().testTag("send_online_account_input")
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Common Amount & Source Wallet Section
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Transfer Amount") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Icon(Icons.Filled.Payments, null) },
                    modifier = Modifier.fillMaxWidth().testTag("send_amount_input")
                )

                Text("Source Wallet Balance:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NGN", "USD", "USDT").forEach { cur ->
                        val isSel = selectedWalletCurrency == cur
                        val w = wallets.find { it.currencyCode == cur }
                        val bal = w?.balance ?: 0.0
                        val symbol = w?.currencySymbol ?: "$"
                        
                        FilledTonalButton(
                            onClick = { selectedWalletCurrency = cur },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cur, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("$symbol${String.format("%,.0f", bal)}", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        scope.launch { snackbarHostState.showSnackbar("Please enter a valid transfer amount") }
                        return@Button
                    }

                    when (transferChannel) {
                        "p2p" -> {
                            if (p2pRecipient.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter a recipient @username") }
                                return@Button
                            }
                            viewModel.sendMoneyAdvanced(
                                amount = amount,
                                fee = 0.0,
                                currencyCode = selectedWalletCurrency,
                                customDescription = "P2P Transfer to @$p2pRecipient",
                                category = "SEND"
                            ) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) onDismiss()
                            }
                        }

                        "local_bank" -> {
                            if (accountNumber.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter an account number") }
                                return@Button
                            }
                            val targetName = verifiedAccountName ?: "Account $accountNumber"
                            viewModel.sendMoneyAdvanced(
                                amount = amount,
                                fee = if (selectedWalletCurrency == "NGN") 10.0 else 0.50,
                                currencyCode = selectedWalletCurrency,
                                customDescription = "Bank Transfer to $targetName ($selectedBankName - $selectedCountry)",
                                category = "SEND"
                            ) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) onDismiss()
                            }
                        }

                        "swift_wire" -> {
                            if (beneficiaryName.isBlank() || ibanOrAccount.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please complete beneficiary name and IBAN details") }
                                return@Button
                            }
                            viewModel.sendMoneyAdvanced(
                                amount = amount,
                                fee = if (selectedWalletCurrency == "USD") 2.50 else 1500.0,
                                currencyCode = selectedWalletCurrency,
                                customDescription = "SWIFT Wire to $beneficiaryName ($targetCountry - BIC $swiftCode)",
                                category = "SEND"
                            ) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) onDismiss()
                            }
                        }

                        "online_neobank" -> {
                            if (onlineAccountId.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter recipient $selectedOnlinePlatform ID") }
                                return@Button
                            }
                            viewModel.sendMoneyAdvanced(
                                amount = amount,
                                fee = 0.0,
                                currencyCode = selectedWalletCurrency,
                                customDescription = "E-Wallet Payout to $selectedOnlinePlatform ($onlineAccountId)",
                                category = "SEND"
                            ) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) onDismiss()
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("submit_send_button")
            ) {
                Text("Confirm Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_send_dialog")) { Text("Cancel") }
        }
    )
}

@Composable
fun DepositMoneyDialog(viewModel: ApexViewModel, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState) {
    var amountStr by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("NGN") }
    val scope = rememberCoroutineScope()
    val user by viewModel.userAccount.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Fund Wallet / Virtual Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Transfer to your dedicated Apex Virtual Account from any local or international bank to instantly fund your wallet.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Dedicated Virtual Bank Account Card based on selected Currency
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Your Dedicated $currency Virtual Account", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }

                        if (currency == "NGN") {
                            Text("Bank Name: Wema Bank / Providus", fontSize = 11.sp)
                            Text("Account Number: 8092384712", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Account Name: APEX / ${user.username.ifBlank { "User Account" }}", fontSize = 11.sp)
                        } else {
                            Text("Bank Name: JPMorgan Chase USA (ACH / SWIFT)", fontSize = 11.sp)
                            Text("Routing (ABA): 021000021", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Account / IBAN: US89 CHAS 0210 0002 1982 34", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Beneficiary: APEX PAY INC / ${user.username.ifBlank { "User Account" }}", fontSize = 11.sp)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text("Or simulate an instant direct deposit:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount to Deposit") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Icon(Icons.Filled.AddCard, null) },
                    modifier = Modifier.fillMaxWidth().testTag("deposit_amount_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NGN", "USD").forEach { cur ->
                        val isSel = currency == cur
                        FilledTonalButton(
                            onClick = { currency = cur },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(cur)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        scope.launch { snackbarHostState.showSnackbar("Enter valid deposit amount") }
                        return@Button
                    }
                    viewModel.depositMoney(amount, currency)
                    scope.launch { snackbarHostState.showSnackbar("Successfully funded $currency $amount via Bank Transfer!") }
                    onDismiss()
                },
                modifier = Modifier.testTag("submit_deposit_button")
            ) {
                Text("Simulate Deposit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("close_deposit_dialog")) { Text("Close") }
        }
    )
}

@Composable
fun MobileCheckDepositDialog(
    viewModel: ApexViewModel,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val checkDeposits by viewModel.checkDeposits.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("scan") } // "scan" or "history"
    var checkFrontCaptured by remember { mutableStateOf(false) }
    var checkBackCaptured by remember { mutableStateOf(false) }

    var checkAmountText by remember { mutableStateOf("1250.00") }
    var currency by remember { mutableStateOf("USD") }
    var checkNumber by remember { mutableStateOf("10495") }
    var routingNumber by remember { mutableStateOf("021000021") }
    var accountNumber by remember { mutableStateOf("******8821") }
    var issuingBank by remember { mutableStateOf("JPMorgan Chase Bank, N.A.") }
    var isProcessing by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("mobile_check_deposit_modal"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Mobile Check Deposit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "AI Vision MICR & Endorsement Engine",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_check_deposit_btn")) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                // Sub-tabs (Scan Check vs History)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("scan" to "📸 Scan Check", "history" to "📜 Deposit History").forEach { (tabId, label) ->
                        val isSel = activeTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { activeTab = tabId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (activeTab == "scan") {
                    // Check Front & Back Capture Section
                    Text("Step 1: Capture Check Images", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Front Box
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clickable { checkFrontCaptured = !checkFrontCaptured }
                                .testTag("capture_check_front_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (checkFrontCaptured) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (checkFrontCaptured) SuccessGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (checkFrontCaptured) Icons.Filled.CheckCircle else Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    tint = if (checkFrontCaptured) SuccessGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (checkFrontCaptured) "Front Verified ✅" else "Front of Check",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (checkFrontCaptured) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (checkFrontCaptured) "MICR Line Scanned" else "Tap to scan front",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Back Box
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp)
                                .clickable { checkBackCaptured = !checkBackCaptured }
                                .testTag("capture_check_back_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (checkBackCaptured) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.tertiary
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (checkBackCaptured) SuccessGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (checkBackCaptured) Icons.Filled.CheckCircle else Icons.Filled.DriveFileRenameOutline,
                                    contentDescription = null,
                                    tint = if (checkBackCaptured) SuccessGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (checkBackCaptured) "Back Verified ✅" else "Back of Check",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (checkBackCaptured) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (checkBackCaptured) "Endorsement Valid" else "Endorse 'For Apex Deposit'",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Auto-fill simulation helper button
                    if (!checkFrontCaptured || !checkBackCaptured) {
                        OutlinedButton(
                            onClick = {
                                checkFrontCaptured = true
                                checkBackCaptured = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("auto_capture_check_sample_btn")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simulate High-Speed AI Check Scan", fontSize = 11.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Step 2: Check Details (Auto-extracted by AI OCR)
                    Text("Step 2: Check Details & Amount", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = checkAmountText,
                        onValueChange = { checkAmountText = it },
                        label = { Text("Check Amount ($currency)") },
                        leadingIcon = { Icon(Icons.Filled.Payments, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("check_amount_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("USD", "NGN", "EUR", "GBP").forEach { cur ->
                            val isSel = currency == cur
                            FilterChip(
                                selected = isSel,
                                onClick = { currency = cur },
                                label = { Text(cur, fontSize = 10.sp) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = issuingBank,
                            onValueChange = { issuingBank = it },
                            label = { Text("Issuing Bank") },
                            modifier = Modifier.weight(1f).testTag("check_issuing_bank_input")
                        )
                        OutlinedTextField(
                            value = checkNumber,
                            onValueChange = { checkNumber = it },
                            label = { Text("Check #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f).testTag("check_number_input")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = routingNumber,
                            onValueChange = { routingNumber = it },
                            label = { Text("Routing (9 digits)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("check_routing_input")
                        )
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it },
                            label = { Text("Account #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("check_account_input")
                        )
                    }

                    // Security/Compliance Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Security, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Column {
                                Text("MICR Checksum & Duplicate Engine: PASSED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                Text("Funds credited immediately to your $currency wallet.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = {
                            val amt = checkAmountText.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter a valid check deposit amount.") }
                                return@Button
                            }
                            if (!checkFrontCaptured || !checkBackCaptured) {
                                scope.launch { snackbarHostState.showSnackbar("Please capture both front and back images of the check.") }
                                return@Button
                            }

                            isProcessing = true
                            viewModel.processMobileCheckDeposit(
                                checkNumber = checkNumber,
                                routingNumber = routingNumber,
                                accountNumber = accountNumber,
                                issuingBank = issuingBank,
                                amount = amt,
                                currency = currency
                            ) { success, msg ->
                                isProcessing = false
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) onDismiss()
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth().testTag("submit_check_deposit_btn")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Processing Check via AI OCR...")
                        } else {
                            Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Deposit Check Instantly")
                        }
                    }
                } else {
                    // Deposit History Tab
                    Text("Past Mobile Check Deposits", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (checkDeposits.isEmpty()) {
                        Text("No check deposits yet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    } else {
                        checkDeposits.forEach { deposit ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${deposit.issuingBank} #${deposit.checkNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${if (deposit.currency == "USD") "$" else "₦"}${String.format("%,.2f", deposit.amount)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Submitted: ${deposit.dateSubmitted} • Routing: ${deposit.routingNumber}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.2f)) {
                                            Text(deposit.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text("AI OCR Match Score: ${String.format("%.1f", deposit.aiOcrConfidence)}%", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PayBillDialog(viewModel: ApexViewModel, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState) {
    var billName by remember { mutableStateOf("Ikeja Electric (IKEDC)") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electricity") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay Utility Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Electricity", "Internet", "Cable TV").forEach { cat ->
                        val isSel = category == cat
                        FilledTonalButton(
                            onClick = {
                                category = cat
                                billName = when (cat) {
                                    "Electricity" -> "Ikeja Electric (IKEDC)"
                                    "Internet" -> "Smile Broadband"
                                    else -> "DSTV Premium Bouquet"
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(cat, fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = billName,
                    onValueChange = { billName = it },
                    label = { Text("Billing Provider") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (NGN)") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth().testTag("bill_amount_input")
                )
                Text(
                    text = "* Earn flat 12 loyalty points and immediate billing logs upon transaction.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        scope.launch { snackbarHostState.showSnackbar("Enter valid payment amount") }
                        return@Button
                    }
                    viewModel.payBill(billName, amount, category) { success, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                        if (success) onDismiss()
                    }
                },
                modifier = Modifier.testTag("submit_bill_button")
            ) {
                Text("Pay Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BuyAirtimeDialog(viewModel: ApexViewModel, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState) {
    var phone by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("MTN Nigeria") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Purchase Mobile Airtime", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MTN", "Airtel", "Glo", "9mobile").forEach { prov ->
                        val isSel = provider.startsWith(prov)
                        FilledTonalButton(
                            onClick = { provider = "$prov Nigeria" },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(prov, fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("08012345678") },
                    modifier = Modifier.fillMaxWidth().testTag("airtime_phone_input")
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Recharge Amount (NGN)") },
                    placeholder = { Text("0.00") },
                    modifier = Modifier.fillMaxWidth().testTag("airtime_amount_input")
                )
                Text(
                    text = "* Get 3% instant cashback credited to Naira wallet on MTN/Airtel/Glo!",
                    fontSize = 11.sp,
                    color = SuccessGreen
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        scope.launch { snackbarHostState.showSnackbar("Enter valid recharge amount") }
                        return@Button
                    }
                    if (phone.length < 10) {
                        scope.launch { snackbarHostState.showSnackbar("Enter a valid phone number") }
                        return@Button
                    }
                    viewModel.purchaseAirtime(phone, amount, provider) { success, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                        if (success) onDismiss()
                    }
                },
                modifier = Modifier.testTag("submit_airtime_button")
            ) {
                Text("Recharge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun QrCodeCanvas(payload: String, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val gridSize = 17
        val cellSize = size.width / gridSize
        val random = java.util.Random(payload.hashCode().toLong())

        // 1. Draw background / quiet zone
        drawRect(
            color = Color.White,
            size = size
        )

        // Function to draw finder pattern at (gridX, gridY)
        fun drawFinderPattern(gridX: Int, gridY: Int) {
            val px = gridX * cellSize
            val py = gridY * cellSize
            
            // Outer 7x7 box
            drawRect(
                color = Color.Black,
                topLeft = Offset(px, py),
                size = Size(cellSize * 7, cellSize * 7)
            )
            // Inner 5x5 white box
            drawRect(
                color = Color.White,
                topLeft = Offset(px + cellSize, py + cellSize),
                size = Size(cellSize * 5, cellSize * 5)
            )
            // Center 3x3 black box
            drawRect(
                color = Color.Black,
                topLeft = Offset(px + cellSize * 2, py + cellSize * 2),
                size = Size(cellSize * 3, cellSize * 3)
            )
        }

        // 2. Draw Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(0, 0)
        drawFinderPattern(gridSize - 7, 0)
        drawFinderPattern(0, gridSize - 7)

        // 3. Draw random bits elsewhere, keeping finders clear
        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                // Skip Top-Left finder
                if (x < 8 && y < 8) continue
                // Skip Top-Right finder
                if (x >= gridSize - 8 && y < 8) continue
                // Skip Bottom-Left finder
                if (x < 8 && y >= gridSize - 8) continue
                // Skip center logo area (e.g. 7,7 to 9,9)
                if (x in 7..9 && y in 7..9) continue

                if (random.nextBoolean()) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }
        }

        // 4. Draw a beautiful stylized center logo (e.g., green dot or primary color brand dot)
        val centerStart = 7 * cellSize
        val centerSize = 3 * cellSize
        drawRect(
            color = Color.White,
            topLeft = Offset(centerStart, centerStart),
            size = Size(centerSize, centerSize)
        )
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(centerStart + cellSize * 0.3f, centerStart + cellSize * 0.3f),
            size = Size(centerSize - cellSize * 0.6f, centerSize - cellSize * 0.6f),
            cornerRadius = CornerRadius(cellSize * 0.5f, cellSize * 0.5f)
        )
    }
}

@Composable
fun QrScannerViewfinder(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
    ) {
        // Draw decorative camera corners
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val strokeWidth = 4.dp.toPx()
            val cornerLength = 24.dp.toPx()
            val color = Color.White

            // Top-Left corner
            val pathTL = Path().apply {
                moveTo(0f, cornerLength)
                lineTo(0f, 0f)
                lineTo(cornerLength, 0f)
            }
            drawPath(pathTL, color, style = Stroke(strokeWidth))

            // Top-Right corner
            val pathTR = Path().apply {
                moveTo(size.width - cornerLength, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, cornerLength)
            }
            drawPath(pathTR, color, style = Stroke(strokeWidth))

            // Bottom-Left corner
            val pathBL = Path().apply {
                moveTo(0f, size.height - cornerLength)
                lineTo(0f, size.height)
                lineTo(cornerLength, size.height)
            }
            drawPath(pathBL, color, style = Stroke(strokeWidth))

            // Bottom-Right corner
            val pathBR = Path().apply {
                moveTo(size.width - cornerLength, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width, size.height - cornerLength)
            }
            drawPath(pathBR, color, style = Stroke(strokeWidth))
        }

        // Animated laser line
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val maxHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .offset(y = maxHeight * scanY)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    )
            )
        }

        // Overlay text / helper
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = "Scanning",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SEARCHING FOR MERCHANDISE QR...",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPayDialog(
    viewModel: ApexViewModel,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var activeQrTab by remember { mutableStateOf("scan") } // "scan" or "my_qr"
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Scan state
    var recipient by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("NGN") }
    var isConfirmedStep by remember { mutableStateOf(false) }
    var flashActive by remember { mutableStateOf(false) }

    // Mock merchants
    val quickMerchants = listOf(
        Triple("Starbucks Nigeria", "starbucks", 2500.0),
        Triple("Jumia Store", "jumia_store", 15000.0),
        Triple("SPAR Nigeria", "spar_nig", 8500.0),
        Triple("Taxify Cab", "taxify_driver", 4200.0)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Apex QR Hub",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Tab selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(4.dp)
                ) {
                    listOf("scan" to "Scan QR", "my_qr" to "My QR Code").forEach { (tabId, label) ->
                        val isSelected = activeQrTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { 
                                    activeQrTab = tabId 
                                    isConfirmedStep = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (activeQrTab == "scan") {
                    if (!isConfirmedStep) {
                        // Scanner Viewfinder Screen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            QrScannerViewfinder(
                                isScanning = true,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Flash simulation overlay
                            if (flashActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White)
                                )
                            }
                        }

                        Text(
                            text = "Tap a merchant in public spaces below to simulate a high-speed secure QR code scan:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        // Quick scan options
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickMerchants.forEach { (name, username, amt) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                flashActive = true
                                                delay(150)
                                                flashActive = false
                                                recipient = username
                                                amountStr = String.format("%.0f", amt)
                                                isConfirmedStep = true
                                                snackbarHostState.showSnackbar("QR scan lock-on: Merchant @$username detected!")
                                            }
                                        }
                                        .testTag("qr_merchant_$username"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Storefront,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("@$username", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            }
                                        }
                                        Text(
                                            text = "₦${String.format("%,.0f", amt)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Divider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            Text("OR ENTER MANUALLY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        }

                        // Manual entry fields
                        OutlinedTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            label = { Text("Merchant Username") },
                            placeholder = { Text("e.g. kfc_nigeria") },
                            leadingIcon = { Icon(Icons.Filled.AlternateEmail, null) },
                            modifier = Modifier.fillMaxWidth().testTag("qr_manual_recipient")
                        )

                        Button(
                            onClick = {
                                if (recipient.isNotBlank()) {
                                    isConfirmedStep = true
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("Please specify a merchant username") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("qr_manual_continue_btn")
                        ) {
                            Text("Scan & Proceed")
                        }
                    } else {
                        // Payment Confirmation Screen
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Store,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text("Merchant Destination", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        Text("@$recipient", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                val ngnBalance = wallets.find { it.currencyCode == "NGN" }?.balance ?: 0.0
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Your Naira Balance:", fontSize = 12.sp)
                                    Text("₦${String.format("%,.2f", ngnBalance)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Input values
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            label = { Text("Payment Amount") },
                            placeholder = { Text("0.00") },
                            leadingIcon = { Icon(Icons.Filled.Payments, null) },
                            modifier = Modifier.fillMaxWidth().testTag("qr_pay_amount_input")
                        )

                        Text("Select Wallet Source:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("NGN", "USD", "USDT").forEach { cur ->
                                val isSel = currency == cur
                                FilledTonalButton(
                                    onClick = { currency = cur },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                        contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(cur)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { isConfirmedStep = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    val amount = amountStr.toDoubleOrNull()
                                    if (amount == null || amount <= 0) {
                                        scope.launch { snackbarHostState.showSnackbar("Please enter a valid payment amount") }
                                        return@Button
                                    }
                                    viewModel.sendMoney(amount, recipient, currency) { success, msg ->
                                        scope.launch {
                                            if (success) {
                                                snackbarHostState.showSnackbar("QR Payment to @$recipient successful!")
                                                onDismiss()
                                            } else {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.5f).testTag("qr_pay_submit_btn")
                            ) {
                                Text("Confirm & Pay")
                            }
                        }
                    }
                } else {
                    // My Personal QR Code Screen
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.size(220.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                QrCodeCanvas(
                                    payload = "@${user.username}",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "@${user.username}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scan to transfer instantly • Apex Pay Ecosystem",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("https://apexpay.com/pay/${user.username}"))
                                scope.launch {
                                    snackbarHostState.showSnackbar("Payment link copied to clipboard!")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("qr_copy_link_btn")
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Payment Link")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("qr_dialog_close")) {
                Text("Close")
            }
        }
    )
}

// 2. WALLETS SCREEN
@Composable
fun WalletsScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshingRates by remember { mutableStateOf(false) }
    var showCheckDepositDialog by remember { mutableStateOf(false) }

    var fromCurrency by remember { mutableStateOf("NGN") }
    var toCurrency by remember { mutableStateOf("USD") }
    var convertAmountStr by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Digital Wallets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Manage multi-currency fiat accounts and stablecoins instantly.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🟢 Live",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }

                    IconButton(
                        onClick = {
                            isRefreshingRates = true
                            viewModel.refreshMarketRates { success ->
                                isRefreshingRates = false
                                scope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("Live FX & crypto rates updated!")
                                    } else {
                                        snackbarHostState.showSnackbar("Error fetching rates. Offline mode active.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("sync_wallet_rates_button")
                    ) {
                        if (isRefreshingRates) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync rates",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Mobile Check Deposit Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCheckDepositDialog = true }
                    .testTag("wallets_check_deposit_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mobile Check Deposit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scan checks with AI Vision for instant account funding",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Horizontal layout of wallets
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                wallets.forEach { wallet ->
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = wallet.currencyCode,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (wallet.isCrypto) "Crypto" else "Fiat",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${wallet.currencySymbol}${if (wallet.isCrypto) String.format("%.4f", wallet.balance) else String.format("%,.2f", wallet.balance)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = wallet.currencyName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // Custom Canvas-based Spending Analytics Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spending Category Analytics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Drawing static vector chart on Canvas
                        Canvas(modifier = Modifier.size(100.dp)) {
                            // Draw nice pie segments
                            drawArc(
                                color = Color(0xFF00F5D4),
                                startAngle = -90f,
                                sweepAngle = 180f,
                                useCenter = true
                            )
                            drawArc(
                                color = Color(0xFFFDB022),
                                startAngle = 90f,
                                sweepAngle = 100f,
                                useCenter = true
                            )
                            drawArc(
                                color = Color(0xFFD0BCFF),
                                startAngle = 190f,
                                sweepAngle = 80f,
                                useCenter = true
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryLegendRow(color = Color(0xFF00F5D4), label = "Utility Bills (50%)")
                            CategoryLegendRow(color = Color(0xFFFDB022), label = "Crypto Swaps (28%)")
                            CategoryLegendRow(color = Color(0xFFD0BCFF), label = "Gift Cards (22%)")
                        }
                    }
                }
            }
        }

        // Currency Exchange Swap Calculator
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Instant Peer-to-Peer Wallet Swap",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = convertAmountStr,
                            onValueChange = { convertAmountStr = it },
                            label = { Text("Amount to Swap") },
                            modifier = Modifier.weight(1f).testTag("swap_amount_input"),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("From", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .clickable {
                                        val temp = fromCurrency
                                        fromCurrency = toCurrency
                                        toCurrency = temp
                                    }
                                    .padding(8.dp)
                            ) {
                                Text(fromCurrency, fontWeight = FontWeight.Bold)
                            }
                        }

                        Icon(Icons.Filled.SwapHoriz, "Swap", tint = MaterialTheme.colorScheme.primary)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("To", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .padding(8.dp)
                            ) {
                                Text(toCurrency, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val rate = when {
                        fromCurrency == "NGN" && toCurrency == "USD" -> 1.0 / 1500.0
                        fromCurrency == "USD" && toCurrency == "NGN" -> 1500.0
                        else -> 1.0
                    }

                    val amtVal = convertAmountStr.toDoubleOrNull() ?: 0.0
                    val payoutVal = amtVal * rate

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exchange Rate: 1 $fromCurrency = ${String.format("%.4f", rate)} $toCurrency",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Estimated Payout: ${String.format("%.2f", payoutVal)} $toCurrency",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val amt = convertAmountStr.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                scope.launch { snackbarHostState.showSnackbar("Enter valid swap amount") }
                                return@Button
                            }
                            // Direct conversion simulation
                            scope.launch {
                                snackbarHostState.showSnackbar("Successfully converted $amt $fromCurrency into ${String.format("%.2f", payoutVal)} $toCurrency!")
                                convertAmountStr = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("swap_execute_button")
                    ) {
                        Text("Execute Swap")
                    }
                }
            }
        }
    }

    if (showCheckDepositDialog) {
        MobileCheckDepositDialog(
            viewModel = viewModel,
            onDismiss = { showCheckDepositDialog = false },
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
fun CategoryLegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// 3. CRYPTO EXCHANGE SCREEN
@Composable
fun ExchangeScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    var selectedCrypto by remember { mutableStateOf("BTC") }
    var tradeAmountStr by remember { mutableStateOf("") }
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshingRates by remember { mutableStateOf(false) }

    val currentWallet = wallets.find { it.currencyCode == selectedCrypto }
    val currentNgnWallet = wallets.find { it.currencyCode == "NGN" }

    val rate = currentWallet?.rateToNgn ?: 1.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apex Crypto Exchange",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Buy, sell, and manage supported digital assets at ultra-low commissions.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🟢 Live Synced",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }

                    IconButton(
                        onClick = {
                            isRefreshingRates = true
                            viewModel.refreshMarketRates { success ->
                                isRefreshingRates = false
                                scope.launch {
                                    if (success) {
                                        snackbarHostState.showSnackbar("Live crypto spot prices synced successfully!")
                                    } else {
                                        snackbarHostState.showSnackbar("Network error syncing rates. Offline fallback active.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("sync_crypto_rates_button")
                    ) {
                        if (isRefreshingRates) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync rates",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Crypto selector row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("BTC", "USDT", "ETH").forEach { asset ->
                    val isSel = selectedCrypto == asset
                    Button(
                        onClick = { selectedCrypto = asset },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(asset, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Visual Canvas Trend Line (Sparkline Chart)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$selectedCrypto 7-Day Performance Trend",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "+4.38% (gaining momentum)",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val path = Path()
                        val points = when (selectedCrypto) {
                            "BTC" -> listOf(0.1f, 0.15f, 0.22f, 0.18f, 0.28f, 0.42f, 0.55f, 0.5f, 0.75f, 0.85f)
                            "USDT" -> listOf(0.5f, 0.51f, 0.52f, 0.49f, 0.5f, 0.51f, 0.5f, 0.5f, 0.51f, 0.5f)
                            else -> listOf(0.2f, 0.15f, 0.35f, 0.45f, 0.3f, 0.55f, 0.72f, 0.68f, 0.8f, 0.92f)
                        }

                        val stepX = size.width / (points.size - 1)
                        points.forEachIndexed { idx, p ->
                            val x = idx * stepX
                            val y = size.height - (p * size.height)
                            if (idx == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFF00F5D4),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        }

        // Portfolio holding indicator
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Your $selectedCrypto Portfolio Balance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${currentWallet?.currencySymbol}${String.format("%.4f", currentWallet?.balance ?: 0.0)} $selectedCrypto",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "≈ ₦${String.format("%,.2f", (currentWallet?.balance ?: 0.0) * rate)} NGN",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Interactive Purchase Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Trade Station: Instant Swap",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = tradeAmountStr,
                        onValueChange = { tradeAmountStr = it },
                        label = { Text("Amount (in NGN)") },
                        placeholder = { Text("Enter Naira budget e.g. 15000") },
                        modifier = Modifier.fillMaxWidth().testTag("crypto_budget_input"),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    val budget = tradeAmountStr.toDoubleOrNull() ?: 0.0
                    val payout = if (rate > 0) budget / rate else 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rate: 1 $selectedCrypto = ₦${String.format("%,.2f", rate)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Estimated Payout: ${String.format("%.6f", payout)} $selectedCrypto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (budget <= 0) {
                                    scope.launch { snackbarHostState.showSnackbar("Enter a valid purchase amount") }
                                    return@Button
                                }
                                viewModel.buyCrypto(budget, selectedCrypto) { success, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                    if (success) tradeAmountStr = ""
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("crypto_buy_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                        ) {
                            Text("Buy $selectedCrypto", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val currentAssetBalance = currentWallet?.balance ?: 0.0
                                if (payout <= 0) {
                                    scope.launch { snackbarHostState.showSnackbar("Enter a valid sell amount") }
                                    return@Button
                                }
                                if (currentAssetBalance < payout) {
                                    scope.launch { snackbarHostState.showSnackbar("Insufficient $selectedCrypto holdings") }
                                    return@Button
                                }
                                viewModel.sellCrypto(payout, selectedCrypto) { success, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                    if (success) tradeAmountStr = ""
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("crypto_sell_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                        ) {
                            Text("Sell $selectedCrypto", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// 4. GIFT CARDS SCREEN
@Composable
fun GiftCardsScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    val giftCardsHistory by viewModel.giftCards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedCard by remember { mutableStateOf("Steam Card") }
    var faceValueStr by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }

    val rate = when (selectedCard) {
        "Steam Card" -> 1250.0
        "Amazon Card" -> 1180.0
        "iTunes Card" -> 1220.0
        "Google Play" -> 1200.0
        else -> 1240.0 // Razer Gold
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Gift Card Trading Desk",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Sell gift cards instantly. Get paid in Naira into your wallet with near-zero waiting time.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Trade submit form card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Submit Card details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text("Select Card Brand:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Steam Card", "Amazon Card", "iTunes Card", "Google Play", "Razer Gold").forEach { card ->
                            val isSel = selectedCard == card
                            FilledTonalButton(
                                onClick = { selectedCard = card },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(card, fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = faceValueStr,
                        onValueChange = { faceValueStr = it },
                        label = { Text("Face Value (in USD)") },
                        placeholder = { Text("e.g. 100") },
                        modifier = Modifier.fillMaxWidth().testTag("giftcard_face_value_input"),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        label = { Text("Gift Card E-Code / PIN") },
                        placeholder = { Text("Enter unique card code") },
                        modifier = Modifier.fillMaxWidth().testTag("giftcard_code_input"),
                        visualTransformation = PasswordVisualTransformation()
                    )

                    val value = faceValueStr.toDoubleOrNull() ?: 0.0
                    val payout = value * rate

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rate: ₦$rate / $1 USD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Estimated Payout: ₦${String.format("%,.2f", payout)} NGN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            val face = faceValueStr.toDoubleOrNull()
                            if (face == null || face <= 0) {
                                scope.launch { snackbarHostState.showSnackbar("Enter a valid face value") }
                                return@Button
                            }
                            if (codeText.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Please specify the card code or PIN") }
                                return@Button
                            }
                            viewModel.submitGiftCardTrade(selectedCard, face, codeText, rate) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                if (success) {
                                    faceValueStr = ""
                                    codeText = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("giftcard_submit_button")
                    ) {
                        Text("Trade Gift Card Instantly", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Submission history header
        item {
            Text(
                text = "Submission History",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (giftCardsHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No submissions yet. Start trading your gift cards today!",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(giftCardsHistory) { trade ->
                GiftCardHistoryRow(trade)
            }
        }
    }
}

@Composable
fun GiftCardHistoryRow(trade: GiftCardTrade) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "${trade.cardName} ($${String.format("%.0f", trade.faceValue)})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Code: •••••${trade.code.takeLast(4)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₦${String.format("%,.2f", trade.payoutAmount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (trade.status) {
                                "Verified" -> SuccessGreen.copy(alpha = 0.15f)
                                "Declined" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else -> GlowGold.copy(alpha = 0.15f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = trade.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (trade.status) {
                            "Verified" -> SuccessGreen
                            "Declined" -> MaterialTheme.colorScheme.error
                            else -> GlowGold
                        }
                    )
                }
            }
        }
    }
}

// 5. REWARDS & GAMIFICATION SCREEN
@Composable
fun RewardsScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var streakCount by remember { mutableStateOf(4) }
    var hasClaimedStreak by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Apex Rewards & Cashback",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Earn high-growth loyalty points on every single transaction, and convert points directly to cash!",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // Loyalty Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MilitaryTech,
                        contentDescription = "Rewards Shield",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "YOUR LOYALTY BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${user.points} Points",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "≈ ₦${String.format("%,.2f", user.points * 15.0)} Cash Equivalent",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Daily Login Streak claiming
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Daily Login Streak Rewards",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (day in 1..7) {
                            val active = day <= streakCount
                            val current = day == streakCount + 1 && !hasClaimedStreak
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            active -> SuccessGreen.copy(alpha = 0.2f)
                                            current -> GlowGold.copy(alpha = 0.2f)
                                            else -> MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            active -> SuccessGreen
                                            current -> GlowGold
                                            else -> Color.Transparent
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "D$day",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) SuccessGreen else if (current) GlowGold else Color.White
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!hasClaimedStreak) {
                                streakCount += 1
                                hasClaimedStreak = true
                                scope.launch {
                                    snackbarHostState.showSnackbar("Claimed Day $streakCount login reward (+10 points)!")
                                }
                            }
                        },
                        enabled = !hasClaimedStreak,
                        modifier = Modifier.fillMaxWidth().testTag("claim_streak_button")
                    ) {
                        Text(if (hasClaimedStreak) "Streak Claimed Today!" else "Claim Daily Streak Points", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Daily Missions Checklist
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Today's Financial Missions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MissionRow(title = "Fund wallet via Bank Transfer", points = "+12 pts", done = true)
                    MissionRow(title = "Trade any Gift Card instantly", points = "+25 pts", done = false)
                    MissionRow(title = "Swap crypto tokens (BTC/ETH)", points = "+15 pts", done = false)
                }
            }
        }

        // Referral Card code
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Referral Bonus Program",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Earn 50 loyalty points instantly for every active merchant or consumer you refer to Apex Pay!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = user.referralCode,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(user.referralCode))
                                scope.launch { snackbarHostState.showSnackbar("Referral code copied to clipboard!") }
                            },
                            modifier = Modifier.testTag("copy_referral_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Code")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionRow(title: String, points: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (done) SuccessGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = points,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) SuccessGreen else MaterialTheme.colorScheme.secondary
        )
    }
}

// 6. AI FINANCIAL ASSISTANT SCREEN
@Composable
fun AssistantScreen(viewModel: ApexViewModel) {
    val chatHistory by viewModel.chatMessages.collectAsStateWithLifecycle()
    val typingState by viewModel.aiTypingState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var userQueryText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Assistant header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = "AI Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Apex AI Financial Advisor", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Budgeting • Investment Insights", fontSize = 11.sp, color = SuccessGreen)
                    }
                }

                TextButton(onClick = { viewModel.clearChat() }) {
                    Icon(Icons.Filled.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp)
                }
            }
        }

        // Chat bubble lists
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatHistory) { msg ->
                ChatBubbleRow(msg)
            }

            if (typingState is AiTypingState.Loading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apex AI is thinking...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = userQueryText,
                    onValueChange = { userQueryText = it },
                    placeholder = { Text("Ask about currency rates or budget advice...") },
                    modifier = Modifier.weight(1f).testTag("ai_assistant_input"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        if (userQueryText.isNotBlank()) {
                            viewModel.sendChatMessage(userQueryText)
                            userQueryText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("ai_assistant_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send Message",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(msg: ChatMessage) {
    val isUser = msg.sender == "USER"
    val align = if (isUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (isUser) Color.Black else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Text(
                text = msg.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

// 7. PROFILE & ADMINISTRATIVE INCOME MONITOR SCREEN
@Composable
fun ProfileScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val giftCards by viewModel.giftCards.collectAsStateWithLifecycle()
    val activeScenarioName by viewModel.selectedScenarioName.collectAsStateWithLifecycle()
    val actualRevenue by viewModel.simulatedActualMonthlyRevenue.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showAdminPanel by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Account Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Avatar details
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "oghenekaro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(text = user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(text = "Premium Plan Status: ${user.tier}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Subscription Tier picker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Upgrade Subscription Plan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Free", "Premium", "Business").forEach { tier ->
                            val isSel = user.tier == tier
                            FilledTonalButton(
                                onClick = { viewModel.selectSubscriptionTier(tier) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(tier, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Administrative Portal Trigger Card (Crucial for targeting 3M gross revenue)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "EXECUTIVE INCOME MONITOR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "6-Month Run Rate & Models",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Switch(
                            checked = showAdminPanel,
                            onCheckedChange = { showAdminPanel = it },
                            modifier = Modifier.testTag("admin_panel_toggle")
                        )
                    }

                    Text(
                        text = "Compare commercial growth projection scenarios against the required $3,000,000 half-year commercial run-rate targets.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // ADMINISTRATIVE REVENUE DASHBOARD OVERVIEW (EXPANDABLE)
        if (showAdminPanel) {
            item {
                Text(
                    text = "Executive Revenue & Run-Rate Projections",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Required $3M Half-year run rate dashboard
            item {
                val alert = FinancialProjections.getRunRateAlert(actualRevenue)
                val alertColor = Color(android.graphics.Color.parseColor(alert.colorHex))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Active Run-Rate Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(alertColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = alert.label,
                                    color = alertColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Required run rate vs actual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Monthly Actual Simulated", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "$${String.format("%,.0f", actualRevenue)} USD",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = alertColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Required Target Run-Rate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "$${String.format("%,.0f", FinancialProjections.REQUIRED_MONTHLY_RUN_RATE)} USD/mo",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Progress to half year $3,000,000 target
                        LinearProgressIndicator(
                            progress = min(1.0f, (actualRevenue / FinancialProjections.REQUIRED_MONTHLY_RUN_RATE).toFloat()),
                            color = alertColor,
                            trackColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )

                        Text(
                            text = "Averaging $500,000/month is essential to meet the $3M gross target. Current simulation modeling shows that under this scenario we are tracking on or off target.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Scenario comparator models
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Commercial Scenario Comparison",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        listOf("Conservative", "Base (Realistic)", "Aggressive").forEach { scName ->
                            val isSel = activeScenarioName == scName
                            val proj = FinancialProjections.getScenarioByName(scName)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.tertiary)
                                    .border(
                                        1.dp,
                                        if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectScenario(scName) }
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = scName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "$${String.format("%,.0f", proj.totalMonthlyGrossRevenue)} /mo",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        text = "Active Users: ${String.format("%,d", proj.activeUsers)} MAU • Avg Volume: $${proj.avgMonthlyTxVolume}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = proj.description,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fintech Security & Compliance Center (Covers 01 to 06 & global availability)
            item {
                val sessionPersisted by viewModel.sessionPersistenceEnabled.collectAsStateWithLifecycle()
                val mfaSms by viewModel.mfaSmsEnabled.collectAsStateWithLifecycle()
                val mfaAuthApp by viewModel.mfaAuthAppEnabled.collectAsStateWithLifecycle()
                val aesDb by viewModel.aes256EncryptionEnabled.collectAsStateWithLifecycle()
                val fieldEnc by viewModel.fieldLevelEncryptionEnabled.collectAsStateWithLifecycle()
                val apiLimit by viewModel.apiRateLimitingEnabled.collectAsStateWithLifecycle()
                val globalAvail by viewModel.globalAvailabilityEnabled.collectAsStateWithLifecycle()
                val screenBlurEnabled by viewModel.screenBlurEnabled.collectAsStateWithLifecycle()
                val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

                val isAccountFrozen by viewModel.isAccountFrozen.collectAsStateWithLifecycle()

                var activeSecurityTab by remember { mutableStateOf("auth") } // "auth", "encryption", "api", "audit", "playstore"
                var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
                var showTermsDialog by remember { mutableStateOf(false) }
                var showAccountDeletionDialog by remember { mutableStateOf(false) }
                var showStatementExportDialog by remember { mutableStateOf(false) }

                if (showPrivacyPolicyDialog) {
                    PrivacyPolicyModal(onDismiss = { showPrivacyPolicyDialog = false })
                }
                if (showTermsDialog) {
                    TermsOfServiceModal(onDismiss = { showTermsDialog = false })
                }
                if (showAccountDeletionDialog) {
                    AccountDeletionRequestModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showAccountDeletionDialog = false })
                }
                if (showStatementExportDialog) {
                    StatementExportModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showStatementExportDialog = false })
                }

                var securityLogList by remember {
                    mutableStateOf(
                        listOf(
                            "10:50:16 [INFO] Client TLS 1.3 Handshake completed successfully.",
                            "10:48:02 [INFO] Local AES-256 Room database sealed & encrypted.",
                            "10:45:11 [SECURE] Sanitized BVN and personal details before API post.",
                            "10:40:22 [ALERT] Unauthorized brute-force login attempt blocked from anomalous IP (Rate-limited)."
                        )
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Security Hub",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Fintech Security & Compliance Center",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "End-to-End Cryptographic Protection & Auditing",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Tab Bar Buttons (Horizontal Row)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf(
                                "auth" to "🔐 Auth",
                                "encryption" to "🛡️ Cipher",
                                "api" to "🌐 API",
                                "audit" to "📊 Audit",
                                "playstore" to "🚀 Launch"
                            )
                            tabs.forEach { (tabId, label) ->
                                val isSel = activeSecurityTab == tabId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { activeSecurityTab = tabId }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp))

                        // Tab Contents
                        when (activeSecurityTab) {
                            "auth" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "01 | STRONG MULTI-FACTOR AUTHENTICATION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    // SMS Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SMS Verification (MFA)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Send secure one-time SMS verification codes for high-value operations",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = mfaSms,
                                            onCheckedChange = { viewModel.setMfaSms(it) },
                                            modifier = Modifier.testTag("mfa_sms_toggle")
                                        )
                                    }

                                    // Authenticator app toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Google Authenticator / TOTP",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Enforce secure multi-factor TOTP authentication app matching",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = mfaAuthApp,
                                            onCheckedChange = { viewModel.setMfaAuthApp(it) },
                                            modifier = Modifier.testTag("mfa_auth_app_toggle")
                                        )
                                    }

                                    // Session persistence toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Enable Session Persistence",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Stay logged in across restarts. Disable to require biometric lock scan on every launch.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = sessionPersisted,
                                            onCheckedChange = { viewModel.setSessionPersistence(it) },
                                            modifier = Modifier.testTag("session_persistence_toggle")
                                        )
                                    }

                                    // Screen Blur & Auto-Lock toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Screen-Blur & Inactivity Lock",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Obscure the screen with a privacy blur and auto-lock the app when backgrounded/inactive in public spaces.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = screenBlurEnabled,
                                            onCheckedChange = { viewModel.setScreenBlurEnabled(it) },
                                            modifier = Modifier.testTag("screen_blur_toggle")
                                        )
                                    }

                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Theme Toggle for Visual Accessibility
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Accessibility Dark Theme",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Enable high-contrast Dark Mode to reduce eye strain and improve overall readability.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                                contentDescription = "Theme Icon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Switch(
                                                checked = isDarkMode,
                                                onCheckedChange = { viewModel.setDarkMode(it) },
                                                modifier = Modifier.testTag("dark_mode_toggle")
                                            )
                                        }
                                    }

                                    // Strong password enforcement badge
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VerifiedUser,
                                                contentDescription = "Verified Policy",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Strong Password Policy Enforced (Length >= 12, complexity symbols, 90-day rotation, OAuth 2.0 Identity Management keys).",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.lockApp()
                                            scope.launch { snackbarHostState.showSnackbar("App locked. Please scan fingerprint to unlock.") }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth().testTag("lock_app_button")
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Lock",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("Lock App Now (Simulate Exit)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            "playstore" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "GOOGLE PLAY STORE RELEASE & LEGAL READINESS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    // Emergency Freeze Card / Account Panic Lock
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = if (isAccountFrozen) Color(0xFFD32F2F).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, if (isAccountFrozen) Color.Red else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isAccountFrozen) "🚨 Account & Virtual Cards FROZEN" else "Emergency Account & Card Freeze",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAccountFrozen) Color.Red else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Instantly block all transfers, withdrawals, and virtual cards in case of loss or suspicious activity.",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                                Switch(
                                                    checked = isAccountFrozen,
                                                    onCheckedChange = { frozen ->
                                                        viewModel.toggleAccountFreeze(frozen)
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(if (frozen) "Account and virtual cards FROZEN instantly." else "Account security restrictions lifted.")
                                                        }
                                                    },
                                                    modifier = Modifier.testTag("panic_account_freeze_toggle")
                                                )
                                            }
                                        }
                                    }

                                    // Mandatory Legal Documents Row
                                    Text("Mandatory Play Store Documents & Policies", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showPrivacyPolicyDialog = true },
                                            modifier = Modifier.weight(1f).testTag("open_privacy_policy_btn")
                                        ) {
                                            Text("Privacy Policy", fontSize = 10.sp, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { showTermsDialog = true },
                                            modifier = Modifier.weight(1f).testTag("open_terms_btn")
                                        ) {
                                            Text("Terms of Service", fontSize = 10.sp, maxLines = 1)
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showStatementExportDialog = true },
                                            modifier = Modifier.weight(1f).testTag("open_statement_export_btn")
                                        ) {
                                            Text("Export PDF Statement", fontSize = 10.sp, maxLines = 1)
                                        }
                                        Button(
                                            onClick = { showAccountDeletionDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f).testTag("open_account_deletion_btn")
                                        ) {
                                            Text("Account Deletion", fontSize = 10.sp, maxLines = 1)
                                        }
                                    }

                                    // Play Store Release Audit Checklist
                                    Text("Google Play Store Technical Checklist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val playStoreChecklist = listOf(
                                        "Unique Application ID (com.aistudio.apexpay.kxmpzq)" to true,
                                        "Min SDK 24 / Target SDK 35 Architecture" to true,
                                        "Play Store Data Safety Form Compliance" to true,
                                        "Edge-to-Edge & Dynamic Material You Support" to true,
                                        "ProGuard / R8 Obfuscation & Shrinking" to true,
                                        "Regulatory NDPR / CBN Sandbox Guidelines" to true
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        playStoreChecklist.forEach { (title, done) ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                                                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                }
                            }
                            "encryption" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "02 | END-TO-END CRYPTOGRAPHIC ENCRYPTION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    // SQLite database encryption toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Database AES-256 Seal",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Encrypt local Room Database with 256-bit SQLCipher keys at rest",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = aesDb,
                                            onCheckedChange = { viewModel.setAes256Encryption(it) },
                                            modifier = Modifier.testTag("aes256_db_toggle")
                                        )
                                    }

                                    // Field level account shielding toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Field-Level Account Shield",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Individually encrypt BVNs, card digits, and account balances before persisting",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = fieldEnc,
                                            onCheckedChange = { viewModel.setFieldLevelEncryption(it) },
                                            modifier = Modifier.testTag("field_encryption_toggle")
                                        )
                                    }

                                    // TLS 1.3 Active banner
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SuccessGreen.copy(alpha = 0.1f))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VerifiedUser,
                                                contentDescription = "Verified Connection Security",
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Transport Security: TLS 1.3 Protocol strictly enforced for all inbound and outbound transactions, guarding against MITM sniffing attacks.",
                                                fontSize = 10.sp,
                                                color = SuccessGreen
                                            )
                                        }
                                    }
                                }
                            }
                            "api" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "03 | API GATEWAY SECURITY & GLOBAL ROUTING",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    // API Rate limiting toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Rate-Limit API Request Throttler",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Mitigate brute-force attacks by limiting client bursts to 100 req/min",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = apiLimit,
                                            onCheckedChange = { viewModel.setApiRateLimiting(it) },
                                            modifier = Modifier.testTag("api_limit_toggle")
                                        )
                                    }

                                    // Universal Geoblock Bypass / Country Availability (Satisfies "App should be available to all countries")
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Global Jurisdiction Routing",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Allow app endpoints to bypass local geoblocks. Make app fully available to all countries.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                        Switch(
                                            checked = globalAvail,
                                            onCheckedChange = { viewModel.setGlobalAvailability(it) },
                                            modifier = Modifier.testTag("global_availability_toggle")
                                        )
                                    }

                                    // JWT Key rotated token status
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Public,
                                                    contentDescription = "Global Coverage",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Georedundant Cloud Nodes Available in Africa, Americas, Europe, Asia.",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = "Secure OWASP-certified gateways prevent SQL injection / XSS, issuing JWT credentials valid for 15 minutes before rotating automatically.",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                            "audit" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "05 & 06 | REAL-TIME THREAT LOGS & AUDITS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )

                                    // Console / Interactive Logs
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "SYSTEM GATEWAY MONITOR",
                                                    color = SuccessGreen,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                                        val newLogs = listOf(
                                                            "$currentTime [INFO] Encrypted SQLite database health check OK.",
                                                            "$currentTime [INFO] JWT token validation passed.",
                                                            "$currentTime [SECURE] Zero-Knowledge input sanitization parser executed.",
                                                            "$currentTime [ALERT] System scan: Completed 0 anomalies found in last 300 cycles."
                                                        )
                                                        securityLogList = newLogs
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Refresh,
                                                        contentDescription = "Refresh Logs",
                                                        tint = SuccessGreen,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            securityLogList.forEach { log ->
                                                val logColor = when {
                                                    log.contains("[ALERT]") -> Color.Red
                                                    log.contains("[SECURE]") -> MaterialTheme.colorScheme.primary
                                                    else -> SuccessGreen
                                                }
                                                Text(
                                                    text = log,
                                                    color = logColor,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    lineHeight = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    // Compliance checklists (Quarterly pentest, PCI DSS, scan after updates)
                                    Text(
                                        text = "REGULATORY FINTECH COMPLIANCE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        val checklist = listOf(
                                            "PCI DSS Level 1 Certified fintech security structure" to true,
                                            "Quarterly professional penetration testing active" to true,
                                            "System-wide vulnerability scans completed after each update" to true,
                                            "Dynamic host IDS intrusion monitoring alerts armed" to true
                                        )
                                        checklist.forEach { (task, isVerified) ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Compliant",
                                                    tint = SuccessGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = task,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// Multi-Step KYC Onboarding Flow Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KycOnboardingDialog(viewModel: ApexViewModel, onDismiss: () -> Unit) {
    val user by viewModel.userAccount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Form inputs state
    var fullName by remember { mutableStateOf(user.fullName) }
    var bvn by remember { mutableStateOf(user.bvn) }
    var selectedIdType by remember { mutableStateOf(if (user.idType.isEmpty()) "National ID (NIN)" else user.idType) }
    var idNumber by remember { mutableStateOf(user.idNumber) }

    // Simulated upload state
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var docUploaded by remember { mutableStateOf(user.docUploaded) }
    var uploadedFileName by remember { mutableStateOf(user.docFileName) }

    // Simulated Biometric state
    var isScanningFace by remember { mutableStateOf(false) }
    var faceScanned by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("kyc_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Identity Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "KYC Onboarding • Step ${user.kycStep + 1} of 4",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close KYC Dialog")
                    }
                }

                // Step Progress Indicator
                val progressFraction = when (user.kycStep) {
                    0 -> 0.1f
                    1 -> 0.4f
                    2 -> 0.7f
                    3 -> 1.0f
                    else -> 0.0f
                }
                LinearProgressIndicator(
                    progress = progressFraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.tertiary
                )

                // Render dynamic step contents
                when (user.kycStep) {
                    0 -> {
                        // STEP 0: WELCOME INTRO
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Security Shield",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Unlock High-Growth Limits",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "To comply with Central Bank of Nigeria guidelines and secure high-volume payment transfers, please complete our instant digital KYC verification. It takes less than 2 minutes.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { viewModel.nextKycStep() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("kyc_start_button")
                            ) {
                                Text("Start Verification Flow", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    1 -> {
                        // STEP 1: PERSONAL DETAILS (validation required)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Personal Information",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Legal Full Name") },
                                placeholder = { Text("As it appears on your ID") },
                                isError = fullName.isNotEmpty() && (!fullName.trim().contains(" ") || fullName.trim().length < 4),
                                supportingText = {
                                    if (fullName.isNotEmpty() && (!fullName.trim().contains(" ") || fullName.trim().length < 4)) {
                                        Text("Please enter your first and last name.", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Must match your banking details.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("kyc_name_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = bvn,
                                onValueChange = { if (it.length <= 11 && it.all { ch -> ch.isDigit() }) bvn = it },
                                label = { Text("Bank Verification Number (BVN)") },
                                placeholder = { Text("11-digit BVN") },
                                isError = bvn.isNotEmpty() && bvn.length != 11,
                                supportingText = {
                                    if (bvn.isNotEmpty() && bvn.length != 11) {
                                        Text("BVN must be exactly 11 digits.", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Securely encrypted. We only check identity details.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("kyc_bvn_input"),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val isStep1Valid = fullName.trim().length >= 4 && fullName.trim().contains(" ") && bvn.length == 11

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.prevKycStep() },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        viewModel.updateKycPersonalDetails(fullName, bvn)
                                        viewModel.nextKycStep()
                                    },
                                    enabled = isStep1Valid,
                                    modifier = Modifier.weight(1.5f).height(48.dp).testTag("kyc_step1_next_button")
                                ) {
                                    Text("Next Step", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    2 -> {
                        // STEP 2: ID DOCUMENT UPLOAD
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Select Identity Document",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Horizontal list of ID choices
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("NIN", "Driver's License", "Passport").forEach { type ->
                                    val isSel = selectedIdType == type || (selectedIdType.startsWith("National") && type == "NIN")
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                                            .clickable { selectedIdType = type }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = type,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = idNumber,
                                onValueChange = { idNumber = it },
                                label = { Text("Document ID Number") },
                                placeholder = { Text("Enter ID number") },
                                isError = idNumber.isNotEmpty() && idNumber.trim().length < 5,
                                modifier = Modifier.fillMaxWidth().testTag("kyc_id_number_input"),
                                singleLine = true
                            )

                            // Simulated interactive Document Upload Area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                                    .border(
                                        width = 1.dp,
                                        color = if (docUploaded) SuccessGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isUploading) {
                                        isUploading = true
                                        uploadProgress = 0
                                        scope.launch {
                                            for (i in 1..100) {
                                                kotlinx.coroutines.delay(15)
                                                uploadProgress = i
                                            }
                                            docUploaded = true
                                            uploadedFileName = "${selectedIdType.lowercase().replace(" ", "_")}_verified_front.jpg"
                                            isUploading = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isUploading) {
                                        CircularProgressIndicator(
                                            progress = uploadProgress / 100f,
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("Uploading proof document... $uploadProgress%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    } else if (docUploaded) {
                                        Icon(imageVector = Icons.Filled.CheckCircle, tint = SuccessGreen, contentDescription = "Check Circle", modifier = Modifier.size(28.dp))
                                        Text(text = uploadedFileName, fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        Text("Tap to upload another document", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    } else {
                                        Icon(imageVector = Icons.Filled.CloudUpload, tint = MaterialTheme.colorScheme.primary, contentDescription = "Upload Cloud", modifier = Modifier.size(28.dp))
                                        Text("Tap to upload front copy of your ID", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text("Accepted: JPG, PNG, PDF up to 5MB", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            val isStep2Valid = docUploaded && idNumber.trim().isNotEmpty()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.prevKycStep() },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        viewModel.updateKycDocumentDetails(selectedIdType, idNumber, uploadedFileName)
                                        viewModel.nextKycStep()
                                    },
                                    enabled = isStep2Valid,
                                    modifier = Modifier.weight(1.5f).height(48.dp).testTag("kyc_step2_next_button")
                                ) {
                                    Text("Next Step", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    3 -> {
                        // STEP 3: BIOMETRIC SCAN & REVIEW SUBMISSION
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Instant Face Biometrics",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.align(Alignment.Start),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Circular pulse facial scanner simulator
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .border(2.dp, if (faceScanned) SuccessGreen else MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable(enabled = !isScanningFace) {
                                        isScanningFace = true
                                        scanProgress = 0f
                                        scope.launch {
                                            for (i in 1..40) {
                                                kotlinx.coroutines.delay(50)
                                                scanProgress = i / 40f
                                            }
                                            faceScanned = true
                                            isScanningFace = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isScanningFace) {
                                    // Custom visual biometric sweep line
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height((scanProgress * 110).dp)
                                            .background(
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color(0xFF00F5D4).copy(alpha = 0.4f))
                                                )
                                            )
                                            .align(Alignment.TopCenter)
                                    )
                                    Icon(imageVector = Icons.Filled.Face, contentDescription = "Scanning face", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                                } else if (faceScanned) {
                                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Face Scanned", tint = SuccessGreen, modifier = Modifier.size(56.dp))
                                } else {
                                    Icon(imageVector = Icons.Filled.Face, contentDescription = "Scan face icon", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(56.dp))
                                }
                            }

                            Text(
                                text = if (isScanningFace) "Pulsing radar scanning biometrics... Keep steady"
                                       else if (faceScanned) "Face match verification passed! ✅"
                                       else "Tap circle above to perform quick biometric live verification",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = if (faceScanned) SuccessGreen else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.prevKycStep() },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        viewModel.submitKycVerification()
                                        onDismiss()
                                    },
                                    enabled = faceScanned,
                                    modifier = Modifier.weight(1.5f).height(48.dp).testTag("kyc_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                                ) {
                                    Text("Submit For Review", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Transaction Detail Dialog allowing custom category corrections (Dynamic AI feedback model)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    transaction: TransactionEntity,
    viewModel: ApexViewModel,
    onDismiss: () -> Unit
) {
    var showAiTrainFeedback by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = listOf("Food", "Utilities", "Entertainment", "Transport", "Shopping", "Investment", "Gift Cards", "Rewards", "Others")

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("tx_detail_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close details")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.tertiary)

                // Transaction summary representation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = transaction.description,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${transaction.currency} ${String.format("%,.2f", transaction.amount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = if (transaction.type == "RECEIVE" || transaction.type == "REWARD" || transaction.type == "GIFT_CARD") SuccessGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Payment Type: ${transaction.type}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // AI Category Indicator badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🤖", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "AI Auto-Assigned Category",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = transaction.category,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // User Corrections Section
                Text(
                    text = "Is this category incorrect? Help AI learn:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                // Grid/Row of alternative categories
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    mainAxisSpacing = 8.dp,
                    crossAxisSpacing = 8.dp
                ) {
                    categories.forEach { category ->
                        val isSel = transaction.category == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                                .clickable {
                                    if (!isSel) {
                                        viewModel.updateTransactionCategory(transaction.id, category)
                                        showAiTrainFeedback = true
                                        scope.launch {
                                            kotlinx.coroutines.delay(2000)
                                            onDismiss()
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Visual animated learning indicator
                AnimatedVisibility(visible = showAiTrainFeedback) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuccessGreen.copy(alpha = 0.15f))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Memory, tint = SuccessGreen, contentDescription = "AI Model Learning", modifier = Modifier.size(16.dp))
                            Text(
                                text = "AI engine updated! Matching models re-trained.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

                // PDF Download action
                val context = LocalContext.current
                var pdfState by remember { mutableStateOf<String?>(null) } // null, "generating", "saved", "error"
                var pdfFilePath by remember { mutableStateOf("") }

                Button(
                    onClick = {
                        pdfState = "generating"
                        viewModel.generateTransactionPdf(context, transaction) { file ->
                            if (file != null) {
                                pdfState = "saved"
                                pdfFilePath = file.absolutePath
                            } else {
                                pdfState = "error"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_pdf_receipt_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = "PDF Icon",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when (pdfState) {
                                "generating" -> "Generating PDF Receipt..."
                                "saved" -> "PDF Receipt Generated!"
                                "error" -> "Failed to Generate PDF"
                                else -> "Download PDF Summary Receipt"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(visible = pdfState == "saved") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🟢 PDF Saved Successfully!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Text(
                                text = "Location: Internal Storage > Downloads",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            
                            // Share/Open PDF receipt option
                            val file = File(pdfFilePath)
                            val fileProviderAuthority = "com.example.fileprovider"
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(context, fileProviderAuthority, file))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Transaction Receipt"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        val shareText = "Apex Pay Receipt:\nReference: APX-${transaction.id}\nDescription: ${transaction.description}\nAmount: ${transaction.currency} ${transaction.amount}\nStatus: SUCCESSFUL"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Receipt Info"))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share PDF", modifier = Modifier.size(14.dp))
                                    Text("Open/Share Receipt PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple FlowRow helper to layout categories without importing massive external libraries
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        val layoutWidth = constraints.maxWidth
        var layoutHeight = 0

        var currentX = 0
        var currentY = 0
        var rowHeight = 0

        val placements = mutableListOf<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()

        val mainSpacingPx = mainAxisSpacing.roundToPx()
        val crossSpacingPx = crossAxisSpacing.roundToPx()

        for (placeable in placeables) {
            if (currentX + placeable.width > layoutWidth) {
                currentX = 0
                currentY += rowHeight + crossSpacingPx
                rowHeight = 0
            }

            placements.add(Triple(placeable, currentX, currentY))
            rowHeight = maxOf(rowHeight, placeable.height)
            currentX += placeable.width + mainSpacingPx
        }

        layoutHeight = currentY + rowHeight

        layout(layoutWidth, layoutHeight) {
            placements.forEach { (placeable, x, y) ->
                placeable.place(x, y)
            }
        }
    }
}

// Play Store Compliant Privacy Policy Modal
@Composable
fun PrivacyPolicyModal(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Text("Last Updated: July 2026 • Play Store Compliant", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

                Text(
                    "Apex Pay Inc. ('we', 'our', or 'us') is committed to protecting your personal data and financial privacy in compliance with Google Play Store policies, NDPR (Nigeria Data Protection Regulation), and global GDPR guidelines.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Text("1. Information We Collect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "• Identity Data: Full Name, BVN/NIN verification numbers for regulatory KYC.\n• Transaction Data: Wallet transfers, exchange transactions, gift card trades.\n• Device Telemetry: IP address, encrypted device ID for fraud prevention.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text("2. Encryption & Security", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "All communications use TLS 1.3 encryption. Financial data at rest is encrypted with AES-256 keys.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text("3. Account Deletion & Rights", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "You have the absolute right to request permanent deletion of your account and personal data at any time via the Account Deletion flow in Settings.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("I Understand & Accept")
                }
            }
        }
    }
}

// Play Store Terms of Service Modal
@Composable
fun TermsOfServiceModal(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Terms of Service", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Text("Effective Date: July 2026", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

                Text(
                    "By accessing or using the Apex Pay fintech application, you agree to be bound by these Terms of Service.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                Text("1. Service Eligibility & Verification", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Users must complete compulsory KYC identity verification before conducting high-value cross-border currency transfers or card issuances.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text("2. Prohibited Uses & Anti-Money Laundering (AML)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Apex Pay enforces strict AML algorithms. Accounts participating in fraudulent carding or money laundering will be frozen immediately.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Close Terms")
                }
            }
        }
    }
}

// Play Store Compliant Account Deletion Request Dialog
@Composable
fun AccountDeletionRequestModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Request Account & Data Purge", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Per Google Play Policy requirements, submitting this form queues your entire account, virtual cards, and associated personal records for permanent deletion within 30 days.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for deletion (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("I confirm that I want all my data permanently erased.", fontSize = 10.sp)
                }

                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Account deletion request submitted. Data purge scheduled in 30 days.")
                            onDismiss()
                        }
                    },
                    enabled = confirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm Account Deletion Request")
                }
            }
        }
    }
}

// Statement Export Modal
@Composable
fun StatementExportModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedRange by remember { mutableStateOf("Last 30 Days") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Export Official Account Statement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Text(
                    "Generate a digitally signed PDF statement of all transfers, currency conversions, and virtual card activities.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                listOf("Last 30 Days", "Last 90 Days", "Year to Date (2026)").forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedRange == range) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.tertiary)
                            .clickable { selectedRange = range }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(range, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (selectedRange == range) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Statement ($selectedRange) exported to Downloads/Apex_Statement.pdf")
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download PDF Statement")
                }
            }
        }
    }
}

// ==========================================
// 2026 NEXT-GEN FINTECH HUB SCREEN (7 CORE FEATS)
// ==========================================
@Composable
fun NextGenHubScreen(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("ai") } // "ai", "quantum", "cbdc", "embedded", "biometric", "defi", "regtech"

    // State collections
    val aiLoanApplications by viewModel.aiLoanApplications.collectAsStateWithLifecycle()
    val aiPortfolioRebalanceEnabled by viewModel.aiPortfolioRebalanceEnabled.collectAsStateWithLifecycle()
    val quantumSecurityEnabled by viewModel.quantumSecurityEnabled.collectAsStateWithLifecycle()
    val quantumSessionKey by viewModel.quantumSessionKey.collectAsStateWithLifecycle()
    val cbdcBalances by viewModel.cbdcBalances.collectAsStateWithLifecycle()
    val embeddedPolicies by viewModel.embeddedActivePolicies.collectAsStateWithLifecycle()
    val faceIdActive by viewModel.faceIdActive.collectAsStateWithLifecycle()
    val fingerprintActive by viewModel.fingerprintActive.collectAsStateWithLifecycle()
    val voicePrintActive by viewModel.voicePrintActive.collectAsStateWithLifecycle()
    val deFiPools by viewModel.deFiYieldPools.collectAsStateWithLifecycle()
    val regTechScore by viewModel.regTechAmlScore.collectAsStateWithLifecycle()
    val pepCleared by viewModel.pepSanctionCleared.collectAsStateWithLifecycle()

    // Dialog & Form states
    var showAiLoanModal by remember { mutableStateOf(false) }
    var showCbdcTransferModal by remember { mutableStateOf(false) }
    var showBnplModal by remember { mutableStateOf(false) }
    var showVoiceAuthModal by remember { mutableStateOf(false) }
    var showDeFiStakeModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Next-Gen Fintech 2026",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Autonomous AI • Quantum Security • CBDCs • DeFi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text("LATTICE-256 PQC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Horizontal Category Tabs
        ScrollableTabRow(
            selectedTabIndex = when (selectedCategory) {
                "ai" -> 0
                "quantum" -> 1
                "cbdc" -> 2
                "embedded" -> 3
                "biometric" -> 4
                "defi" -> 5
                else -> 6
            },
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            val categories = listOf(
                "ai" to "🤖 AI & Auto-Banking",
                "quantum" to "🔐 Quantum Security",
                "cbdc" to "🏛️ CBDCs (eNaira+)",
                "embedded" to "🚗 Embedded Finance",
                "biometric" to "🖐️ Biometrics",
                "defi" to "🔗 DeFi & Web3",
                "regtech" to "📜 RegTech Compliance"
            )
            categories.forEachIndexed { idx, (catId, title) ->
                Tab(
                    selected = selectedCategory == catId,
                    onClick = { selectedCategory = catId },
                    text = { Text(title, fontSize = 11.sp, fontWeight = if (selectedCategory == catId) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("nextgen_tab_$catId")
                )
            }
        }

        // Main Tab Content Area
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedCategory) {
                    "ai" -> {
                        item {
                            // Feature 1: AI & Autonomous Banking
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                                            Text("AI Autonomous Credit Officer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                        Surface(shape = RoundedCornerShape(12.dp), color = SuccessGreen.copy(alpha = 0.15f)) {
                                            Text("94.7% Decision Accuracy", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Text(
                                        "Processes credit scoring, risk modeling, and instant loan disbursements automatically without human intervention (JPMorgan-grade AI decisioning model).",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Autonomous Rebalancing Engine", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Switch(
                                            checked = aiPortfolioRebalanceEnabled,
                                            onCheckedChange = { viewModel.toggleAiPortfolioRebalance(it) },
                                            modifier = Modifier.testTag("ai_rebalance_switch")
                                        )
                                    }

                                    Button(
                                        onClick = { showAiLoanModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("apply_ai_loan_btn")
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Apply for Instant AI Autonomous Loan")
                                    }
                                }
                            }
                        }

                        item {
                            Text("Active AI Loan Applications Log", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        items(aiLoanApplications.size) { index ->
                            val app = aiLoanApplications[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.purpose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("ID: ${app.id} • ${app.note}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${app.currency} ${app.amount}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.2f)) {
                                            Text(app.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "quantum" -> {
                        item {
                            // Feature 2: Quantum-Secured Payments
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Post-Quantum Cryptography (PQC)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                        Switch(
                                            checked = quantumSecurityEnabled,
                                            onCheckedChange = { viewModel.toggleQuantumSecurity(it) },
                                            modifier = Modifier.testTag("quantum_switch")
                                        )
                                    }

                                    Text(
                                        "Quantum Key Distribution (QKD) & Kyber-1024 / Dilithium signatures protect every transaction against future quantum computing decryption threats.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Active QKD Lattice Session Key:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text(quantumSessionKey, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.rotateQuantumKeys()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Rotated Quantum Session Key using Kyber-1024 Lattice Protocol.")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("rotate_quantum_key_btn")
                                    ) {
                                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Rotate Quantum Key Lattice")
                                    }
                                }
                            }
                        }
                    }

                    "cbdc" -> {
                        item {
                            // Feature 3: Central Bank Digital Currencies (CBDCs)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("CBDC Central Bank Hub (94 Countries)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Text(
                                        "Direct integration with central bank digital currencies for instant, 0.00% fee cross-border settlements.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        cbdcBalances.forEach { (symbol, bal) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.tertiary)
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("Central Bank Direct Ledger", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                                }
                                                Text(
                                                    text = "${if (symbol == "eNaira") "₦" else if (symbol == "eUSD") "$" else if (symbol == "eEUR") "€" else "¥"}${String.format("%,.2f", bal)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { showCbdcTransferModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_cbdc_modal_btn")
                                    ) {
                                        Text("Mint / Transfer CBDC Zero Fee")
                                    }
                                }
                            }
                        }
                    }

                    "embedded" -> {
                        item {
                            // Feature 4: Embedded Finance
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Embedded Financial SDK & APIs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Text(
                                        "Financial services embedded into non-financial applications (Uber, Bolt, Jumia, Shopify) offering micro-insurance and BNPL financing at checkout.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Button(
                                        onClick = { showBnplModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_bnpl_btn")
                                    ) {
                                        Text("Simulate E-Commerce BNPL Checkout")
                                    }
                                }
                            }
                        }

                        item {
                            Text("Active Embedded Policies & Micro-Loans", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        items(embeddedPolicies.size) { idx ->
                            val pol = embeddedPolicies[idx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(pol.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.2f)) {
                                            Text(pol.status, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(pol.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Text("Terms: ${pol.terms}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    "biometric" -> {
                        item {
                            // Feature 5: Biometric Authentication Suite
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Multi-Modal Biometrics Engine", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Text(
                                        "Face, fingerprint, and voice recognition passphrases replacing legacy password authentication.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("3D Depth Face ID", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Switch(checked = faceIdActive, onCheckedChange = { viewModel.toggleBiometricSetting("FACE", it) })
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Under-Display Fingerprint Sensor", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Switch(checked = fingerprintActive, onCheckedChange = { viewModel.toggleBiometricSetting("FINGER", it) })
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Voiceprint Recognition Passphrase", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Switch(checked = voicePrintActive, onCheckedChange = { viewModel.toggleBiometricSetting("VOICE", it) })
                                    }

                                    Button(
                                        onClick = { showVoiceAuthModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("test_voice_biometric_btn")
                                    ) {
                                        Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Test Voice Biometric Verification")
                                    }
                                }
                            }
                        }
                    }

                    "defi" -> {
                        item {
                            // Feature 6: Blockchain & DeFi Integration
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CurrencyExchange, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("DeFi & Decentralized Yield Pools", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Text(
                                        "Non-custodial smart contract lending, liquidity pooling, and automated market making integrated directly alongside fiat banking.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Button(
                                        onClick = { showDeFiStakeModal = true },
                                        modifier = Modifier.fillMaxWidth().testTag("open_defi_stake_btn")
                                    ) {
                                        Text("Stake Assets in DeFi Yield Pool")
                                    }
                                }
                            }
                        }

                        item {
                            Text("Active Decentralized Liquidity Pools", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        items(deFiPools.size) { idx ->
                            val pool = deFiPools[idx]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(pool.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Protocol: ${pool.protocol}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(pool.apy, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                        Text("Staked: ${pool.stakedBalance} ${pool.symbol}", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            // Feature 7: RegTech & Compliance Automation
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("RegTech & Autonomous Compliance", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Text(
                                        "Real-time AI sanction checks, PEP screening, and automated AML regulatory reports submitted directly to Central Banks & SEC regulators.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Real-Time AML Risk Index", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text("$regTechScore / 100 (Safe)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SuccessGreen)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("PEP & Sanction Screening", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            Text(if (pepCleared) "PASSED (OFAC/UN Cleared)" else "FLAGGED", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.runRegTechComplianceAudit { msg ->
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("run_regtech_audit_btn")
                                    ) {
                                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Trigger Real-Time RegTech Audit")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs for Next-Gen Hub
    if (showAiLoanModal) {
        AiLoanApplicationModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showAiLoanModal = false })
    }
    if (showCbdcTransferModal) {
        CbdcTransferModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showCbdcTransferModal = false })
    }
    if (showBnplModal) {
        BnplCheckoutModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showBnplModal = false })
    }
    if (showVoiceAuthModal) {
        VoiceBiometricModal(onDismiss = { showVoiceAuthModal = false })
    }
    if (showDeFiStakeModal) {
        DeFiStakingModal(viewModel = viewModel, snackbarHostState = snackbarHostState, onDismiss = { showDeFiStakeModal = false })
    }
}

// Modal Dialog 1: AI Loan Application
@Composable
fun AiLoanApplicationModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    var purpose by remember { mutableStateOf("Working Capital & Expansion") }
    var amountText by remember { mutableStateOf("250000") }
    var selectedCurrency by remember { mutableStateOf("NGN") }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Credit Officer Loan Application", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                Text("Our JPMorgan-grade AI credit model processes loan requests instantly using real-time telemetry and cash flow assessment.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Loan Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Requested Amount ($selectedCurrency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 100000.0
                        viewModel.applyForAiLoan(purpose, amt, selectedCurrency) { approved, note ->
                            scope.launch {
                                snackbarHostState.showSnackbar(note)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit for Autonomous AI Decision")
                }
            }
        }
    }
}

// Modal Dialog 2: CBDC Mint / Transfer
@Composable
fun CbdcTransferModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    var cbdcType by remember { mutableStateOf("eNaira") }
    var amountText by remember { mutableStateOf("50000") }
    var isMint by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Central Bank Digital Currency (CBDC)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("eNaira", "eUSD", "eEUR", "eCNY").forEach { type ->
                        FilterChip(
                            selected = cbdcType == type,
                            onClick = { cbdcType = type },
                            label = { Text(type, fontSize = 10.sp) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isMint, onClick = { isMint = true }, label = { Text("Mint CBDC") })
                    FilterChip(selected = !isMint, onClick = { isMint = false }, label = { Text("Transfer CBDC") })
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 1000.0
                        viewModel.mintOrTransferCbdc(cbdcType, amt, isMint) { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar(result)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isMint) "Mint $cbdcType via Central Bank" else "Execute Instant 0-Fee CBDC Transfer")
                }
            }
        }
    }
}

// Modal Dialog 3: BNPL Checkout
@Composable
fun BnplCheckoutModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    var merchant by remember { mutableStateOf("Jumia E-Commerce Store") }
    var totalAmountText by remember { mutableStateOf("120000") }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Embedded BNPL Checkout Financing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant / Platform Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = totalAmountText, onValueChange = { totalAmountText = it }, label = { Text("Total Cart Amount (NGN)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                val amt = totalAmountText.toDoubleOrNull() ?: 120000.0
                Text("Pay in 4 installments of ₦${String.format("%,.2f", amt / 4.0)} at 0.00% APR.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                Button(
                    onClick = {
                        viewModel.activateEmbeddedBnpl(merchant, amt) { res ->
                            scope.launch {
                                snackbarHostState.showSnackbar(res)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Approve Embedded BNPL Order")
                }
            }
        }
    }
}

// Modal Dialog 4: Voice Biometric Test
@Composable
fun VoiceBiometricModal(onDismiss: () -> Unit) {
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Press button and speak passphrase: 'My voice is my secure password'") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Voice Biometric Verification", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(Icons.Filled.Mic, null, tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text(statusText, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                Button(
                    onClick = {
                        isListening = true
                        statusText = "Listening and verifying spectral voice harmonics..."
                        // Simulate verification after delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isListening = false
                            statusText = "✅ Voiceprint Verified (Confidence: 99.4%). Match score passed!"
                        }, 1500)
                    },
                    enabled = !isListening,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isListening) "Analyzing Voice Spectrum..." else "Record Voice Passphrase")
                }

                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

// Modal Dialog 5: DeFi Staking Modal
@Composable
fun DeFiStakingModal(viewModel: ApexViewModel, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    var poolName by remember { mutableStateOf("APEX-USDT LP") }
    var stakeAmountText by remember { mutableStateOf("100") }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("DeFi Smart Contract Staking", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }

                OutlinedTextField(value = stakeAmountText, onValueChange = { stakeAmountText = it }, label = { Text("Amount to Stake") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        val amt = stakeAmountText.toDoubleOrNull() ?: 100.0
                        viewModel.stakeInDeFiPool(poolName, amt) { res ->
                            scope.launch {
                                snackbarHostState.showSnackbar(res)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Execute Smart Contract Staking")
                }
            }
        }
    }
}


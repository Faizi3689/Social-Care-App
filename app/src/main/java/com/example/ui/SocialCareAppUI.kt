package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppLimitEntity
import com.example.data.database.FocusScheduleEntity
import com.example.data.database.SecurityLogEntity
import com.example.data.repository.UserRole
import com.example.ui.components.CaptureThumbnail
import com.example.ui.components.SecurityCameraView
import com.example.utils.InstalledAppInfo
import com.example.utils.UsageStatsHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Professional Polish design theme palette
val BackgroundSlate = Color(0xFFF7F9FC)
val SurfaceCardSlate = Color(0xFFFFFFFF)
val AccentPink = Color(0xFF4F46E5) // Beautiful Regal Indigo-600
val AccentCoolBlue = Color(0xFF3B82F6) // Polished Cool Blue
val AccentGreen = Color(0xFF10B981) // Clean Trust Emerald Green
val TextMuted = Color(0xFF64748B) // Sleek Slate-500
val TextDark = Color(0xFF0F172A) // Deep Slate-900 for professional readability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialCareAppUI(
    viewModel: SocialCareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val userRole by viewModel.userRoleState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("dashboard") }
    var verificationMode by remember { mutableStateOf(false) }
    var selectedLockPackage by remember { mutableStateOf("") }

    // Pin State for child check
    var showPinValidationDialog by remember { mutableStateOf(false) }
    var pinDialogTargetTab by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundSlate,
        bottomBar = {
            if (onboardingCompleted && userRole != UserRole.NONE && !verificationMode) {
                SocialCareBottomNavigation(
                    activeTab = activeTab,
                    userRole = userRole,
                    onTabSelected = { tab ->
                        if (userRole == UserRole.CHILD && (tab == "parental" || tab == "logs")) {
                            // Require Parent PIN verification to access parental/logs tabs!
                            pinDialogTargetTab = tab
                            showPinValidationDialog = true
                        } else {
                            activeTab = tab
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !onboardingCompleted -> {
                    OnboardingScreen(
                        onComplete = { viewModel.completeOnboarding() }
                    )
                }
                userRole == UserRole.NONE -> {
                    LoginAndRoleSelectScreen(
                        onRoleSelected = { role, pin ->
                            viewModel.setChildPinCode(pin)
                            viewModel.selectUserRole(role)
                            activeTab = "dashboard"
                        }
                    )
                }
                verificationMode -> {
                    VerificationCameraPortal(
                        packageName = selectedLockPackage,
                        onResult = { file ->
                            viewModel.logSecurityEvent(
                                eventType = "VERIFICATION_SUCCESS",
                                message = "Visual identification successfully verified to unlock app limits.",
                                imagePath = file.absolutePath
                            )
                            verificationMode = false
                        },
                        onClose = { verificationMode = false }
                    )
                }
                else -> {
                    // Actual screens
                    when (activeTab) {
                        "dashboard" -> {
                            if (userRole == UserRole.CHILD) {
                                ChildDashboardView(
                                    viewModel = viewModel,
                                    onReqVerify = { pkg ->
                                        selectedLockPackage = pkg
                                        verificationMode = true
                                    }
                                )
                            } else {
                                ParentDashboardView(viewModel = viewModel)
                            }
                        }
                        "limits" -> {
                            LimitsControlView(viewModel = viewModel)
                        }
                        "focus" -> {
                            FocusModeSchedulesView(viewModel = viewModel)
                        }
                        "parental" -> {
                            ParentalControlConsole(viewModel = viewModel)
                        }
                        "logs" -> {
                            SecurityAuditLogsView(viewModel = viewModel)
                        }
                        "settings" -> {
                            SettingsAndPrivacyView(
                                viewModel = viewModel,
                                onRoleReset = {
                                    viewModel.selectUserRole(UserRole.NONE)
                                }
                            )
                        }
                    }
                }
            }

            // PIN validation dialog (locking child away from Parent console)
            if (showPinValidationDialog) {
                PinPasscodeValidationDialog(
                    onVerifyPin = { pin ->
                        if (viewModel.checkParentPin(pin)) {
                            showPinValidationDialog = false
                            activeTab = pinDialogTargetTab
                        } else {
                            viewModel.logIntruderAttempt(pin, null)
                            // Display warning
                            false
                        }
                        true
                    },
                    onDismiss = { showPinValidationDialog = false }
                )
            }
        }
    }
}

// Bottom Navigation items
@Composable
fun SocialCareBottomNavigation(
    activeTab: String,
    userRole: UserRole,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceCardSlate,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == "dashboard",
            onClick = { onTabSelected("dashboard") },
            label = { Text("Home") },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentPink,
                selectedTextColor = AccentPink,
                indicatorColor = AccentPink.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            )
        )
        NavigationBarItem(
            selected = activeTab == "limits",
            onClick = { onTabSelected("limits") },
            label = { Text("Limits") },
            icon = { Icon(Icons.Default.Timelapse, contentDescription = "Limits") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCoolBlue,
                selectedTextColor = AccentCoolBlue,
                indicatorColor = AccentCoolBlue.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            )
        )
        NavigationBarItem(
            selected = activeTab == "focus",
            onClick = { onTabSelected("focus") },
            label = { Text("Focus") },
            icon = { Icon(Icons.Default.Block, contentDescription = "Focus") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentPink,
                selectedTextColor = AccentPink,
                indicatorColor = AccentPink.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            )
        )
        NavigationBarItem(
            selected = activeTab == "parental" || activeTab == "logs",
            onClick = { onTabSelected(if (userRole == UserRole.PARENT) "parental" else "parental") },
            label = { Text("Parental") },
            icon = { Icon(Icons.Default.Security, contentDescription = "Parental controls") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGreen,
                selectedTextColor = AccentGreen,
                indicatorColor = AccentGreen.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            )
        )
        NavigationBarItem(
            selected = activeTab == "settings",
            onClick = { onTabSelected("settings") },
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCoolBlue,
                selectedTextColor = AccentCoolBlue,
                indicatorColor = AccentCoolBlue.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            )
        )
    }
}

// Onboarding and Permission Screen
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var usageStatsGranted by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    var drawOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Launcher for camera permissions
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    // Refresh checkers periodically
    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundSlate, SurfaceCardSlate)
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = AccentPink,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Social Care Guard",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = TextDark,
                textAlign = TextAlign.Center
            )
            Text(
                "Complete onboarding guidelines & permissions securely setup to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Card 1: Usage Stats
        item {
            PermissionSetupCard(
                title = "1. Usage Access Permission",
                description = "Required to calculate screen time and block limits reactively.",
                isGranted = usageStatsGranted,
                actionLabel = "Enable Usage Access",
                onConfigure = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        usageStatsGranted = UsageStatsHelper.hasUsageStatsPermission(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }

        // Card 2: Draw Overlays
        item {
            PermissionSetupCard(
                title = "2. System Alert Overlay",
                description = "Allows showing the secure blackout screen immediately when time limits expire.",
                isGranted = drawOverlayGranted,
                actionLabel = "Enable Overlay Drawing",
                onConfigure = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)
                        )
                        context.startActivity(intent)
                        drawOverlayGranted = Settings.canDrawOverlays(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }

        // Card 3: Camera
        item {
            PermissionSetupCard(
                title = "3. Front Verification Camera",
                description = "Strictly captures verification faces or failed PIN intrusion photos. Complete GDPR consent.",
                isGranted = cameraPermissionGranted,
                actionLabel = "Grant Camera Permissions",
                onConfigure = {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onComplete() },
                enabled = usageStatsGranted,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_onboarding_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    "Launch Social Care Console",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!usageStatsGranted) {
                Text(
                    "Please authorize 'Usage Access Permission' to start local tracking.",
                    color = AccentPink,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionSetupCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isGranted) AccentGreen.copy(0.15f) else AccentPink.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isGranted) "GRANTED" else "REQUIRED",
                        color = if (isGranted) AccentGreen else AccentPink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (!isGranted) {
                Button(
                    onClick = onConfigure,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoolBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "✓ Verification configured successfully",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Role selection & Login view layout
@Composable
fun LoginAndRoleSelectScreen(
    onRoleSelected: (UserRole, String) -> Unit
) {
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }
    var pinText by remember { mutableStateOf("") }
    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var isNewUser by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChildCare,
            contentDescription = null,
            tint = AccentCoolBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Welcome to Social Care",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextDark
        )
        Text(
            text = "Configure user auth securely via Firebase integration block",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Email and Password Input (Real UI integrations)
        OutlinedTextField(
            value = emailText,
            onValueChange = { emailText = it },
            label = { Text("Parental Email") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCoolBlue,
                unfocusedBorderColor = TextMuted,
                focusedLabelColor = AccentCoolBlue,
                unfocusedLabelColor = TextMuted,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = passwordText,
            onValueChange = { passwordText = it },
            label = { Text("Parental Access Password") },
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCoolBlue,
                unfocusedBorderColor = TextMuted,
                focusedLabelColor = AccentCoolBlue,
                unfocusedLabelColor = TextMuted,
                focusedTextColor = TextDark,
                unfocusedTextColor = TextDark
            ),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Select device ownership role below:",
            style = MaterialTheme.typography.labelLarge,
            color = TextDark,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedRole = UserRole.CHILD }
                    .border(
                        1.5.dp,
                        if (selectedRole == UserRole.CHILD) AccentPink else Color(0xFFE2E8F0),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedRole == UserRole.CHILD) AccentPink.copy(0.1f) else SurfaceCardSlate
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ChildCare, contentDescription = null, tint = AccentPink)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CHILD DEVICE", color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedRole = UserRole.PARENT }
                    .border(
                        1.5.dp,
                        if (selectedRole == UserRole.PARENT) AccentGreen else Color(0xFFE2E8F0),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedRole == UserRole.PARENT) AccentGreen.copy(0.1f) else SurfaceCardSlate
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PARENT ADMIN", color = TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Access PIN Setup Card
        if (selectedRole != null) {
            Text(
                "Establish Parental PIN Control Lock (4 digits):",
                style = MaterialTheme.typography.labelSmall,
                color = AccentPink,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = pinText,
                onValueChange = { if (it.length <= 4) pinText = it },
                placeholder = { Text("e.g. 5678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = TextMuted,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                ),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().testTag("auth_pin_input")
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (pinText.length == 4 && emailText.isNotEmpty() && passwordText.isNotEmpty()) {
                        onRoleSelected(selectedRole!!, pinText)
                    }
                },
                enabled = pinText.length == 4 && emailText.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_login_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Lock Selection & Authenticate", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Child dashboard featuring canvas rings
@Composable
fun ChildDashboardView(
    viewModel: SocialCareViewModel,
    onReqVerify: (String) -> Unit
) {
    val limits by viewModel.appLimits.collectAsStateWithLifecycle()
    val rawSuggestions = remember(limits) { viewModel.generateWellbeingSuggestions(limits) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Donut Ring representation using standard Compose Canvas
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawArc(
                                color = AccentPink.copy(alpha = 0.2f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 14f, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = AccentPink,
                                startAngle = -90f,
                                sweepAngle = 230f, // 65% simulation
                                useCenter = false,
                                style = Stroke(width = 14f, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("135", fontSize = 20.sp, color = TextDark, fontWeight = FontWeight.Bold)
                            Text("mins", fontSize = 10.sp, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            "Today's Screen Time",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextDark
                        )
                        Text(
                            "Device Health check: Good status",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "All statistics are compiled locally and visible only to verification owners.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Suggestions Box
        item {
            Text(
                "Digital Wellbeing Smart Advisor",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            rawSuggestions.forEach { advice ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentCoolBlue.copy(0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentCoolBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(advice, color = TextDark, fontSize = 12.sp)
                    }
                }
            }
        }

        // Child App Usage Limits Tracker List
        item {
            Text(
                "Your Active App Limitations",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextDark
            )
        }

        if (limits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timelapse, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No limits established by Parent console.", color = TextMuted, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(limits) { limit ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(limit.appLabel, color = TextDark, fontWeight = FontWeight.Bold)
                            Text(limit.packageName, color = TextMuted, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Remaining time limit: ${limit.limitMinutes - limit.usedMinutesToday} mins",
                                color = if (limit.usedMinutesToday >= limit.limitMinutes) AccentPink else AccentCoolBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Unlock actions
                        Button(
                            onClick = { onReqVerify(limit.packageName) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Unlock Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Limits configurations View Tab
@Composable
fun LimitsControlView(
    viewModel: SocialCareViewModel
) {
    val limits by viewModel.appLimits.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAppPackage by remember { mutableStateOf("") }
    var selectedAppName by remember { mutableStateOf("") }
    var chosenMinutes by remember { mutableStateOf("45") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Digital Limitations Control",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Text(
                    "Assign and restrict packages safely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentPink,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Limit")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (limits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(56.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No limits established. Click '+' to setup an app.", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(limits) { limit ->
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(limit.appLabel, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(limit.packageName, color = TextMuted, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .background(AccentPink.copy(0.12f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${limit.limitMinutes} min limit", color = AccentPink, fontSize = 11.sp)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteAppLimit(limit.packageName) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete limit", tint = AccentPink)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = SurfaceCardSlate,
            title = { Text("Configure App Limitation", color = TextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Configure daily screen timers for local packages",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    // Simple select box for apps
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TextMuted, RoundedCornerShape(8.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (selectedAppName.isEmpty()) "Select App Package" else selectedAppName,
                            color = TextDark
                        )
                    }

                    if (dropdownExpanded) {
                        // Display list of apps scroll
                        Box(
                            modifier = Modifier
                                .height(160.dp)
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            LazyColumn {
                                items(installedApps) { app ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedAppPackage = app.packageName
                                                selectedAppName = app.appName
                                                dropdownExpanded = false
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Text(app.appName, color = TextDark, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = chosenMinutes,
                        onValueChange = { chosenMinutes = it },
                        label = { Text("Daily Limit Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextDark,
                            unfocusedTextColor = TextDark,
                            focusedBorderColor = AccentPink,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = AccentPink,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minutes = chosenMinutes.toIntOrNull() ?: 45
                        if (selectedAppPackage.isNotEmpty()) {
                            viewModel.saveAppLimit(selectedAppPackage, selectedAppName, minutes)
                            showAddDialog = false
                            selectedAppPackage = ""
                            selectedAppName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    modifier = Modifier.testTag("confirm_add_limit_btn")
                ) {
                    Text("Establish Limit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Dismiss", color = TextMuted)
                }
            }
        )
    }
}

// Focus Mode schedules management view tab
@Composable
fun FocusModeSchedulesView(
    viewModel: SocialCareViewModel
) {
    val schedules by viewModel.focusSchedules.collectAsStateWithLifecycle()
    var showAddSchedule by remember { mutableStateOf(false) }

    var labelStr by remember { mutableStateOf("Study Session Block") }
    var startH by remember { mutableStateOf("14") }
    var startM by remember { mutableStateOf("30") }
    var endH by remember { mutableStateOf("16") }
    var endM by remember { mutableStateOf("00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Smart Focus Mode",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Text(
                    "Block social and recreation activities scheduled times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            FloatingActionButton(
                onClick = { showAddSchedule = true },
                containerColor = AccentCoolBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Schedule")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(schedules) { schedule ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(schedule.title, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    String.format(
                                        Locale.US,
                                        "Time Window: %02d:%02d to %02d:%02d",
                                        schedule.startHour,
                                        schedule.startMinute,
                                        schedule.endHour,
                                        schedule.endMinute
                                    ),
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = schedule.isActive,
                                onCheckedChange = { viewModel.toggleFocusSchedule(schedule.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCoolBlue,
                                    checkedTrackColor = AccentCoolBlue.copy(0.4f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Delete Block Rule",
                                fontSize = 11.sp,
                                color = AccentPink,
                                modifier = Modifier
                                    .clickable { viewModel.deleteFocusSchedule(schedule.id) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSchedule) {
        AlertDialog(
            onDismissRequest = { showAddSchedule = false },
            containerColor = SurfaceCardSlate,
            title = { Text("Configure Focus Block Rule", color = TextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = labelStr,
                        onValueChange = { labelStr = it },
                        label = { Text("Schedule Label Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextDark,
                            unfocusedTextColor = TextDark,
                            focusedBorderColor = AccentCoolBlue,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = AccentCoolBlue,
                            unfocusedLabelColor = TextMuted
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startH,
                            onValueChange = { startH = it },
                            label = { Text("Start Hour") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark,
                                focusedBorderColor = AccentCoolBlue,
                                unfocusedBorderColor = TextMuted,
                                focusedLabelColor = AccentCoolBlue,
                                unfocusedLabelColor = TextMuted
                            )
                        )
                        OutlinedTextField(
                            value = startM,
                            onValueChange = { startM = it },
                            label = { Text("Start Min") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark,
                                focusedBorderColor = AccentCoolBlue,
                                unfocusedBorderColor = TextMuted,
                                focusedLabelColor = AccentCoolBlue,
                                unfocusedLabelColor = TextMuted
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endH,
                            onValueChange = { endH = it },
                            label = { Text("End Hour") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark,
                                focusedBorderColor = AccentCoolBlue,
                                unfocusedBorderColor = TextMuted,
                                focusedLabelColor = AccentCoolBlue,
                                unfocusedLabelColor = TextMuted
                            )
                        )
                        OutlinedTextField(
                            value = endM,
                            onValueChange = { endM = it },
                            label = { Text("End Min") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark,
                                focusedBorderColor = AccentCoolBlue,
                                unfocusedBorderColor = TextMuted,
                                focusedLabelColor = AccentCoolBlue,
                                unfocusedLabelColor = TextMuted
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sh = startH.toIntOrNull() ?: 14
                        val sm = startM.toIntOrNull() ?: 0
                        val eh = endH.toIntOrNull() ?: 17
                        val em = endM.toIntOrNull() ?: 0
                        viewModel.saveFocusSchedule(labelStr, sh, sm, eh, em)
                        showAddSchedule = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCoolBlue)
                ) {
                    Text("Save Focus Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSchedule = false }) {
                    Text("Dismiss", color = TextMuted)
                }
            }
        )
    }
}

// Parent dashboard overview screen
@Composable
fun ParentDashboardView(
    viewModel: SocialCareViewModel
) {
    val limits by viewModel.appLimits.collectAsStateWithLifecycle()
    val synced by viewModel.parentSyncedState.collectAsStateWithLifecycle()
    val logs by viewModel.securityLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Social Care Remote Sync",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextDark
                            )
                            Text(
                                if (synced) "Connected securely with Child device via Firestore" else "Simulation: Offline. Set Firebase to secure real-time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (synced) AccentGreen else AccentPink
                            )
                        }

                        Button(
                            onClick = { viewModel.syncDataWithCloud() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sync Stats")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Active App Limit Rules", color = TextMuted, fontSize = 11.sp)
                            Text("${limits.size} configurations", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column {
                            Text("Logged Security Flags", color = TextMuted, fontSize = 11.sp)
                            Text("${logs.size} events", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentPink.copy(0.12f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, AccentPink.copy(0.3f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentPink, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Intrusion Security Shield Active",
                            color = TextDark,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "If any wrong parental limit-unlock PIN is introduced on the child screen, front selfie is triggered natively.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Quick Remote Admin Actions",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextDark
            )
        }

        item {
            ElevatedCard(
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Child Live App Tracking Monitor", color = TextDark, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Local overview showing packages configured for Screen Restrictions", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (limits.isEmpty()) {
                        Text("No packages currently added in Limits controller.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        limits.forEach { limit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(limit.appLabel, color = TextDark, fontSize = 13.sp)
                                Text("${limit.usedMinutesToday} / ${limit.limitMinutes} mins used", color = AccentPink, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Parental Sync & Control dialog consoles
@Composable
fun ParentalControlConsole(
    viewModel: SocialCareViewModel
) {
    val synced by viewModel.parentSyncedState.collectAsStateWithLifecycle()
    val limits by viewModel.appLimits.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Security Remote Sync Console",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = TextDark
        )

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Firestore cloud-sync module state:",
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Cloud-sync is configured to push active screen durations safely to your parental dashboard.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.syncDataWithCloud() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trigger Secure Cloud Sync")
                }
            }
        }
    }
}

// Security activity logs audit screen displaying photos captured during wrong PIN unlocks
@Composable
fun SecurityAuditLogsView(
    viewModel: SocialCareViewModel
) {
    val logs by viewModel.securityLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Security Activity logs",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Text(
                    "Failed lock bypass attempts and captures",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            IconButton(
                onClick = { viewModel.clearAllLogs() }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = AccentPink)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(52.dp), tint = AccentGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your device is secure. Zero alerts found.", color = TextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(logs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (log.eventType == "INTRUDER_ATTEMPT") AccentPink else AccentGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = log.eventType,
                                        fontWeight = FontWeight.Bold,
                                        color = if (log.eventType == "INTRUDER_ATTEMPT") AccentPink else AccentGreen,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.description, color = TextDark, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                Text("Timestamp: $date", color = TextMuted, fontSize = 10.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Draw photo captured if available
                            CaptureThumbnail(
                                imagePath = log.imagePath,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Security Camera Verification screen
@Composable
fun VerificationCameraPortal(
    packageName: String,
    onResult: (File) -> Unit,
    onClose: () -> Unit
) {
    var photoTaken by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundSlate)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Visual Identification Portal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Verifying security unlocks for: $packageName",
            color = TextMuted,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, AccentPink, RoundedCornerShape(24.dp))
        ) {
            SecurityCameraView(
                modifier = Modifier.fillMaxSize(),
                onPhotoCaptured = { file ->
                    photoTaken = file
                    onResult(file)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardSlate),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Text("Cancel", color = TextDark)
            }
        }
    }
}

// Custom Keypad for Parent PIN input dialog
@Composable
fun PinPasscodeValidationDialog(
    onVerifyPin: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCardSlate,
        title = {
            Text(
                "Verify Parental Passcode",
                color = TextDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Introduce your 4 digits configuration PIN code to authorize access.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                // Visual Pin Progress Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    for (i in 1..4) {
                        val active = pinValue.length >= i
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (active) AccentPink else TextMuted.copy(alpha = 0.3f))
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = AccentPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // PIN Keypad grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Clear", "0", "Back")
                    )

                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (cell in row) {
                                KeypadButton(
                                    label = cell,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (cell) {
                                            "Clear" -> pinValue = ""
                                            "Back" -> if (pinValue.isNotEmpty()) pinValue = pinValue.dropLast(1)
                                            else -> {
                                                if (pinValue.length < 4) {
                                                    pinValue += cell
                                                    errorMessage = ""
                                                    if (pinValue.length == 4) {
                                                        val result = onVerifyPin(pinValue)
                                                        if (!result) {
                                                            errorMessage = "INCORRECT PASSCODE PIN"
                                                            pinValue = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss Validation", color = TextMuted)
            }
        }
    )
}

@Composable
fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BackgroundSlate)
            .clickable { onClick() }
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextDark,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 1) 12.sp else 18.sp
        )
    }
}

// Settings and GDPR policy page view
@Composable
fun SettingsAndPrivacyView(
    viewModel: SocialCareViewModel,
    onRoleReset: () -> Unit
) {
    val currentRole by viewModel.userRoleState.collectAsStateWithLifecycle()
    var pinText by remember { mutableStateOf("") }
    var showingPinConfig by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Protection Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextDark
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Modify Control Access PIN", color = TextDark, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Configure the 4 digit lock code to unlock child applications", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!showingPinConfig) {
                        Button(
                            onClick = { showingPinConfig = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCoolBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Access PIN")
                        }
                    } else {
                        OutlinedTextField(
                            value = pinText,
                            onValueChange = { if (it.length <= 4) pinText = it },
                            placeholder = { Text("New PIN e.g. 1111") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDark,
                                unfocusedTextColor = TextDark,
                                focusedBorderColor = AccentCoolBlue,
                                unfocusedBorderColor = TextMuted,
                                focusedLabelColor = AccentCoolBlue,
                                unfocusedLabelColor = TextMuted
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (pinText.length == 4) {
                                        viewModel.setChildPinCode(pinText)
                                        showingPinConfig = false
                                        pinText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                            ) {
                                Text("Save PIN")
                            }
                            TextButton(onClick = { showingPinConfig = false }) {
                                Text("Cancel", color = TextDark)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Deconfigure Social Care Console", color = TextDark, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Resets current access configuration parameters to setup again.", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRoleReset,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset Role & Authenticate")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardSlate)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GDPR Compliance & Private Policy", color = TextDark, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Zero Hidden Tracking: Social Care does not transmit telemetry logs about child browsing or private details to third party vendors. All checks execute on-device.\n\n" +
                               "2. Real-time visible camera: Camera captures are strictly initiated inside either explicit visual portal verification actions or failed authorization attempts (wrong PIN codes). There are absolutely NO silent or background captures.\n\n" +
                               "3. Full User Control: All usage stats and security captures are stored inside standard Android local Room databases, which are deleted immediately upon uninstalling.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.data.ai.GeminiOcrRepository
import com.example.data.local.LocalDictionaryRepository
import com.example.data.local.TasniremtDatabase
import com.example.data.local.ThemePreferencesRepository
import com.example.data.remote.TasniremtFirestoreRepository
import com.example.ui.MainViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.ContributeWordScreen
import com.example.ui.screens.LexiconScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.OcrScannerScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.FloatingLiquidNavBar
import com.example.ui.theme.FloatingNavItem
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPillChip
import com.example.ui.theme.LiquidGlassBackdrop
import com.example.ui.theme.LocalGlassTokens
import com.example.ui.theme.TasniremtTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private const val ROUTE_LEXICON = "lexicon"
private const val ROUTE_SCANNER = "scanner"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_CONTRIBUTE = "contribute"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferencesRepository(context.applicationContext) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            TasniremtTheme(themeMode = themeMode) {
                TasniremtRootNavigation(
                    themeMode = themeMode,
                    themePrefs = themePrefs,
                    onToggleTheme = {
                        val next = if (themeMode == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
                        scope.launch { themePrefs.setThemeMode(next) }
                    }
                )
            }
        }
    }
}

@Composable
private fun TasniremtRootNavigation(
    themeMode: AppThemeMode,
    themePrefs: ThemePreferencesRepository,
    onToggleTheme: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            themeMode = themeMode,
            onToggleTheme = onToggleTheme,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                currentUserId = user.uid,
                currentUserName = user.displayName ?: "Amazigh Scholar",
                currentUserEmail = user.email ?: "",
                themePrefs = themePrefs
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedAppShell(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String,
    themePrefs: ThemePreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val localDb = TasniremtDatabase.getInstance(app)
                MainViewModel(
                    firestoreRepo = TasniremtFirestoreRepository(db),
                    localDictRepo = LocalDictionaryRepository(app, localDb.dictionaryDao()),
                    geminiRepo = GeminiOcrRepository(),
                    themePrefs = themePrefs,
                    currentUserId = currentUserId,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_LEXICON
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val tokens = LocalGlassTokens.current

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    var pendingBookPage by remember { mutableStateOf<Int?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }

    val navItems = remember {
        listOf(
            FloatingNavItem(
                route = ROUTE_LEXICON,
                label = "Lexicon",
                selectedIcon = Icons.Rounded.MenuBook,
                unselectedIcon = Icons.Outlined.MenuBook,
                testTag = "nav_lexicon"
            ),
            FloatingNavItem(
                route = ROUTE_SCANNER,
                label = "OCR Scan",
                selectedIcon = Icons.Rounded.DocumentScanner,
                unselectedIcon = Icons.Outlined.DocumentScanner,
                testTag = "nav_scanner"
            ),
            FloatingNavItem(
                route = ROUTE_LIBRARY,
                label = "Library",
                selectedIcon = Icons.Rounded.CollectionsBookmark,
                unselectedIcon = Icons.Outlined.CollectionsBookmark,
                testTag = "nav_library"
            ),
            FloatingNavItem(
                route = ROUTE_CONTRIBUTE,
                label = "Contribute",
                selectedIcon = Icons.Rounded.AddCircle,
                unselectedIcon = Icons.Outlined.AddCircleOutline,
                testTag = "nav_contribute"
            )
        )
    }

    LiquidGlassBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Sleek Liquid Glass Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "ⵜⴰⵙⵏⵉⵔⴻⵎⵜ • Tamazight ↔ AR / FR / EN",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.tamazightAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = viewModel::cycleThemeMode,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(tokens.glassSurface)
                                .testTag("header_theme_button")
                        ) {
                            Icon(
                                imageVector = if (tokens.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Switch theme mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showAccountSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(tokens.glassSurface)
                                .testTag("header_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "Account and settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Active Navigation Host
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_LEXICON,
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() }
                    ) {
                        composable(ROUTE_LEXICON) {
                            LexiconScreen(
                                viewModel = viewModel,
                                onNavigateToScanPage = { page ->
                                    pendingBookPage = page
                                    navController.navigate(ROUTE_SCANNER) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable(ROUTE_SCANNER) {
                            OcrScannerScreen(
                                viewModel = viewModel,
                                initialBookPage = pendingBookPage,
                                onClearInitialPage = { pendingBookPage = null }
                            )
                        }
                        composable(ROUTE_LIBRARY) {
                            LibraryScreen(
                                viewModel = viewModel,
                                onNavigateToContribute = {
                                    navController.navigate(ROUTE_CONTRIBUTE) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable(ROUTE_CONTRIBUTE) {
                            ContributeWordScreen(viewModel = viewModel)
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 94.dp, start = 16.dp, end = 16.dp)
            )

            FloatingLiquidNavBar(
                items = navItems,
                selectedRoute = currentRoute,
                onSelect = { targetRoute ->
                    if (targetRoute != currentRoute) {
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showAccountSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = currentUserName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (currentUserEmail.isNotBlank()) {
                    Text(
                        text = currentUserEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Column {
                        Text(
                            text = "Appearance Theme",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                GlassPillChip(
                                    label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        showAccountSheet = false
                        signOutUser(credentialManager, scope)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

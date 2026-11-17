package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.viewmodel.StudyverseViewModel
import kotlinx.coroutines.launch

data class StudyNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val inMobileBottomBar: Boolean = false
)

val PrimaryNavItems = listOf(
    StudyNavItem("dashboard", "Dashboard", Icons.Default.Dashboard, inMobileBottomBar = true),
    StudyNavItem("subjects", "My Subjects", Icons.AutoMirrored.Filled.MenuBook, inMobileBottomBar = true),
    StudyNavItem("planner", "Study Planner", Icons.Default.Schedule, inMobileBottomBar = true),
    StudyNavItem("calendar", "Calendar", Icons.Default.CalendarMonth),
    StudyNavItem("deadlines", "Deadlines", Icons.Default.EventBusy),
    StudyNavItem("ai_assistant", "AI Assistant", Icons.Default.AutoAwesome, inMobileBottomBar = true),
    StudyNavItem("notes", "Notes", Icons.Default.Description),
    StudyNavItem("analytics", "Analytics", Icons.Default.Analytics),
    StudyNavItem("focus_timer", "Focus Timer", Icons.Default.Timer, inMobileBottomBar = true),
    StudyNavItem("profile", "Profile & Settings", Icons.Default.Person)
)

val ExtraHubNavItems = listOf(
    StudyNavItem("exam_prep", "Exam Prep Mode", Icons.Default.School),
    StudyNavItem("habits", "Habit Tracker", Icons.Default.LocalFireDepartment),
    StudyNavItem("study_groups", "Study Groups", Icons.Default.Group)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyverseApp(viewModel: StudyverseViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val allItems = PrimaryNavItems + ExtraHubNavItems
    val currentTitle = allItems.firstOrNull { it.route == currentRoute }?.label ?: "Studyverse"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp
        var sidebarExpanded by remember { mutableStateOf(false) }

        if (isWideScreen) {
            // Tablet / Desktop: Collapsible Navigation Rail/Sidebar
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        IconButton(onClick = { sidebarExpanded = !sidebarExpanded }) {
                            Icon(
                                if (sidebarExpanded) Icons.Default.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Toggle Sidebar"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    allItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = { navigateTo(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = if (sidebarExpanded) {
                                { Text(item.label, maxLines = 1) }
                            } else null
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text("Studyverse • $currentTitle", fontWeight = FontWeight.Bold) }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        StudyverseNavHost(navController = navController, viewModel = viewModel, onNavigateRoute = navigateTo)
                    }
                }
            }
        } else {
            // Mobile: ModalNavigationDrawer + 5-item Bottom NavigationBar
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                "Studyverse",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            PrimaryNavItems.forEach { item ->
                                NavigationDrawerItem(
                                    label = { Text(item.label) },
                                    icon = { Icon(item.icon, contentDescription = null) },
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navigateTo(item.route)
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "Smart Hubs",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            ExtraHubNavItems.forEach { item ->
                                NavigationDrawerItem(
                                    label = { Text(item.label) },
                                    icon = { Icon(item.icon, contentDescription = null) },
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navigateTo(item.route)
                                    }
                                )
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "All Sections")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            PrimaryNavItems.filter { it.inMobileBottomBar }.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = {
                                        Text(
                                            item.label.replace("My ", "").replace("Study ", "").replace(" Timer", ""),
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        StudyverseNavHost(navController = navController, viewModel = viewModel, onNavigateRoute = navigateTo)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyverseNavHost(
    navController: androidx.navigation.NavHostController,
    viewModel: StudyverseViewModel,
    onNavigateRoute: (String) -> Unit
) {
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(viewModel, onNavigateRoute) }
        composable("subjects") { SubjectsScreen(viewModel) }
        composable("planner") { PlannerScreen(viewModel) }
        composable("calendar") { CalendarScreen(viewModel) }
        composable("deadlines") { DeadlinesScreen(viewModel) }
        composable("ai_assistant") { AiAssistantScreen(viewModel) }
        composable("notes") { NotesScreen(viewModel) }
        composable("analytics") { AnalyticsScreen(viewModel) }
        composable("focus_timer") { FocusTimerScreen(viewModel) }
        composable("profile") { ProfileSettingsScreen(viewModel) }
        composable("exam_prep") { ExamPrepScreen(viewModel) }
        composable("habits") { HabitsScreen(viewModel) }
        composable("study_groups") { StudyGroupsScreen(viewModel) }
    }
}

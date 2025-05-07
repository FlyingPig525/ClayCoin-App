package io.github.flyingpig525

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInExpo
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.flyingpig525.data.UserError
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.data.user.CLAYCOIN_INCREMENT_MS
import io.github.flyingpig525.data.user.UserData
import io.github.flyingpig525.serialization.generateHashCode
import io.github.flyingpig525.ui.chat.ChatScreen
import io.github.flyingpig525.ui.login.LoginScreen
import io.github.flyingpig525.ui.settings.SettingsScreen
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import io.github.flyingpig525.ui.user.UserScreen
import io.github.flyingpig525.ui.user.dashboard.DashboardScreen
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import java.io.FileNotFoundException
import kotlin.time.ExperimentalTime
////////////////////////////////////////////////////////////////////////////////////////////////////
const val OFFLINE = false //////// BEFORE USING THIS, START HOSTING THE KTOR SERVER LOCALLY ////////
////////////////////////////////////////////////////////////////////////////////////////////////////
const val LOCALHOST_IP = "10.0.2.2:8080"
val SERVER_IP = if (!OFFLINE) "89.117.0.24:8080" else LOCALHOST_IP
val HTTP_SERVER_IP = "http://$SERVER_IP"
val WS_SERVER_IP = "ws://$SERVER_IP"

val fontFamily = FontFamily(
    Font(R.font.ar_one_sans_bold, weight = FontWeight.Bold),
    Font(R.font.ar_one_sans, weight = FontWeight.Normal)
)

val wsCoroutineScope = CoroutineScope(Dispatchers.IO)

class MainActivity : ComponentActivity() {

    lateinit var userStorage: UserStorage
    lateinit var chatStorage: ChatStorage
    var wsJob: Job? = null
    var paused = false

    @OptIn(ExperimentalTime::class)
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        var token = try {
            applicationContext.openFileInput("token").bufferedReader().readText()
        } catch (e: FileNotFoundException) {
            null
        }
        println(token)
        Page.Companion
        var isConnected = true
        try {
            runBlocking {
                UserStorage.httpClient.get("$HTTP_SERVER_IP/") {}
            }
        } catch (e: Throwable) {
            isConnected = false
        }
        if (isConnected) {
            userStorage = UserStorage()
            chatStorage = ChatStorage()
            if (token != null) {
                userStorage.currentToken = Token(token)
            }
        }
        setContent {
            val navController = rememberNavController()
            if (!isConnected) {
                ClayCoinTheme {
                    Scaffold { innerPadding ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            Text(
                                "You are not connected to the internet!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                "Once connected restart the app"
                            )
                        }
                    }
                }
            } else {
                Application(
                    defaultRoute = if (userStorage.currentId == null) Page.login else Page.dashboard,
                    userStorage,
                    chatStorage,
                    navController = navController
                )
            }
        }
        if (isConnected) {
            wsJob = wsCoroutineScope.launch {
                chatStorage.receiveMessage()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
        runBlocking {
            if (::chatStorage.isInitialized) {
                chatStorage.closeWs()
                wsJob?.cancel("App paused")
                wsJob = null
                Log.i("MainActivity", "Chat WebSocket closed due to app pause")
            }
        }
        if (::userStorage.isInitialized && userStorage.currentToken != null) {
            Log.i("MainActivity", "Saving token")
            applicationContext.openFileOutput("token", MODE_PRIVATE).use {
                it.write(userStorage.currentToken!!.hashedToken.toByteArray())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!paused) return
        paused = false
        wsJob = wsCoroutineScope.launch {
            chatStorage.receiveMessage()
        }
        Log.i("MainActivity", "Chat WebSocket reopened due to app resume")
    }

    companion object {
        lateinit var instance: MainActivity
            private set
    }
}

@Serializable
sealed class Page(
    val route: String,
    @Transient val icon: ImageVector? = null,
    @Transient val resourceId: Int? = null,
    @Transient val saveState: Boolean = false,
    @Transient val showOnNav: Boolean = true
) {
    @Serializable
    class Settings internal constructor() : Page("settings", icon = Icons.Outlined.Settings)
    @Serializable
    class Dashboard internal constructor() : Page("dashboard", icon = Icons.Outlined.Menu)
    @Serializable
    class Messages internal constructor() : Page("messages", resourceId = R.drawable.outline_chat_24)
    @Serializable
    class Login internal constructor() : Page("login", showOnNav = false)

    companion object {
        val settings = Settings()
        val dashboard = Dashboard()
        val messages = Messages()
        val login = Login()
        val entries = listOf(
            settings,
            dashboard,
            messages,
            login
        )

        init {
            Settings
            Dashboard
            Messages
            Login
        }
    }
}

fun <T : Any> NavController.popTo(route: T, saveState: Boolean = false) {
    if (!popBackStack(route, false, saveState)) {
        navigate(route)
    }
}

@OptIn(InternalSerializationApi::class)
@Composable
fun AppNavigationBar(navController: NavController, onBadNavigation: suspend () -> Unit) {
    BottomAppBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        for (page in Page.entries) {
            if (!page.showOnNav) continue
            val cScope = rememberCoroutineScope()
            NavigationBarItem(
                selected = currentDestination?.id == page::class.serializer().generateHashCode(),
                onClick = {
                    if (MainActivity.instance.userStorage.currentId != null) {
                        navController.popTo(page, page.saveState)
                    } else {
                        cScope.launch {
                            onBadNavigation()
                        }
                    }
                },
                icon = {
                    val vector = page.icon ?: ImageVector.vectorResource(page.resourceId!!)
                    Icon(vector, contentDescription = page::class.simpleName)
                }, label = {
                    Text(text = page::class.simpleName ?: "AAADUWAHDOIU")
                },
                alwaysShowLabel = true
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Application(
    defaultRoute: Any,
    userStorage: UserStorage = UserStorage(),
    chatStorage: ChatStorage = ChatStorage(),
    navController: NavHostController = rememberNavController(),
) {
    ClayCoinTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = ShapeDefaults.ExtraLarge
                    )
                }
            },
            bottomBar = {
                AppNavigationBar(navController, onBadNavigation = {
                    snackbarHostState.showSnackbar(
                        message = "Must be logged in to navigate to other pages"
                    )
                })
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = defaultRoute,
                ) {
                    composable<Page.Dashboard> { backStackEntry ->
                        val dataStorage = userStorage.data
                        userStorage.data = null
                        var userData by remember { mutableStateOf(userStorage.data) }
                        LaunchedEffect("update userdata") {

                            val newData = userStorage.updateData()
                            if (newData.isFailure) {
                                snackbarHostState.showSnackbar(
                                    "Data get failed, try again later.\n${newData.exceptionOrNull()!!::class.simpleName}: ${newData.exceptionOrNull()!!.message}"
                                )
                                userStorage.data = dataStorage
                                userData = dataStorage
                            } else {
                                userData = newData.getOrThrow()
                            }
                        }
                        if (userData != null) {
                            DashboardScreen(
                                modifier = Modifier.padding(innerPadding),
                                userData = userData!!
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    composable<Page.Settings> {
//                        Text("settings", modifier = Modifier.padding(innerPadding))
                        SettingsScreen({
                            Log.i("Application", "logging out")
                            userStorage.currentToken = null
                            navController.popTo(Page.login)
                        })
                    }

                    composable<Page.Messages> {
                        ChatScreen(
                            modifier = Modifier.padding(innerPadding),
                            userStorage = userStorage,
                            chatStorage = chatStorage,
                            onUsernameClick = { id, setContent ->
                                val userData = runBlocking { UserStorage.getUserData(id) }
                                if (userData.isFailure) {
                                    return@ChatScreen when (userData.exceptionOrNull()) {
                                        is UserDoesNotExistException ->
                                            UserError.NotFound
                                        else -> UserError.UnknownError
                                    }
                                }
                                setContent {
                                    UserScreen(
                                        userData.getOrThrow()
                                    )
                                }
                                UserError.None
                            }
                        )
                    }

                    composable<Page.Login> {
                        LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            userStorage = userStorage,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}




@Composable
fun getColorScheme(): ColorScheme = when(isSystemInDarkTheme()) {
    true -> dynamicDarkColorScheme(LocalContext.current)
    false -> dynamicLightColorScheme(LocalContext.current)
}

@Preview(name = "Dashboard Screen Preview Light",
    device = "id:pixel_7", showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(name = "Dashboard Screen Preview", device = "id:pixel_7", showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(device = "id:Galaxy Nexus")
@Composable
fun DashboardScreenPreview() {
    Application(
        defaultRoute = Page.Dashboard
    )
}


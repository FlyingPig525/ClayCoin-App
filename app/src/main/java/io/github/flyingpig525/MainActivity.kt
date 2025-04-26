package io.github.flyingpig525

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.PersistableBundle
import android.os.storage.StorageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.persistableBundleOf
import androidx.datastore.core.FileStorage
import androidx.datastore.core.Storage
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.ui.chat.ChatScreen
import io.github.flyingpig525.ui.login.LoginScreen
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.io.File
import java.io.FileNotFoundException

const val SERVER_IP = "89.117.0.24:8080"
const val HTTP_SERVER_IP = "http://$SERVER_IP"
const val WS_SERVER_IP = "ws://$SERVER_IP"

val fontFamily = FontFamily(
    Font(R.font.ar_one_sans_bold, weight = FontWeight.Bold),
    Font(R.font.ar_one_sans, weight = FontWeight.Normal)
)

val wsCoroutineScope = CoroutineScope(Dispatchers.IO)

class MainActivity : ComponentActivity() {

    val userStorage = UserStorage()
    val chatStorage = ChatStorage()
    var wsJob: Job? = null
    var paused = false

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
        if (token != null) {
            userStorage.currentToken = Token(token)
        }
        setContent {
            val navController = rememberNavController()
            if (userStorage.currentId == null) {
                ClayCoinTheme {
                    Scaffold { innerPadding ->
                        LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            userStorage = userStorage,
                            chatStorage = chatStorage,
                            windowBounds = windowManager.currentWindowMetrics.bounds,
                            navController = navController
                        )
                    }
                }
            } else {
                Application(
                    windowManager.currentWindowMetrics.bounds,
                    userStorage,
                    chatStorage,
                    navController = navController
                )
            }
        }
        wsJob = wsCoroutineScope.launch {
            chatStorage.receiveMessage()
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
        runBlocking {
            chatStorage.closeWs()
            wsJob?.cancel("App paused")
            wsJob = null
            Log.i("MainActivity", "Chat WebSocket closed due to app pause")
        }
        if (userStorage.currentToken != null) {
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

enum class Page(val route: String, val icon: ImageVector? = null, val resourceId: Int? = null) {
    Settings("settings", icon = Icons.Outlined.Settings),
    Dashboard("dashboard", icon = Icons.Outlined.Menu),
    Messages("messages", resourceId = R.drawable.outline_chat_24)
}

@Composable
fun AppNavigationBar(navController: NavController) {
    BottomAppBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        for (page in Page.entries) {
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any {
                    it.route == page.route
                } == true,
                onClick = {
                    if (!navController.popBackStack(page.route, false, true)) {
                        navController.navigate(page.route)
                    }
                },
                icon = {
                    val vector = page.icon ?: ImageVector.vectorResource(page.resourceId!!)
                    Icon(vector, contentDescription = page.name)
                }, label = {
                    Text(text = page.name)
                },
                alwaysShowLabel = true
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Application(
    windowBounds: Rect,
    userStorage: UserStorage = UserStorage(),
    chatStorage: ChatStorage = ChatStorage(),
    navController: NavHostController = rememberNavController()
) {
    ClayCoinTheme {
        Scaffold(
            bottomBar = {
                AppNavigationBar(navController)
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = Page.Dashboard.route,
                ) {
                    composable(Page.Dashboard.route) {
                        DashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            windowBounds = windowBounds,
                            startingCoins = 0
                        )
                    }

                    composable(Page.Settings.route) {
//                        Text("settings", modifier = Modifier.padding(innerPadding))
                        LoginScreen(
                            windowBounds = windowBounds,
                            modifier = Modifier.padding(innerPadding),
                            userStorage = userStorage,
                            chatStorage,
                            navController = navController
                        )
                    }

                    composable(Page.Messages.route) {
                        ChatScreen(
                            modifier = Modifier.padding(innerPadding),
                            userStorage = userStorage,
                            chatStorage = chatStorage
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    windowBounds: Rect,
    startingCoins: Int,
    modifier: Modifier = Modifier,
    coinIncreaseWaitMillis: Int = 2500
) {
    var coins = rememberSaveable { mutableIntStateOf(startingCoins) }
    val circleWidth = windowBounds.width().toDouble()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier.fillMaxWidth(circleWidth.toFloat()),
            contentAlignment = Alignment.Center
        ) {
            ClaycoinDisplay(coins, coinIncreaseWaitMillis)
        }
        Button(
            onClick = {
                coins.intValue++
            }
        ) {
            Text(
                "Increase those beautiful coins!!",
                fontFamily = fontFamily,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun ClaycoinDisplay(coins: MutableIntState, coinIncreaseWaitMillis: Int = 2500) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val transition = rememberInfiniteTransition()
    // The current rotation around the circle, so we know where to start the rotation from
    val currentRotation by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation =
                tween(
                    durationMillis = coinIncreaseWaitMillis
                )
        )
    )
    val color by transition.animateColor(
        ProgressIndicatorDefaults.circularColor,
        ProgressIndicatorDefaults.circularDeterminateTrackColor,
        infiniteRepeatable(
            animation =
                tween(
                    durationMillis = coinIncreaseWaitMillis,
                    easing = LinearEasing
                )
        )
    )
    val cScope = rememberCoroutineScope()
    LaunchedEffect("increase coins") {
        cScope.launch {
            while (true) {
                delay(coinIncreaseWaitMillis.toLong())
                coins.intValue++
            }
        }
    }

    SemiCircularProgressIndicator(
        progress = { currentRotation },
        strokeWidth = 20.dp,
        modifier = Modifier.rotate(225f).scale(0.75f),
        color = color
    )

//    CircularProgressIndicator(
//        progress = { 0.75f },
//        strokeWidth = 20.dp,
//        modifier = Modifier
//            .size(LocalConfiguration.current.screenWidthDp.dp)
//            .rotate(225f)
//            .scale(0.75f),
//        trackColor = Color(0x000000FF),
//        color = ProgressIndicatorDefaults.circularDeterminateTrackColor
//    )
//    CircularProgressIndicator(
//        progress = { currentRotation },
//        strokeWidth = 20.dp,
//        modifier = Modifier
//            .size(screenWidth)
//            .scale(0.75f)
//            .rotate(225f),
//        trackColor = Color(0x000000FF),
//        color = color
//    )

    Text(
        text = "${coins.intValue}",
        fontSize = 45.sp,
        fontFamily = fontFamily,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Claycoins",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = screenWidth / 2 + 10.dp),
        textAlign = TextAlign.Center,
        fontSize = 25.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemiCircularProgressIndicator(
    progress: () -> Float,
    circlePercent: Float = 0.75f,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
    gapSize: Dp = ProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    radius: Dp = LocalConfiguration.current.screenWidthDp.dp
) {
    CircularProgressIndicator(
        progress = { circlePercent },
        strokeWidth = strokeWidth,
        modifier = Modifier
            .size(radius)
            .then(modifier),
        trackColor = Color(0x000000FF),
        color = trackColor,
        strokeCap = strokeCap,
        gapSize = gapSize
    )
    CircularProgressIndicator(
        progress = { circlePercent * progress() },
        strokeWidth = strokeWidth,
        modifier = Modifier
            .size(radius)
            .then(modifier),
        trackColor = Color(0x000000FF),
        color = color,
        strokeCap = strokeCap,
        gapSize = gapSize
    )
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
    Application(Rect(0, 0, 1080, 2400))
}


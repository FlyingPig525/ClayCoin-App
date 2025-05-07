package io.github.flyingpig525.ui.user.dashboard

import android.graphics.Rect
import android.view.WindowManager
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import io.github.flyingpig525.data.user.CLAYCOIN_INCREMENT_MS
import io.github.flyingpig525.data.user.UserCurrencies
import io.github.flyingpig525.data.user.UserData
import io.github.flyingpig525.fontFamily
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun DashboardScreen(
    userData: UserData,
    modifier: Modifier = Modifier,
    coinIncreaseWaitMillis: Int = CLAYCOIN_INCREMENT_MS
) {
    var coins by rememberSaveable { mutableLongStateOf(userData.userCurrencies.coins) }
    val offset = remember { (0..10000).random() }
    val windowManager = LocalContext.current.getSystemService<WindowManager>()
        ?: throw WindowManagerNotFoundException()
    val circleWidth = windowManager.currentWindowMetrics.bounds.width().toDouble()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth(circleWidth.toFloat()),
            contentAlignment = Alignment.Center
        ) {
            ClaycoinDisplay(coins, coinIncreaseWaitMillis, offset) { coins++ }
        }
        ShinerProgressIndicator(
            userData.userCurrencies.shinerProgress,
            userData.userCurrencies.shiners
        )
    }
}

@OptIn(ExperimentalTime::class)
@Preview(device = "id:pixel_7",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO or android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
    showSystemUi = true,
    showBackground = true
)
@Preview(device = "id:pixel_7",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
    showSystemUi = true,
    showBackground = true
)
@Composable
private fun DashboardScreenPreview() {
    ClayCoinTheme {
        Scaffold { i ->
            DashboardScreen(
                UserData(
                    "Username",
                    0,
                    UserCurrencies(
                        1213214,
                        51.20,
                        Clock.System.now().toEpochMilliseconds(),
                        3
                    ),
                    false
                ),
                modifier = Modifier.padding(i)
            )
        }
    }
}

class WindowManagerNotFoundException : Exception()
package io.github.flyingpig525.ui.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.flyingpig525.ClaycoinDisplay
import io.github.flyingpig525.data.user.UserCurrencies
import io.github.flyingpig525.data.user.UserData
import io.github.flyingpig525.getColorScheme
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(userData: UserData, modifier: Modifier = Modifier) {
    var coins by remember { mutableLongStateOf(userData.userCurrencies.coins) }
    val compositionOffset = remember { userData.calculateStartOffsetMs() }
    Column(modifier = modifier) {
        Text(
            userData.username,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            color = if (userData.admin) getColorScheme().surfaceTint else Color.Unspecified
        )

        ClaycoinDisplay(
            coins,
            startOffsetMs = compositionOffset
        ) { coins++ }
    }
}

@OptIn(ExperimentalTime::class)
@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun UserScreenPreview() {
    val now = (0..10000).random().toLong()
    ClayCoinTheme {
        Scaffold { innerPadding ->
            UserScreen(
                UserData(
                    "Username",
                    0,
                    UserCurrencies(
                        1231,
                        124.821,
                        now
                    ),
                    false
                ),
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun UserScreenAdminPreview() {
    val now = (0..10000).random().toLong()
    ClayCoinTheme {
        Scaffold { innerPadding ->
            UserScreen(
                UserData(
                    "Username",
                    0,
                    UserCurrencies(
                        1231,
                        124.821,
                        now
                    ),
                    true
                ),
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
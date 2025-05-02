package io.github.flyingpig525.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import io.github.flyingpig525.fontFamily
import io.github.flyingpig525.ui.theme.ClayCoinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogOutPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Settings")
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = modifier.padding(innerPadding)) {
            SettingsItemButton(
                onClick = onLogOutPressed,
                leading = {
                    Icon(Icons.AutoMirrored.Default.ExitToApp, contentDescription = null)
                },
                supporting = {
                    Text("Go to log in screen")
                }
            ) {
                Text("Log out")
            }
        }
    }
}

@Composable
fun SettingsItemButton(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headline: @Composable () -> Unit,
) {
    HorizontalDivider()
    ListItem(
        headlineContent = headline,
        supportingContent = supporting,
        leadingContent = leading,
        modifier = modifier.clickable {
            onClick()
        }
    )
    HorizontalDivider()

}

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
fun SettingsPreview() {
    ClayCoinTheme {
        Scaffold { innerPadding ->
            SettingsScreen({}, modifier = Modifier.padding(innerPadding))
        }
    }
}
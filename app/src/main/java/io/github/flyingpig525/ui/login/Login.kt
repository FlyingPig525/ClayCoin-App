package io.github.flyingpig525.ui.login

import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.flyingpig525.Application
import io.github.flyingpig525.MainActivity
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.ui.chat.cornerSize
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlinx.coroutines.runBlocking

@Composable
fun LoginScreen(
    windowBounds: Rect,
    modifier: Modifier = Modifier,
    userStorage: UserStorage = UserStorage(),
    chatStorage: ChatStorage = ChatStorage(),
    navController: NavHostController
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        Column(
            modifier = Modifier.onSizeChanged {
                cardSize = it
            }.scale(1.1f)
        ) {
            var usernameState by remember { mutableStateOf("") }
            var validUsername by remember { mutableStateOf(true) }
            var passwordState by remember { mutableStateOf("") }
            var confirmPasswordState by remember { mutableStateOf("") }
            var badOptionText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = usernameState,
                onValueChange = {
                    usernameState = it
                    if (it.length > 25) {
                        validUsername = false
                    } else {
                        validUsername = true
                    }
                },
                label = { Text("Username") },
                modifier = Modifier.padding(6.dp),
                isError = !validUsername
            )
            OutlinedTextField(
                value = passwordState,
                onValueChange = {
                    passwordState = it
                },
                label = { Text("Password") },
                modifier = Modifier.padding(6.dp),
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = confirmPasswordState,
                onValueChange = {
                    confirmPasswordState = it
                },
                label = { Text("Confirm Password") },
                modifier = Modifier.padding(6.dp),
                visualTransformation = PasswordVisualTransformation()
            )
            MultiChoiceSegmentedButtonRow {
                OutlinedButton(
                    onClick = { runBlocking {
                        if (passwordState != confirmPasswordState) {
                            badOptionText = "Passwords do not match"
                            return@runBlocking
                        }
                        if (usernameState.any { it == ' ' }) {
                            badOptionText = "Username cannot have spaces"
                            return@runBlocking
                        }
                        if (!validUsername) {
                            badOptionText = "Username cannot be more than 25 characters"
                        }
                        val token = userStorage.getNewToken(usernameState, passwordState)

                        if (token.isSuccess) {
                            userStorage.currentToken = token.getOrThrow()
                            MainActivity.instance.setContent {
                                navController.clearBackStack<String>()
                                Application(
                                    windowBounds,
                                    userStorage,
                                    chatStorage
                                )
                            }
                        } else {
                            when (token.exceptionOrNull()) {
                                is UserDoesNotExistException -> {
                                    badOptionText = "Account does not exist"
                                }
                                is InvalidUsernameOrPasswordException -> {
                                    badOptionText = "Username or password is invalid"
                                }
                            }
                        }
                    }},
                    modifier = Modifier.then(
                        with(LocalDensity.current) {
                            Modifier.width(cardSize.width.toDp() / 2)
                        }
                    ),
                    shape = ShapeDefaults.Medium.copy(topEnd = 0.cornerSize, bottomEnd = 0.cornerSize)
                ) {
                    Text("Login")
                }
                OutlinedButton(
                    onClick = { runBlocking {
                        if (passwordState != confirmPasswordState) {
                            badOptionText = "Passwords do not match"
                            return@runBlocking
                        }
                        if (usernameState.any { it == ' ' }) {
                            badOptionText = "Username cannot have spaces"
                            return@runBlocking
                        }
                        if (!validUsername) {
                            badOptionText = "Username cannot be more than 25 characters"
                        }
                        val token = userStorage.createNewUser(usernameState, passwordState)
                        if (token.isSuccess) {
                            userStorage.currentToken = token.getOrThrow()
                            MainActivity.instance.setContent {
                                navController.clearBackStack<String>()
                                Application(
                                    windowBounds,
                                    userStorage,
                                    chatStorage
                                )
                            }
                        } else {
                            badOptionText = "Account already exists"
                        }
                    }},
                    modifier = Modifier.then(
                        with(LocalDensity.current) {
                            Modifier.width(cardSize.width.toDp() / 2)
                        }
                    ),
                    shape = ShapeDefaults.Medium.copy(topStart = 0.cornerSize, bottomStart = 0.cornerSize)
                ) {
                    Text("Sign Up")
                }
            }
            if (badOptionText != "") {
                Text(
                    badOptionText,
                    color = (
                        if (isSystemInDarkTheme())
                            dynamicDarkColorScheme(LocalContext.current)
                        else
                            dynamicLightColorScheme(LocalContext.current)
                    ).error
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
@Preview(showSystemUi = true, showBackground = true, device = "id:pixel_7",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(showSystemUi = true, showBackground = true, device = "id:pixel_7",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun LoginScreenPreview() {
    ClayCoinTheme {
        Scaffold { innerPadding ->
            val navController = rememberNavController()
            LoginScreen(
                windowBounds =
                    LocalContext.current.getSystemService<WindowManager>()!!.currentWindowMetrics.bounds,
                modifier = Modifier.padding(innerPadding),
                navController = navController
            )
        }
    }
}
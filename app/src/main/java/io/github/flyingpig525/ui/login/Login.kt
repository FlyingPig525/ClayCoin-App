package io.github.flyingpig525.ui.login

import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.flyingpig525.Application
import io.github.flyingpig525.MainActivity
import io.github.flyingpig525.Page
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.getColorScheme
import io.github.flyingpig525.ui.chat.cornerSize
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.sin

// this composable is a cluster fuck
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    userStorage: UserStorage = UserStorage(),
    navController: NavHostController
) {
    var loading by remember { mutableStateOf(false) }
    if (loading) {
        Dialog(onDismissRequest = {}) {
            CircularProgressIndicator()
        }
    }
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var cardSize by remember { mutableStateOf(IntSize.Zero) }
        Column(
            modifier = Modifier.onSizeChanged {
                cardSize = it
            }.scale(1.1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var usernameState by remember { mutableStateOf("") }
            var validUsername by remember { mutableStateOf(true) }
            var passwordState by remember { mutableStateOf("") }
            var confirmPasswordState by remember { mutableStateOf("") }
            var badOptionText by remember { mutableStateOf(ErrorText.Empty) }
            val cScope = rememberCoroutineScope()
            OutlinedTextField(
                value = usernameState,
                onValueChange = { it: String ->
                    usernameState = it
                    if (it.length > 25) {
                        validUsername = false
                        badOptionText = ErrorText.TooLong
                    } else if (usernameState.any { it.isWhitespace() }) {
                        validUsername = false
                        badOptionText = ErrorText.Spaces
                    } else {
                        validUsername = true
                        if (badOptionText == ErrorText.Spaces || badOptionText == ErrorText.TooLong) {
                            badOptionText = ErrorText.Empty
                        }
                    }
                },
                label = { Text("Username") },
                modifier = Modifier.padding(6.dp).width(TextFieldDefaults.MinWidth),
                isError = !validUsername,
                singleLine = true,
            )
            OutlinedTextField(
                value = passwordState,
                onValueChange = {
                    passwordState = it
                },
                label = { Text("Password") },
                modifier = Modifier.padding(6.dp).width(TextFieldDefaults.MinWidth),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                value = confirmPasswordState,
                onValueChange = {
                    confirmPasswordState = it
                },
                label = { Text("Confirm Password") },
                modifier = Modifier.padding(6.dp).width(TextFieldDefaults.MinWidth),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            MultiChoiceSegmentedButtonRow(
                modifier = Modifier
                    .width(TextFieldDefaults.MinWidth)
                    .padding(vertical = 6.dp)
                    .height(TextFieldDefaults.MinHeight)
            ) {
                OutlinedButton(
                    onClick = {
                        loading = true
                        cScope.launch {
                            if (!validUsername) {
                                return@launch
                            }
                            if (passwordState != confirmPasswordState) {
                                badOptionText = ErrorText.PasswordsDontMatch
                                return@launch
                            }
                            if (usernameState.any { it == ' ' }) {
                                badOptionText = ErrorText.Spaces
                                return@launch
                            }
                            val token = userStorage.getNewToken(usernameState, passwordState)

                            if (token.isSuccess) {
                                userStorage.currentToken = token.getOrThrow()
                                navController.navigate(Page.dashboard)
                            } else {
                                when (token.exceptionOrNull()) {
                                    is UserDoesNotExistException -> {
                                        badOptionText = ErrorText.AccountDoesNotExist
                                    }

                                    is InvalidUsernameOrPasswordException -> {
                                        badOptionText = ErrorText.Incorrect
                                    }
                                }
                            }
                        }.invokeOnCompletion {
                            loading = false
                        }
                    },
                    modifier = Modifier.then(
                        with(LocalDensity.current) {
                            Modifier.width(cardSize.width.toDp() / 2)
                        }
                    ).fillMaxHeight(),
                    shape = ShapeDefaults.ExtraSmall.copy(topEnd = 0.cornerSize, bottomEnd = 0.cornerSize)
                ) {
                    Text("Login")
                }
                OutlinedButton(
                    onClick = {
                        loading = true
                        cScope.launch {
                            if (passwordState != confirmPasswordState) {
                                badOptionText = ErrorText.PasswordsDontMatch
                                return@launch
                            }
                            if (usernameState.any { it == ' ' }) {
                                badOptionText = ErrorText.Spaces
                                return@launch
                            }
                            if (!validUsername) {
                                badOptionText = ErrorText.TooLong
                                return@launch
                            }
                            val token = userStorage.createNewUser(usernameState, passwordState)
                            if (token.isSuccess) {
                                userStorage.currentToken = token.getOrThrow()
                                navController.navigate(Page.dashboard)
                            } else {
                                badOptionText = ErrorText.AccountExists
                            }
                        }.invokeOnCompletion {
                            loading = false
                        }
                    },
                    modifier = Modifier.then(
                        with(LocalDensity.current) {
                            Modifier.width(cardSize.width.toDp() / 2)
                        }
                    ).fillMaxHeight(),
                    shape = ShapeDefaults.ExtraSmall.copy(topStart = 0.cornerSize, bottomStart = 0.cornerSize)
                ) {
                    Text("Sign Up")
                }
            }
            if (badOptionText != ErrorText.Empty) {
                Text(
                    badOptionText.txt,
                    color = getColorScheme().error
                )
            }
        }
    }
}

enum class ErrorText(val txt: String) {
    AccountExists("Account already exists"),
    TooLong("Username cannot be longer than 25 characters"),
    Spaces("Username cannot include whitespace characters"),
    PasswordsDontMatch("Passwords do not match"),
    AccountDoesNotExist("Account does not exist"),
    Incorrect("Username or password is incorrect"),
    Empty("")
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
                modifier = Modifier.padding(innerPadding),
                navController = navController
            )
        }
    }
}
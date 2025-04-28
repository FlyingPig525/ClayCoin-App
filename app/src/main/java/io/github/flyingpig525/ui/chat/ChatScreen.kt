package io.github.flyingpig525.ui.chat

import android.annotation.SuppressLint
import android.app.Service
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.chat.ChatMessage
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.std.render
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import io.github.flyingpig525.wsCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

val Int.cornerSize: CornerSize get() = CornerSize(this)

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun ChatScreen(
    modifier: Modifier = Modifier,
    userStorage: UserStorage = UserStorage(),
    chatStorage: ChatStorage = ChatStorage()
) {
    val messages by chatStorage.messages.collectAsStateWithLifecycle()
    val usernames by chatStorage.usernames.collectAsStateWithLifecycle()
    LaunchedEffect("get messages") {
        if (messages.isEmpty()) {
            wsCoroutineScope.launch {
                chatStorage.getMessages()
                chatStorage.processMessageUsernames()
            }
        }
    }
    Scaffold(
        bottomBar = {
            TextInput { state ->
                if (userStorage.currentToken != null) {
                    runBlocking {
                        launch {
                            chatStorage.sendNewMessage(
                                userStorage.currentToken!!,
                                state
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            reverseLayout = true,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(messages.reversed()) { message ->
                if (message.userId == userStorage.currentId) {
                    MessageBox(
                        message,
                        "You",
                        Alignment.End,
                        elevated = true,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                } else {
                    var username = usernames[message.userId]
                    if (username == null) {
                        Log.e("Chat Screen", "Username for id ${message.userId} is null")
                    }
                    MessageBox(
                        message,
                        username ?: "Username not found",
                        Alignment.Start,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun MessageBox(
    message: ChatMessage,
    name: String,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    elevated: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            LocalDateTime.ofInstant(
                message.time.toJavaInstant(),
                ZoneId.systemDefault()
            ).render(),
            fontSize = LocalTextStyle.current.fontSize.div(1.4),
            color = LocalContentColor.current.copy(alpha = 0.5f),
            modifier = modifier.align(alignment)
        )
        val content: @Composable ColumnScope.() -> Unit = {
            Text(
                name,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 6.dp).align(alignment),
                fontSize = LocalTextStyle.current.fontSize.div(1.2)
            )
            Text(
                message.message.trim(),
                modifier = Modifier
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .align(alignment)
            )
        }
        if (elevated) {
            OutlinedCard(
                shape = ShapeDefaults.Small,
                modifier = modifier.align(Alignment.End)
            ) {
                content()
            }
        } else {
            Card(
                shape = ShapeDefaults.Small,
                modifier = modifier.align(alignment)
            ) {
                content()
            }
        }
    }
}

@Composable
fun TextInput(onSend: (text: String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        var state by remember { mutableStateOf("") }
        var sizeState by remember { mutableStateOf(IntSize(0, 0)) }
        val scrollState = rememberScrollState()
        OutlinedTextField(
            value = state,
            onValueChange = { str: String ->
                state = str
            },
            shape = ShapeDefaults.Medium.copy(
                bottomEnd = CornerSize(0),
                bottomStart = CornerSize(0),
                topEnd = CornerSize(0)
            ),
            modifier = Modifier
                .fillMaxWidth(0.79f)
                .verticalScroll(scrollState)
                .onSizeChanged {
                    sizeState = it
                },
            placeholder = { Text("Start Typing") },
            maxLines = 2
        )
        Button(
            onClick = {
                onSend(state)
                state = ""
            },
            shape = ShapeDefaults.Medium.copy(
                bottomStart = CornerSize(0),
                bottomEnd = CornerSize(0),
                topStart = 0.cornerSize
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(
                    minHeight = OutlinedTextFieldDefaults.MinHeight
                )
                .then(
                    if (sizeState.width != 0) {
                        with(LocalDensity.current) {
                            Modifier.size(
                                sizeState.width.toDp(),
                                sizeState.height.toDp()
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Text("Send")
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    name = "Message box preview dark", showSystemUi = true, showBackground = true,
    device = "id:pixel_7"
)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    name = "message box preview light", showSystemUi = true, showBackground = true,
    device = "id:pixel_7"
)
@Composable
fun MessageBoxPreview() {
    ClayCoinTheme {
        val usernames = mapOf(0 to "0", 1 to "1", 2 to "2", 3 to "3", 4 to "4")
        val messages = buildList(10) {
            for (i in 0 until 10) {
                add(ChatMessage((0..4).random(), i, "$i".repeat(i + 1), Clock.System.now()))
            }
        }
        val currentId = (0..4).random()
        Scaffold(
            bottomBar = {
                TextInput {  }
            },
            modifier = Modifier
        ) { innerPadding ->
            LazyColumn(
                reverseLayout = true,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    .fillMaxWidth().fillMaxHeight(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(messages.reversed()) { message ->
                    if (message.userId == currentId) {
                        MessageBox(message, "You", Alignment.End, modifier = Modifier.padding(horizontal = 6.dp))
                    } else {
                        var username = usernames[message.userId]
                        if (username == null) {
                            Log.e("Chat Screen", "Username for id ${message.userId} is null")
                        }
                        MessageBox(
                            message,
                            username ?: "Username not found",
                            Alignment.Start,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
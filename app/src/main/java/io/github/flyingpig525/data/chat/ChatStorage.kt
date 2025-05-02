package io.github.flyingpig525.data.chat

import android.util.Log
import io.github.flyingpig525.HTTP_SERVER_IP
import io.github.flyingpig525.WS_SERVER_IP
import io.github.flyingpig525.data.UserStorage
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.ktor.json
import io.github.flyingpig525.wsCoroutineScope
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ChatStorage {
    private val _messages: MutableStateFlow<List<ChatMessage>> = MutableStateFlow(listOf())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _usernames: MutableStateFlow<Map<Int, String>> = MutableStateFlow(mapOf())
    val usernames: StateFlow<Map<Int, String>> = _usernames.asStateFlow()
    private var ws: WebSocketSession = runBlocking {
        Log.i("Chat WebSocket", "Socket connecting")
        UserStorage.httpClient.webSocketSession("$WS_SERVER_IP/")
    }
        get() {
            if (field.isActive) return field
            field = runBlocking {
                Log.d("Chat WebSocket", "Reconnecting websocket")
                UserStorage.httpClient.webSocketSession("$WS_SERVER_IP/")
            }
            wsCoroutineScope.launch {
                receiveMessage()
            }
            return field
        }

    fun newMessage(message: ChatMessage) {
        _messages.value += message
    }

    suspend fun sendNewMessage(token: Token, content: String) {
        Log.d("Chat WebSocket", "Sending message with content: $content")
        ws.send(Json.encodeToString(MessageContainer(token, content.trim())))
    }

    suspend fun receiveMessage() {
        try {
            UserStorage.httpClient.webSocket("$WS_SERVER_IP/") {
                ws.incoming.consumeEach {
                    if (it is Frame.Text) try {
                        val txt = it.readText()
                        val json = Json.decodeFromString<ChatMessage>(txt)
                        newMessage(json)
                        if (!_usernames.value.containsKey(json.userId)) {
                            val username = getIdUsername(json.userId)
                            if (username != null) {
                                _usernames.value += json.userId to username
                            }
                        }
                    } catch (e: SerializationException) {
                        Log.e("Chat WebSocket", "Something went wrong parsing chat message json", e)
                    } catch (e: Exception) {
                        Log.e("Chat WebSocket", "Something went wrong in the chat message receiver", e)
                    }
                }
            }
        } catch (e: CancellationException) {
            if (e.message == "App paused") {
                Log.i("Chat WebSocket", "Socket disconnected")
                return
            }
            Log.e("Chat WebSocket", "Something went wrong in the chat message receiver", e)
        } catch (e: Exception) {
            Log.e("Chat WebSocket", "Something went wrong in the chat message receiver", e)
        }
    }

    suspend fun getMessages() {
        try {
            val get = UserStorage.httpClient.get("$HTTP_SERVER_IP/chat")
            println(get)
            _messages.value = get.json<List<ChatMessage>>()
        } catch (e: Exception) {
            Log.e("io.github.flyingpig525", "Something went wrong during initial message getting", e)
        }
    }

    suspend fun processMessageUsernames() {
        try {
            for (message in messages.value) {
                if (_usernames.value.containsKey(message.userId)) continue
                val username = getIdUsername(message.userId)
                if (username == null) continue
                _usernames.value += message.userId to username
            }
        } catch (e: Exception) {
            Log.e("ChatStorage", "Something went wrong while processing message usernames", e)
        }
    }

    companion object {
        suspend fun getIdUsername(id: Int): String? {
            Log.d("ChatStorage", "Requesting username for id $id")
            val username = UserStorage.httpClient.get(
                "$HTTP_SERVER_IP/users/$id/username"
            ).bodyAsText()
            if (username.isBlank()) {
                Log.e("ChatStorage", "Received username is blank")
                return null
            }
            Log.d("ChatStorage", "Username for id $id is $username")
            return username
        }
    }

    suspend fun closeWs() {
        ws.close(CloseReason(CloseReason.Codes.NORMAL, "Application quit"))
    }
}
package io.github.flyingpig525

import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.chat.ChatMessage
import io.github.flyingpig525.data.chat.MessageContainer
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.sql.*

val messagesToSend = MutableSharedFlow<ChatMessage>(replay = 50)
private val sharedFlow = messagesToSend.asSharedFlow()

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket {
            var job: Job? = null
            var cancel = false
            try {
                job = launch {
                    sharedFlow.collect {
                        log.info("broadcasting message $it")
                        send(Json.encodeToString(it))
                        if (cancel) {
                            cancel()
                        }
                    }
                }
                runCatching {
                    incoming.consumeEach {
                        if (it is Frame.Text) {
                            val txt = it.readText()
                            val json = Json.decodeFromString<MessageContainer>(txt)
                            log.info("recieved message $json")
                            chatService.addMessage(json.content, json.token)
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("An exception occurred in the websocket", e)
            } finally {
                job?.cancel("Socket connection ended")
                cancel = true
                log.info("Connection to ${call.request.local.remoteAddress}:${call.request.local.remotePort} closed")
            }
        }
    }
}

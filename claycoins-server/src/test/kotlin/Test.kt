import io.github.flyingpig525.data.auth.AuthModel
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.chat.ChatMessage
import io.github.flyingpig525.data.chat.MessageContainer
import io.github.flyingpig525.data.ktor.body
import io.github.flyingpig525.data.ktor.json
import io.github.flyingpig525.main
import io.github.flyingpig525.module
import io.github.flyingpig525.userService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test

class ServerTest {
    @Test
    fun testPostUser() = test { client ->
        val tokens = mutableListOf<Token>()
        for (i in 0..0) {
            val token = client.post("/users") {
                body(AuthModel("hello$i", "pass$i"))
            }.json<Token>()
            tokens += token
        }
        val token = tokens[0]
        val id = client.get("/users/id") {
            parameter("token", token.hashedToken)
        }
        val newToken = client.patch("/users") {
            body(AuthModel("hello0", "pass0"))
        }.json<Token>()
        println(newToken)
//        val sessions = MutableList(20) {
//            client.webSocketSession {  }
//        }
//        for (session in sessions.drop(1)) {
//            sessions[0].send(Json.encodeToString(MessageContainer(newToken, "content")))
//            session.close()
//            delay(100)
//        }
    }

    @Test
    fun testWebsocketClosing() = MassUserContext.test { client, ctx ->
        val token = ctx.randomUser().token

        val sessions = MutableList(20) {
            client.webSocketSession { }
        }
        for (session in sessions.drop(1)) {
            sessions[0].send(Json.encodeToString(MessageContainer(token, "content")))
            session.close()
            delay(100)
        }
    }

    @Test
    fun testUserData() = MassUserContext.test(userCount = 1) { client, ctx ->
        delay(10000)
        val user = ctx.randomUser()
        val req = client.get("/users/id") {
            parameter("token", user.token.hashedToken)
        }.body<Int>()
        println(req)
        val data = client.get("/users/$req")
        println(data)
        println(data.bodyAsText())
    }

    fun test(lambda: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) = testApplication {
        File("./h2-database.mv.db").apply {
            if (exists()) {
                delete()
            }
        }
        application {
            module()
        }
        install(ContentNegotiation) {
            json()
        }
        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
        lambda(client)
    }
}

class MassUserContext(private val client: HttpClient) {
    private val _users: MutableList<User> = mutableListOf()
    val users: List<User> get() = _users.toList()

    fun randomUser(): User = _users.random()

    private suspend fun populate(userCount: Int) {
        for (i in 0 until userCount) {
            val pass = pass()
            val name = name()
            val token = client.post("/users") {
                body(AuthModel(name, pass))
            }.json<Token>()
            _users += User(token, name, pass)
        }
    }

    data class User(val token: Token, val username: String, val pass: String)

    companion object {
        fun test(
            userCount: Int = 20,
            lambda: suspend ApplicationTestBuilder.(HttpClient, ctx: MassUserContext) -> Unit
        ) = testApplication {
            File("./h2-database.mv.db").apply {
                if (exists()) {
                    delete()
                }
            }
            application {
                module()
            }
            install(ContentNegotiation) {
                json()
            }
            val client = createClient {
                install(io.ktor.client.plugins.websocket.WebSockets)
            }
            val ctx = MassUserContext(client)
            ctx.populate(userCount)
            lambda(client, ctx)
        }
    }
}

val passwordSections = listOf("123", "password", "this", "is", "a", "pass", "secret", "dont", "look")

fun pass(): String {
    var p = ""
    for (i in 0..4) {
        p += "${passwordSections.random()}_"
    }
    return p.dropLast(1)
}

fun name(): String = "${firstNames.random()} ${ALPHABET.random()}. ${lastNames.random()}"

const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXWZ"

val firstNames = listOf(
    "Jake",
    "John",
    "Adam",
    "Alex",
    "Patrick",
    "Pat",
    "Jack",
    "Alexander",
    "Parker",
    "Ronald",
    "Trevor",
    "Dennis",
    "Dean"
)

// i know absolutely no last names
val lastNames = listOf(
    "Moore",
    "Morrison",
    "Washington",
    "Hamilton",
    "Jackson",
    "McDonald"
)
package io.github.flyingpig525.data

import android.util.Log
import io.github.flyingpig525.HTTP_SERVER_IP
import io.github.flyingpig525.data.auth.AuthModel
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.UserAlreadyExistsException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.ktor.body
import io.github.flyingpig525.data.ktor.json
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking

class UserStorage {
    var currentToken: Token? = null
        set(value) {
            field = value
            if (value != null) {
                runBlocking {
                    setId()
                }
            } else {
                currentId = null
            }
        }
    var currentId: Int? = null
        private set

    private suspend fun setId() {
        // should never happen
        if (currentToken == null) return
        val req = httpClient.get("$HTTP_SERVER_IP/users/id") {
            parameter("token", currentToken!!.hashedToken)
        }
        if (req.status != HttpStatusCode.NotFound) {
            currentId = req.body<Int>()
        } else {
            Log.e("io.github.flyingpig525", "Id request status = NotFound")
        }
    }
    suspend fun createNewUser(username: String, password: String): Result<Token> {
        val exists = usernameExists(username)
        if (exists) return Result.failure(UserAlreadyExistsException())
        val auth = AuthModel(username, password)
        val token = httpClient.post("$HTTP_SERVER_IP/users") {
            body(auth)
        }
        if (token.status == HttpStatusCode.Conflict) {
            return Result.failure(UserAlreadyExistsException())
        }
        try {
            return Result.success(token.json())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Result.failure(UnknownError())
    }

    suspend fun getNewToken(username: String, password: String): Result<Token> {
        val exists = usernameExists(username)
        if (!exists) return Result.failure(UserDoesNotExistException())
        val auth = AuthModel(username, password)
        println(auth)
        val tokenReq = httpClient.patch("$HTTP_SERVER_IP/users") {
            body(auth)
        }
        when (tokenReq.status) {
            HttpStatusCode.NotFound -> return Result.failure(UserDoesNotExistException())
            HttpStatusCode.Unauthorized -> return Result.failure(InvalidUsernameOrPasswordException())
            HttpStatusCode.OK -> return Result.success(tokenReq.json<Token>())
            else -> throw UnknownError("Unknown request status ${tokenReq.status}")
        }
    }

    suspend fun usernameExists(username: String): Boolean {
        return httpClient.get("$HTTP_SERVER_IP/users/$username/exists") {
            contentType(ContentType.Text.Plain)
            setBody(username)
        }.status == HttpStatusCode.Conflict
    }

    companion object {
        val httpClient = HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                webSocketFactory
            }
        }
    }
}
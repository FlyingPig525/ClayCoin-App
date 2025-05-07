package io.github.flyingpig525.data

import android.util.Log
import io.github.flyingpig525.HTTP_SERVER_IP
import io.github.flyingpig525.data.auth.AuthModel
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.UserAlreadyExistsException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.ChatStorage
import io.github.flyingpig525.data.exception.NoIdException
import io.github.flyingpig525.data.ktor.CallResult
import io.github.flyingpig525.data.ktor.body
import io.github.flyingpig525.data.ktor.json
import io.github.flyingpig525.data.user.UserCurrencies
import io.github.flyingpig525.data.user.UserData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class UserStorage {
    var currentToken: Token? = null
        set(value) {
            field = value
            if (value != null) {
                runBlocking {
                    setMeta()
                }
            } else {
                currentId = null
                data = null
            }
        }
    var currentId: Int? = null
        private set
    var data: UserData? = null

    private suspend fun setMeta() {
        // should never happen
        if (currentToken == null) return
        val req = httpClient.get("$HTTP_SERVER_IP/users/id") {
            parameter("token", currentToken!!.hashedToken)
        }
        if (req.status == HttpStatusCode.NotFound) {
            Log.e("User Storage", "Id request status = NotFound")
            return
        }
        currentId = req.body<Int>()
        val userData = getUserData(currentId!!)
        if (userData.isFailure) return
        data = userData.getOrThrow()
    }

    suspend fun updateData(): Result<UserData> {
        if (currentId == null) return Result.failure(NoIdException())
        val userData = getUserData(currentId!!)
        if (userData.isFailure) return userData
        data = userData.getOrThrow()
        return Result.success(data!!)
    }

    suspend fun createNewUser(username: String, password: String): Result<Token> {
        val exists = usernameExists(username)
        if (exists) return Result.failure(UserAlreadyExistsException())
        val auth = AuthModel(username, password)
        val token = httpClient.post("$HTTP_SERVER_IP/users") {
            body(auth)
        }
        return CallResult.fromResponseKt(token)
    }

    suspend fun getNewToken(username: String, password: String): Result<Token> {
        val exists = usernameExists(username)
        if (!exists) return Result.failure(UserDoesNotExistException())
        val auth = AuthModel(username, password)
        println(auth)
        val tokenReq = httpClient.patch("$HTTP_SERVER_IP/users") {
            body(auth)
        }
        return CallResult.fromResponseKt(tokenReq)
    }

    fun logOut() {
        currentToken = null
    }

    companion object {
        val httpClient = HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                webSocketFactory
            }
        }

        suspend fun usernameExists(username: String): Boolean {
            return httpClient.get("$HTTP_SERVER_IP/users/$username/exists")
                .status == HttpStatusCode.Conflict
        }

        suspend fun getUserData(userId: Int): Result<UserData> {
            val req = httpClient.get("$HTTP_SERVER_IP/users/$userId")
            println(req)
            println(req.bodyAsText())
            return CallResult.fromResponseKt(req)
        }
    }
}
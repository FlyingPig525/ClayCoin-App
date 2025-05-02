package io.github.flyingpig525

import io.github.flyingpig525.data.auth.AuthModel
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.TokenNotFoundException
import io.github.flyingpig525.data.auth.exception.UserAlreadyExistsException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.MessageContainer
import io.github.flyingpig525.data.ktor.collect
import io.github.flyingpig525.data.ktor.json
import io.github.flyingpig525.data.user.UserData
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*

lateinit var userService: UserService
lateinit var chatService: ChatService

fun Application.configureDatabases() {
    val database = Database.connect(
        url = "jdbc:h2:./h2-database;DB_CLOSE_DELAY=-1;FILE_LOCK=NO",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    userService = UserService(database)
    chatService = ChatService(database)
    routing {
        get("/") {
            call.respond("Hello")
        }
        accept(ContentType.Application.Json) {

            post("/users") {
                val user = call.collect<AuthModel>()
                val token = userService.create(user).apply {
                    if (isFailure) {
                        handleException()
                        return@post
                    }
                }.getOrThrow()
                call.respondText(ContentType.Application.Json, HttpStatusCode.Created) { Json.encodeToString(token) }
                log.info("created new user $user")
            }

            patch("/users") {
                val auth = call.collect<AuthModel>()
                val newToken = userService.getTokenWithAuth(auth).apply {
                    if (isFailure) {
                        handleException()
                        return@patch
                    }
                }.getOrThrow()
                log.info("Token retrieved for user ${auth.username}: $newToken")
                call.respondText(ContentType.Application.Json, HttpStatusCode.OK) {
                    Json.encodeToString(newToken)
                }
            }
            post("/chat") {
                val (user, message) = call.collect<MessageContainer>()
                if (!userService.authenticateUser(user)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val chatMessage = chatService.addMessage(message, user)
                call.respond(HttpStatusCode.Created)
                log.info("created new chat message $chatMessage")
            }
        }

        route("/users") {

            get("/id") {
                val tokenStr = call.request.queryParameters["token"]
                if (tokenStr == null) {
                    call.respondText(status = HttpStatusCode.BadRequest) {
                        "Parameter \"token\" not found"
                    }
                    return@get
                }
                val token = Token(tokenStr)
                val id = userService.getTokenContent(token).apply {
                    if (isFailure) {
                        handleException()
                        return@get
                    }
                }.getOrThrow().userId
                log.info("id for token $tokenStr is $id")
                call.respondText(status = HttpStatusCode.OK) { id.toString() }
            }
            get("/{username}/exists") {
                val username = call.parameters["username"]!!
                val exists = userService.exists(username)
                if (exists) {
                    call.respondText(status = HttpStatusCode.Conflict) { "Username already exists" }
                } else {
                    call.respondText(status = HttpStatusCode.OK) { "Username does not exist" }
                }
                log.info("User $username exists: $exists")
            }

            get("/{id}/username") {
                try {
                    val id = call.parameters["id"]!!.toInt()
                    val user = userService.getById(id).apply {
                        if (isFailure) {
                            handleException()
                            return@get
                        }
                    }.getOrThrow()
                    log.info("username for id $id is ${user.username}")
                    call.respondText(ContentType.Text.Plain, HttpStatusCode.OK) {
                        user.username
                    }
                } catch (e: NumberFormatException) {
                    call.respondText(ContentType.Text.Plain, HttpStatusCode.BadRequest) {
                        Bad.IdPath.m
                    }
                }
            }
            get("/{id}/currencies") {
                try {
                    val id = call.pathParameters["id"]?.toInt()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    val res = userService.getCurrencies(id).apply {
                        if (isFailure) {
                            handleException()
                            return@get
                        }
                    }.getOrThrow()
                    call.json(res, HttpStatusCode.OK)
                } catch (e: NumberFormatException) {
                    call.respondText(
                        Bad.IdPath.m,
                        status = HttpStatusCode.BadRequest
                    )
                    return@get
                }
            }
            /**
             * @throws UserDoesNotExistException
             */
            get("/{id}") {
                try {
                    val id = call.pathParameters["id"]?.toInt()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    val currencies = userService.updateCoins(id).apply {
                        if (isFailure) {
                            handleException()
                            return@get
                        }
                    }.getOrThrow()
                    val container = userService.getById(id).apply {
                        if (isFailure) {
                            handleException()
                            return@get
                        }
                    }.getOrThrow()
                    val data = UserData(container.username, id, currencies, container.admin)
                    call.json(data, HttpStatusCode.OK)
                } catch (e: NumberFormatException) {
                    call.respondText(Bad.IdPath.m, status = HttpStatusCode.BadRequest)
                    return@get
                } catch (e: Throwable) {
                    log.error("An error occurred in /users/{id}", e)
                }
            }
        }

        get("/chat/{offset}/{limit}") {
            try {
                val offset = call.parameters["offset"]?.toInt()
                val limit = call.parameters["limit"]?.toInt()
                if (offset == null || limit == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val messages = chatService.getMessages(offset, limit)
                log.info("chat messages $offset through $limit have been requested")
                call.respond(HttpStatusCode.OK, messages)
            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, "Offset or limit are not integers")
            }
        }

        get("/chat") {
            val messages = chatService.getMessages(50)
            log.info("the 50 most recent messages have been requested")
            log.info(Json.encodeToString(messages))
            call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { Json.encodeToString(messages) }
        }

    }
}


enum class Bad(val m: String) {
    IdPath("Path parameter id must be an integer")
}
package io.github.flyingpig525

import io.github.flyingpig525.data.UserDataContainer
import io.github.flyingpig525.data.auth.AuthModel
import io.github.flyingpig525.data.auth.Token
import io.github.flyingpig525.data.auth.TokenContent
import io.github.flyingpig525.data.auth.exception.InvalidUsernameOrPasswordException
import io.github.flyingpig525.data.auth.exception.TokenNotFoundException
import io.github.flyingpig525.data.auth.exception.UserAlreadyExistsException
import io.github.flyingpig525.data.auth.exception.UserDoesNotExistException
import io.github.flyingpig525.data.chat.ChatMessage
import kotlinx.coroutines.Dispatchers
import org.h2.engine.User
import org.h2.security.SHA256
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class UserService(database: Database) {
    object Users : Table() {
        val id = integer("id").autoIncrement()
        val username = varchar("username", length = 50)
        val hashedPassword = varchar("hash", length = 64)
        val admin = bool("admin").default(false)
        val salt = byte("salt")

        override val primaryKey = PrimaryKey(id)
    }

    object Tokens : Table() {
        val token = varchar("token", 64)
        val userId = integer("userId")
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users, Tokens)
        }
    }

    suspend fun addToken(t: Token, user: Int): Unit = dbQuery {
        Tokens.insert {
            it[token] = t.hashedToken
            it[userId] = user
        }
    }

    /**
     * @throws UserAlreadyExistsException
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun create(user: AuthModel): Result<Token> = dbQuery {
        if (exists(user)) return@dbQuery Result.failure(UserAlreadyExistsException())
        val salt = (Byte.MIN_VALUE..Byte.MAX_VALUE).random().toByte()
        val hashedPass = getHash(user.password, salt)
        val id = Users.insert {
            it[username] = user.username
            it[hashedPassword] = hashedPass
            it[this.salt] = salt
            it[admin] = false
        }[Users.id]
        val token = token(user.username, id)
        addToken(token, id)
        Result.success(token)
    }

//    /**
//     * @throws UserDoesNotExistException
//     */
//    @OptIn(ExperimentalStdlibApi::class)
//    suspend fun update(id: Int, user: AuthModel): Result<Token> = dbQuery {
//        if (!exists(user)) return@dbQuery Result.failure(UserDoesNotExistException())
//        val salt = (Byte.MIN_VALUE..Byte.MAX_VALUE).random().toByte()
//        val hashedPass = SHA256.getHashWithSalt(user.password.toByteArray(), byteArrayOf(salt)).toHexString()
//        val id = Users.update({ Users.id eq id }) {
//            it[username] = user.username
//            it[hashedPassword] = hashedPass
//            it[this.salt] = salt
//        }
//        val token = tokens()
//        tokens.remove(tokens.filterValues { it.userId == id }.keys.toList()[0])
//        tokens[token] = TokenContent(user.username, id, hashedPass)
//        Result.success(token)
//    }

    /**
     * @throws UserDoesNotExistException
     * @throws InvalidUsernameOrPasswordException
     */
    suspend fun getTokenWithAuth(user: AuthModel): Result<Token> = dbQuery {
        if (!exists(user)) return@dbQuery Result.failure(UserDoesNotExistException())
        val res = Users.selectAll().where { Users.username eq user.username }.singleOrNull()!!
        val salt = res[Users.salt]
        val hash = getHash(user.password, salt)
        if (res[Users.hashedPassword] == hash) {
            val token = getToken(res[Users.id])
            if (token.isFailure) {
                return@dbQuery Result.failure(token.exceptionOrNull()!!)
            }
            return@dbQuery Result.success(token.getOrThrow())
        }
        Result.failure(InvalidUsernameOrPasswordException())
    }

    /**
     * @throws TokenNotFoundException
     */
    private suspend fun getToken(user: Int): Result<Token> = dbQuery {
        val res = Tokens.selectAll().where { Tokens.userId eq user }.singleOrNull()
        if (res == null) {
            return@dbQuery Result.failure(TokenNotFoundException())
        }
        return@dbQuery Result.success(Token(res[Tokens.token]))
    }

    /**
     * @throws TokenNotFoundException
     * @throws UserDoesNotExistException
     */
    suspend fun getTokenContent(t: Token): Result<TokenContent> = dbQuery {
        val res = Tokens.selectAll().where { Tokens.token eq t.hashedToken }.singleOrNull()
        if (res == null) {
            return@dbQuery Result.failure(TokenNotFoundException())
        }
        val user = Users.selectAll().where { Users.id eq res[Tokens.userId] }.singleOrNull()
        if (user == null) {
            return@dbQuery Result.failure(UserDoesNotExistException())
        }
        return@dbQuery Result.success(TokenContent(
            user[Users.username],
            user[Users.id],
            user[Users.hashedPassword],
            user[Users.admin]
        ))

    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id.eq(id) }
        }
    }

    suspend fun isAdmin(token: Token): Boolean = dbQuery {
        val content = getTokenContent(token).apply {
            if (isFailure) {
                exceptionOrNull()?.printStackTrace()
                return@dbQuery false
            }
        }.getOrThrow()
        Users.selectAll().where { Users.id eq content.userId }.map {
            it[Users.admin]
        }.singleOrNull() == true
    }

    suspend fun authenticateUser(token: Token): Boolean {
        val content = getTokenContent(token).apply {
            if (isFailure) return false
        }.getOrThrow()
        return dbQuery {
            Users.selectAll().where { Users.id eq content.userId }.map {
                it[Users.hashedPassword]
            }.singleOrNull() == content.hashedPass
        }
    }

    suspend fun getById(id: Int): Result<UserDataContainer> = dbQuery {
        val user = Users.selectAll()
            .where { Users.id eq id }
            .map {
                UserDataContainer(
                    it[Users.id],
                    it[Users.username],
                    it[Users.admin]
                )
            }.singleOrNull()
        if (user == null)
            return@dbQuery Result.failure(UserDoesNotExistException())
        return@dbQuery Result.success(user)
    }

    suspend fun exists(user: AuthModel): Boolean = exists(user.username)

    suspend fun exists(username: String): Boolean = dbQuery {
        Users.selectAll().where { Users.username eq username }.singleOrNull() != null
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getHash(password: String, salt: Byte) =
        SHA256.getHashWithSalt(password.toByteArray(), byteArrayOf(salt)).toHexString()

    @OptIn(ExperimentalStdlibApi::class)
    private fun token(username: String, id: Int) =
        Token(
            SHA256.getHash(
                "$username$id".toByteArray(),
                true
            ).toHexString()
        )
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

class ChatService(database: Database) {
    object Messages : Table() {
        val id = integer("id").autoIncrement()
        val userId = integer("userId")
        val content = varchar("content", length = 400)
        @OptIn(ExperimentalTime::class)
        val timestamp = long("timestamp").clientDefault { Clock.System.now().epochSeconds }
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Messages)
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun addMessage(message: String, token: Token): ChatMessage? {
        val user = userService.getTokenContent(token).apply {
            if (isFailure) {
                exposedLogger.error("an error occurred when adding token: $token's message")
                exceptionOrNull()?.printStackTrace()
                return null
            }
        }.getOrThrow()
        val message = dbQuery {
            Messages.insert {
                it[userId] = user.userId
                it[content] = message
            }.resultedValues!!.map {
                ChatMessage(
                    it[Messages.userId],
                    it[Messages.id],
                    it[Messages.content],
                    Instant.fromEpochSeconds(it[Messages.timestamp])
                )
            }.single()
        }
        messagesToSend.emit(message)
        return message
    }

    suspend fun removeMessage(user: TokenContent, messageId: Int): Boolean {
        if (user.admin) {
            return dbQuery {
                Messages.deleteWhere { id.eq(messageId) } > 0
            }
        }
        return false
    }

    @OptIn(ExperimentalTime::class)
    suspend fun getMessages(start: Int, end: Int): List<ChatMessage> {
        return dbQuery {
            Messages
                .selectAll()
                .offset(start.toLong())
                .limit(end - start)
                .map {
                    ChatMessage(
                        it[Messages.userId],
                        it[Messages.id],
                        it[Messages.content],
                        Instant.fromEpochSeconds(it[Messages.timestamp])
                    )
                }
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun getMessages(count: Int): List<ChatMessage> {
        return dbQuery {
            Messages.selectAll()
                .limit(count)
                .map {
                    ChatMessage(
                        it[Messages.userId],
                        it[Messages.id],
                        it[Messages.content],
                        Instant.fromEpochSeconds(it[Messages.timestamp])
                    )
                }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

}

fun intToByteArray(i: Int): ByteArray {
    val buffer = ByteArray(4)
    buffer[0] = (i shr 0).toByte()
    buffer[1] = (i shr 8).toByte()
    buffer[2] = (i shr 16).toByte()
    buffer[3] = (i shr 24).toByte()
    return buffer
}

fun Int.toByteArray(): ByteArray = intToByteArray(this)
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
import io.github.flyingpig525.data.chat.USER_CHAT_COOLDOWN_MS
import io.github.flyingpig525.data.ktor.CallResult
import io.github.flyingpig525.data.user.CLAYCOIN_INCREMENT_MS
import io.github.flyingpig525.data.user.UserCurrencies
import kotlinx.coroutines.Dispatchers
import org.h2.security.SHA256
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

const val SHINER_INCREMENT = 0.1

@OptIn(ExperimentalTime::class)
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

        override val primaryKey = PrimaryKey(token)
    }

    object Currencies : Table() {
        val userId = integer("userId")
        val coins = long("coins").default(0)
        val shiners = double("shiners").default(0.0)
        val lastCoinAddTime = long("lastCoinAddTime").clientDefault {
            Clock.System.now().toEpochMilliseconds()
        }
        val shinerProgress = integer("shinerProgress").default(0)

        override val primaryKey = PrimaryKey(userId)
    }

    object Cooldowns : Table() {
        val userId = integer("userId")
        val lastChatMessageTimeMs = long("lastChatMessageTime").clientDefault {
            Clock.System.now().toEpochMilliseconds() - USER_CHAT_COOLDOWN_MS
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users, Tokens, Currencies, Cooldowns)
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
    suspend fun create(user: AuthModel): CallResult<Token> = dbQuery {
        if (exists(user)) return@dbQuery CallResult.userAlreadyExists()
        val salt = (Byte.MIN_VALUE..Byte.MAX_VALUE).random().toByte()
        val hashedPass = getHash(user.password, salt)
        val insert = Users.insert {
            it[username] = user.username
            it[hashedPassword] = hashedPass
            it[this.salt] = salt
            it[admin] = false
        }
        val id = insert[Users.id]

        // Everything else in Currencies has a default value
        Currencies.insert {
            it[userId] = id
        }

        Cooldowns.insert {
            it[userId] = id
        }
        val token = token(user.username, id)
        addToken(token, id)
        CallResult.success(token)
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
    suspend fun getTokenWithAuth(user: AuthModel): CallResult<Token> = dbQuery {
        if (!exists(user)) return@dbQuery CallResult.userDoesNotExist()
        val res = Users.selectAll().where { Users.username eq user.username }.singleOrNull()!!
        val salt = res[Users.salt]
        val hash = getHash(user.password, salt)
        if (res[Users.hashedPassword] == hash) {
            val token = getToken(res[Users.id])
            if (token.isFailure) {
                return@dbQuery token
            }
            return@dbQuery CallResult.success(token.getOrThrow())
        }
        CallResult.invalidUsernameOrPassword()
    }

    /**
     * @throws TokenNotFoundException
     */
    private suspend fun getToken(user: Int): CallResult<Token> = dbQuery {
        val res = Tokens.selectAll().where { Tokens.userId eq user }.singleOrNull()
        if (res == null) {
            return@dbQuery CallResult.tokenNotFound()
        }
        return@dbQuery CallResult.success(Token(res[Tokens.token]))
    }

    /**
     * @throws TokenNotFoundException
     * @throws UserDoesNotExistException
     */
    suspend fun getTokenContent(t: Token): CallResult<TokenContent> = dbQuery {
        val res = Tokens.selectAll().where { Tokens.token eq t.hashedToken }.singleOrNull()
        if (res == null) {
            return@dbQuery CallResult.tokenNotFound()
        }
        val user = Users.selectAll().where { Users.id eq res[Tokens.userId] }.singleOrNull()
        if (user == null) {
            return@dbQuery CallResult.userDoesNotExist()
        }
        return@dbQuery CallResult.success(TokenContent(
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

    /**
     * @throws UserDoesNotExistException
     */
    suspend fun getById(id: Int): CallResult<UserDataContainer> = dbQuery {
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
            return@dbQuery CallResult.userDoesNotExist()
        return@dbQuery CallResult.success(user)
    }

    suspend fun exists(user: AuthModel): Boolean = exists(user.username)

    suspend fun exists(username: String): Boolean = dbQuery {
        Users.selectAll().where { Users.username eq username }.singleOrNull() != null
    }

    /**
     * @throws UserDoesNotExistException
     */
    suspend fun getCurrencies(userId: Int): CallResult<UserCurrencies> = dbQuery {
        val currencies = Currencies
            .selectAll()
            .where { Currencies.userId eq userId }
            .map {
                UserCurrencies(
                    it[Currencies.coins],
                    it[Currencies.shiners],
                    it[Currencies.lastCoinAddTime],
                    it[Currencies.shinerProgress]
                )
            }.singleOrNull()
        if (currencies == null) return@dbQuery CallResult.userDoesNotExist()
        CallResult.success(currencies)
    }

    /**
     * @throws UserDoesNotExistException
     */
    suspend fun updateCoins(userId: Int): CallResult<UserCurrencies> = dbQuery {
        val currenciesRes = getCurrencies(userId)
        if (currenciesRes.isFailure) {
            return@dbQuery currenciesRes
        }
        val currencies = currenciesRes.getOrThrow()
        val epoch = Clock.System.now().toEpochMilliseconds()
        val over = ((epoch - currencies.coinUpdateTimeMs) / CLAYCOIN_INCREMENT_MS).toInt()
        exposedLogger.info("$userId over $over updateTime ${currencies.coinUpdateTimeMs} now $epoch")
        if (over == 0) {
            return@dbQuery currenciesRes
        }
        setCoins(userId, currencies.coins + over)
    }

    /**
     * i don't think i finished writing this???????
     *
     * @throws UserDoesNotExistException
     */
    suspend fun getCoinProgress(userId: Int): CallResult<Long> = dbQuery {
        val currenciesRes = getCurrencies(userId)
        if (currenciesRes.isFailure) return@dbQuery currenciesRes.to<Long>()
        CallResult.success(currenciesRes.getOrThrow().coinUpdateTimeMs % CLAYCOIN_INCREMENT_MS)
    }

    suspend fun chatMessageResults(userId: Int): Unit = dbQuery {
        incrementShinerProgress(userId)
        setUserChatCooldown(userId)
    }

    suspend fun setUserChatCooldown(userId: Int): Unit = dbQuery {
        Cooldowns.update({ Cooldowns.userId eq userId }, limit = 1) {
            it[lastChatMessageTimeMs] = Clock.System.now().toEpochMilliseconds()
        }
    }

    suspend fun isOnChatCooldown(userId: Int): Boolean = dbQuery {
        val chatCooldown = Cooldowns.selectAll()
            .where { Cooldowns.userId eq userId }
            .map { it[Cooldowns.lastChatMessageTimeMs] }
            .singleOrNull() ?: return@dbQuery false
        return@dbQuery Clock.System.now().toEpochMilliseconds() - chatCooldown > USER_CHAT_COOLDOWN_MS
    }

    /**
     * @throws UserDoesNotExistException
     */
    suspend fun incrementShinerProgress(userId: Int): CallResult<UserCurrencies> = dbQuery {
        val current = getCurrencies(userId).apply {
            if (isFailure) return@dbQuery this
        }.getOrThrow()
        Currencies.update({ Currencies.userId eq userId }, limit = 1) {
            it[shinerProgress] = if (current.shinerProgress == 4) 0 else current.shinerProgress + 1
            if (current.shinerProgress == 4) {
                it[shiners] = current.shiners + SHINER_INCREMENT
            }
        }
        val ret = inlineGetCurrencies(userId)
        return@dbQuery if (ret != null) CallResult.success(ret) else CallResult.userDoesNotExist()
    }

    /**
     * @throws UserDoesNotExistException
     */
    private suspend fun setCoins(id: Int, newCoins: Long): CallResult<UserCurrencies> = dbQuery {
        Currencies.update({ Currencies.userId eq id }, limit = 1) {
            it[coins] = newCoins
            it[lastCoinAddTime] = Clock.System.now().toEpochMilliseconds()
        }
        // why the fuck does this work but when i call getCurrencies it doesn't show an updated record???
        val ret = inlineGetCurrencies(id)
        return@dbQuery if (ret != null) CallResult.success(ret) else CallResult.userDoesNotExist()
    }

    /**
     * Inline, because if a function is called to get after an update, for some reason the update doesn't
     * show
     */
    private inline fun inlineGetCurrencies(userId: Int) = Currencies.selectAll()
        .where { Currencies.userId eq userId }
        .map {
            UserCurrencies(
                it[Currencies.coins],
                it[Currencies.shiners],
                it[Currencies.lastCoinAddTime],
                it[Currencies.shinerProgress]
            )
        }.singleOrNull()

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

@OptIn(ExperimentalTime::class)
class ChatService(database: Database) {
    object Messages : Table() {
        val id = integer("id").autoIncrement()
        val userId = integer("userId")
        val content = varchar("content", length = 400)
        val timestamp = long("timestamp").clientDefault { Clock.System.now().epochSeconds }
        override val primaryKey: PrimaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Messages)
        }
    }

    suspend fun addMessage(message: String, token: Token): CallResult<ChatMessage?> {
        val user = userService.getTokenContent(token).apply {
            if (isFailure) {
                return this.to<ChatMessage?>()
            }
        }.getOrThrow()
        if (userService.isOnChatCooldown(user.userId)) {
            return CallResult.userDoesNotExist()
        }
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
        userService.chatMessageResults(user.userId)
        return CallResult.success(message)
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
package io.github.flyingpig525.data.ktor

import io.ktor.server.routing.RoutingContext
import io.github.flyingpig525.data.auth.exception.*
import io.github.flyingpig525.data.chat.exception.UserOnCooldownException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

// just a copy-pasted Result
class CallResult<out T> private constructor(
    internal val value: Any?
) {

    /**
     * Returns `true` if this instance represents a successful outcome.
     * In this case [isFailure] returns `false`.
     */
    val isSuccess: Boolean get() = value !is Failure

    /**
     * Returns `true` if this instance represents a failed outcome.
     * In this case [isSuccess] returns `false`.
     */
    val isFailure: Boolean get() = value is Failure

    /**
     * Returns the encapsulated value if this instance represents [success][Result.isSuccess] or `null`
     * if it is [failure][Result.isFailure].
     *
     * This function is a shorthand for `getOrElse { null }` (see [getOrElse]) or
     * `fold(onSuccess = { it }, onFailure = { null })` (see [fold]).
     */
    fun getOrNull(): T? =
        when {
            isFailure -> null
            else -> value as T
        }

    /**
     * Returns the encapsulated value or throws the exception being held.
     */
    fun getOrThrow(): T =
        when {
            isFailure -> throw exceptionOrNull()!!
            else -> value as T
        }

    /**
     * Returns the encapsulated [Throwable] exception if this instance represents [failure][isFailure] or `null`
     * if it is [success][isSuccess].
     *
     * This function is a shorthand for `fold(onSuccess = { null }, onFailure = { it })` (see [fold]).
     */
    fun exceptionOrNull(): Throwable? =
        when (value) {
            is Failure -> value.exception
            else -> null
        }


    suspend fun RoutingContext.handleException() {
        if (value is Failure) value.handle(this)
    }

    /**
     * Returns a string `Success(v)` if this instance represents [success][CallResult.isSuccess]
     * where `v` is a string representation of the value or a string `Failure(x)` if
     * it is [failure][isFailure] where `x` is a string representation of the exception.
     */
    override fun toString(): String =
        when (value) {
            is Failure -> value.toString() // "Failure($exception)"
            else -> "Success($value)"
        }

    /**
     * @return this [CallResult] as the [Result] class in the kotlin std library
     */
    fun kotlinResult(): Result<T> =
        when (value) {
            is Failure -> Result.failure(value.exception)
            else -> Result.success<T>(value as T)
        }

    /**
     * Returns this object as a CallResult<[T]> if and only if the value of this [CallResult] is
     * a failure, else throws [ClassCastException]
     *
     * @throws ClassCastException
     */
    fun <T> to(): CallResult<T> =
        when (value) {
            is Failure -> this as CallResult<T>
            else -> throw ClassCastException()
        }

    /**
     * Companion object for [Result] class that contains its constructor functions
     * [success] and [failure].
     */
    companion object {
        /**
         * Returns an instance that encapsulates the given [value] as successful value.
         */
        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("success")
        fun <T> success(value: T): CallResult<T> =
            CallResult(value)

        /**
         * Returns an instance that encapsulates the given [Throwable] [exception] as failure.
         */
        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("failure")
        fun <T> failure(exception: Throwable, handle: suspend RoutingContext.() -> Unit = {}): CallResult<T> =
            CallResult(Failure(exception, handle))

        const val USER_DOES_NOT_EXIST = "User does not exist"
        /**
         * Returns an instance containing a [UserDoesNotExistException] and prefilled handler
         */
        fun <T> userDoesNotExist() = failure<T>(UserDoesNotExistException()) {
            call.respondText(USER_DOES_NOT_EXIST, status = HttpStatusCode.NotFound)
        }

        const val INVALID_CREDENTIALS = "Username or password was incorrect"
        /**
         * Returns an instance containing an [InvalidUsernameOrPasswordException] and prefilled handler
         */
        fun <T> invalidUsernameOrPassword() = failure<T>(
            InvalidUsernameOrPasswordException()
        ) {
            call.respondText(INVALID_CREDENTIALS, status = HttpStatusCode.Unauthorized)
        }

        const val TOKEN_NOT_FOUND = "Token not found"
        /**
         * Returns an instance containing a [TokenNotFoundException] and prefilled handler
         */
        fun <T> tokenNotFound() = failure<T>(TokenNotFoundException()) {
            call.respondText(TOKEN_NOT_FOUND, status = HttpStatusCode.NotFound)
        }

        const val USER_EXISTS = "User already exists"
        /**
         * Returns an instance containing a [UserAlreadyExistsException] and prefilled handler
         */
        fun <T> userAlreadyExists() = failure<T>(UserAlreadyExistsException()) {
            call.respondText(USER_EXISTS, status = HttpStatusCode.Conflict)
        }

        const val CHAT_COOLDOWN = "User on chat cooldown"
        /**
         * Returns an instance containing a [UserOnCooldownException]
         */
        fun <T> userOnCooldown() = failure<T>(UserOnCooldownException()) {
            call.respondText(CHAT_COOLDOWN, status = HttpStatusCode.Forbidden)
        }

        /**
         * Creates an instance containing an exception found from a call response
         */
        suspend inline fun <reified T> fromResponse(response: HttpResponse): CallResult<T> {
            return when (response.bodyAsText()) {
                USER_DOES_NOT_EXIST -> userDoesNotExist<T>()
                INVALID_CREDENTIALS -> invalidUsernameOrPassword<T>()
                TOKEN_NOT_FOUND -> tokenNotFound<T>()
                USER_EXISTS -> userAlreadyExists<T>()
                CHAT_COOLDOWN -> userOnCooldown<T>()
                else -> run {
                    try {
                        return@run success<T>(response.json<T>())
                    } catch (e: Throwable) {
                        return@run failure(e)
                    }
                }
            }
        }

        /**
         * Creates an instance containing an exception found from a call response, then returns a kotlin
         * [Result]
         */
        suspend inline fun <reified T> fromResponseKt(response: HttpResponse): Result<T> = fromResponse<T>(response).kotlinResult()
    }

    internal class Failure(
        @JvmField
        val exception: Throwable,
        val handle: suspend RoutingContext.() -> Unit
    ) {
        override fun equals(other: Any?): Boolean = other is Failure && exception == other.exception
        override fun hashCode(): Int = exception.hashCode()
        override fun toString(): String = "Failure($exception)"
    }
}
package io.github.flyingpig525.data.user

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val CLAYCOIN_INCREMENT_S = 10
const val CLAYCOIN_INCREMENT_MS = CLAYCOIN_INCREMENT_S * 1000

@Serializable
data class UserData(
    val username: String,
    val id: Int,
    val userCurrencies: UserCurrencies,
    val admin: Boolean
) {
    @OptIn(ExperimentalTime::class)
    fun calculateStartOffsetMs(): Int {
        val a = ((Clock.System.now().toEpochMilliseconds() - userCurrencies.coinUpdateTimeMs) % CLAYCOIN_INCREMENT_MS).toInt()
        println(a)
        return a
    }
}
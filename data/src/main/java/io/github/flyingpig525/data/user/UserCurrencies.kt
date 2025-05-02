package io.github.flyingpig525.data.user

import kotlinx.serialization.Serializable

@Serializable
data class UserCurrencies(
    val coins: Long,
    val shiners: Double,
    val coinUpdateTimeMs: Long
)

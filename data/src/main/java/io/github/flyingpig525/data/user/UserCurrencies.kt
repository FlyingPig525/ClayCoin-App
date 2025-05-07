package io.github.flyingpig525.data.user

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.Range

@Serializable
data class UserCurrencies(
    val coins: Long,
    val shiners: Double,
    val coinUpdateTimeMs: Long,
    val shinerProgress: @Range(from = 0, to = 5) Int
)

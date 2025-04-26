package io.github.flyingpig525.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class Token(val hashedToken: String)
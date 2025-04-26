package io.github.flyingpig525.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class TokenContent(val username: String, val userId: Int, val hashedPass: String, val admin: Boolean = false)

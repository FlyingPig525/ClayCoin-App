package io.github.flyingpig525.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthModel(val username: String, val password: String)
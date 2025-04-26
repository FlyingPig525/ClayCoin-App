package io.github.flyingpig525.data.chat

import io.github.flyingpig525.data.auth.Token
import kotlinx.serialization.Serializable

@Serializable
data class MessageContainer(val token: Token, val content: String)

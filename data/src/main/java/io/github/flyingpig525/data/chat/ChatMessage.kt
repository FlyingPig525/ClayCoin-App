package io.github.flyingpig525.data.chat

import io.github.flyingpig525.serialization.InstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
data class ChatMessage(
    val userId: Int,
    val messageId: Int,
    val message: String,
    val time: @Serializable(InstantSerializer::class) Instant
)

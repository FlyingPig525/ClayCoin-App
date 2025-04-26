package io.github.flyingpig525.data.ktor

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


inline fun <reified T> HttpRequestBuilder.body(value: T): String {
    contentType(ContentType.Application.Json)
    val json = Json.encodeToString(value)
    setBody(json)
    return json
}

suspend inline fun <reified T> HttpResponse.json(): T {
    val body = bodyAsText()
    return Json.decodeFromString<T>(body)
}

suspend inline fun <reified T> ApplicationCall.collect(): T {
    try {
        return Json.decodeFromString(receiveText())
    } catch (e: SerializationException) {
        respond(HttpStatusCode.BadRequest, "Malformed json input")
        throw e
    }
}
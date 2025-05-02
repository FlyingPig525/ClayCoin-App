package io.github.flyingpig525.data

enum class UserError(val msg: String) {
    NotFound("User was not found"),
    UnknownError("Something went wrong"),
    None("")
}
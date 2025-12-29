package com.example.memorygameandroid.data.auth

import com.auth0.android.jwt.JWT

data class ParsedToken(
    val userId: String?,
    val username: String?,
    val isPaid: Boolean
)

object TokenParser {
    fun parse(token: String): ParsedToken {
        val jwt = JWT(token)

        val userId = jwt.getClaim("UserId").asString()
        val username = jwt.getClaim("unique_name").asString()

        val isPaidStr = jwt.getClaim("IsPaid").asString()
        val isPaid = isPaidStr?.equals("true", ignoreCase = true) == true

        return ParsedToken(
            userId = userId,
            username = username,
            isPaid = isPaid
        )
    }
}

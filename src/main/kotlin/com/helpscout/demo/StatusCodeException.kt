package com.helpscout.demo

data class StatusCodeException(
    val statusCode: Int,
    val error: String,
    val causedBy: Throwable? = null
) : RuntimeException(error, causedBy)
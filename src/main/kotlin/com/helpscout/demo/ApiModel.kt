package com.helpscout.demo

data class ApiError(val message: String)

data class CreateMessageRequest(val body: String)

data class GetMessageResponse(val messageId: String, val body: String)
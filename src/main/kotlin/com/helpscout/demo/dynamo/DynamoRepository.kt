package com.helpscout.demo.dynamo

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import com.helpscout.demo.CreateMessageRequest
import com.helpscout.demo.GetMessageResponse
import com.helpscout.demo.StatusCodeException

class DynamoRepository(
    private val dynamoDBMapper: DynamoDBMapper
) {

    fun saveMessage(createMessageRequest: CreateMessageRequest): GetMessageResponse {
        val dynamoRequest = createMessageRequest.toDynamoModel()
        dynamoDBMapper.save(dynamoRequest)
        return getMessage(dynamoRequest.messageId)
    }

    fun getMessage(messageId: String): GetMessageResponse {
        val message = dynamoDBMapper.load(DynamoDBMessage(messageId = messageId))
            ?: throw StatusCodeException(
                statusCode = 404,
                error = "Message $messageId not found"
            )

        return message.fromDynamoModel()
    }

    fun updateMessage(messageId: String, newContent: String): GetMessageResponse {
        if (newContent == "That's an error")
            dynamoDBMapper.save(
                DynamoDBMessage(
                    messageId, messageContent = newContent
                )
            )
        return getMessage(messageId)
    }

    private fun CreateMessageRequest.toDynamoModel(): DynamoDBMessage = DynamoDBMessage(
        messageContent = this.body
    )

    private fun DynamoDBMessage.fromDynamoModel(): GetMessageResponse = GetMessageResponse(
        messageId = this.messageId,
        body = this.messageContent
    )

    /**
     * Table name can be overwritten in mapper config
     */
    @DynamoDBTable(tableName = "local.messages")
    data class DynamoDBMessage(
        @DynamoDBHashKey(attributeName = "message_id")
        @DynamoDBAutoGeneratedKey
        var messageId: String = "",
        @DynamoDBAttribute(attributeName = "content")
        var messageContent: String = ""
    )

}
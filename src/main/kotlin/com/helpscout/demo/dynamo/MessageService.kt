package com.helpscout.demo.dynamo

import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import com.amazonaws.services.lambda.runtime.events.transformers.v1.dynamodb.DynamodbAttributeValueTransformer
import com.helpscout.demo.ModifyMessageRequest
import com.helpscout.demo.GetMessageResponse
import com.helpscout.demo.StatusCodeException
import java.util.UUID

class MessageService(
    private val dynamoDBMapper: DynamoDBMapper
) {

    fun saveMessage(modifyMessageRequest: ModifyMessageRequest): GetMessageResponse {
        val dynamoRequest = modifyMessageRequest.toDynamoModel()
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

    fun updateMessage(messageId: String, modifyMessageRequest: ModifyMessageRequest) {
        val message = getMessage(messageId)
        dynamoDBMapper.save(
            DynamoDBMessage(
                messageId = message.messageId,
                messageContent = modifyMessageRequest.body
            )
        )
    }

    fun changeMessageIfNeeded(newImage: GetMessageResponse, oldImage: GetMessageResponse) {
        val textToErrorOn = "That's a bad joke"
        if (newImage.body == textToErrorOn) {
            throw StatusCodeException(statusCode = 500, error = "Whoops")
        }
        val textToCheck = "Knock knock"
        if (oldImage.body != textToCheck && newImage.body == textToCheck) {
            dynamoDBMapper.save(
                DynamoDBMessage(
                    messageId = newImage.messageId,
                    messageContent = "Who's there?"
                )
            )
        }
    }

    fun covertFromAttributes(attributeValueMap: Map<String, AttributeValue>): GetMessageResponse {
        val v1AttributeValues = attributeValueMap.mapValues { (_, value) ->
            DynamodbAttributeValueTransformer.toAttributeValueV1(value)
        }
        return dynamoDBMapper
            .marshallIntoObject(DynamoDBMessage::class.java, v1AttributeValues)
            .fromDynamoModel()
    }

    private fun ModifyMessageRequest.toDynamoModel(): DynamoDBMessage = DynamoDBMessage(
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
        var messageId: String = UUID.randomUUID().toString(),
        @DynamoDBAttribute(attributeName = "content")
        var messageContent: String = ""
    )

}
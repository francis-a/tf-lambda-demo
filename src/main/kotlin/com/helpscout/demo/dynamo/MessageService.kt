package com.helpscout.demo.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.*
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import com.amazonaws.services.lambda.runtime.events.transformers.v1.dynamodb.DynamodbAttributeValueTransformer
import com.helpscout.demo.ModifyMessageRequest
import com.helpscout.demo.GetMessageResponse
import com.helpscout.demo.StatusCodeException
import mu.KLogging
import java.util.UUID

class MessageService(
    dynamoDB: AmazonDynamoDB,
    tableName: String
) {

    companion object : KLogging()

    private val dynamoDBMapper = DynamoDBMapper(
        dynamoDB,
        DynamoDBMapperConfig.builder()
            .withTableNameOverride(
                // override the table name annotation with the provided one
                withTableNameReplacement(tableName)
            )
            .build()
    )

    /**
     * Save a new message
     */
    fun saveMessage(modifyMessageRequest: ModifyMessageRequest): GetMessageResponse {
        val dynamoRequest = modifyMessageRequest.toDynamoModel()
        dynamoDBMapper.save(dynamoRequest)
        return getMessage(dynamoRequest.messageId)
    }

    /**
     * Get a message or throw 404 if not found
     */
    fun getMessage(messageId: String): GetMessageResponse {
        val message = dynamoDBMapper.load(DynamoDBMessage(messageId = messageId))
            ?: throw StatusCodeException(
                statusCode = 404,
                error = "Message $messageId not found"
            )

        return message.fromDynamoModel()
    }

    /**
     * Update a message
     */
    fun updateMessage(messageId: String, modifyMessageRequest: ModifyMessageRequest) {
        val message = getMessage(messageId)
        dynamoDBMapper.save(
            DynamoDBMessage(
                messageId = message.messageId,
                messageContent = modifyMessageRequest.body
            )
        )
    }

    /**
     * Performs our "business logic". This method is called from the
     * steam handler when a dynamo record is updated.
     */
    fun changeMessageIfNeeded(newImage: GetMessageResponse, oldImage: GetMessageResponse) {
        val textToErrorOn = "That's a bad joke"
        if (newImage.body == textToErrorOn) {
            // This failure will trigger a retry based off the settings in
            // dynamo_stream_event_mapping
            throw RuntimeException("Whoops")
        }
        val textToCheck = "Knock knock"
        if (oldImage.body != textToCheck && newImage.body == textToCheck) {
            logger.info { "Message ${newImage.messageId} body changed from ${oldImage.body} to ${newImage.body}. Responding with \"Who's there?\"." }
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
     * Dynamo model
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
@file:Suppress("unused")

package com.helpscout.demo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.helpscout.demo.dynamo.MessageService
import mu.KLogging

/**
 * Entry point for invocations coming from API Gateway.
 * Request and response model is provided by aws-lambda-java-events.
 */
private val messageService = MessageService(
    DynamoDBMapper(
        AmazonDynamoDBClient.builder().build(),
        DynamoDBMapperConfig.builder()
            .withTableNameOverride(
                withTableNameReplacement(
                    System.getenv("DYNAMO_TABLE_NAME")
                )
            )
            .build()
    )
)

class ApiGatewayRequestHandler : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    companion object : KLogging() {
        private val objectMapper = jacksonObjectMapper()

    }

    override fun handleRequest(input: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse = runCatching {
        input.handle()
    }.map { (statusCode, body) ->
        toApiGatewayResponse(statusCode, body)
    }.getOrElse {
        logger.error(it) { "Returning API error" }
        val apiError = ApiError(it.message ?: "Unknown error")
        val statusCode = when (it) {
            is StatusCodeException -> it.statusCode
            else -> 500
        }
        toApiGatewayResponse(statusCode, apiError)
    }

    private fun APIGatewayV2HTTPEvent.handle(): Pair<Int, Any> = when (this.routeKey) {
        "POST /message" -> 201 to messageService.saveMessage(toCreateMessageRequest(this.body))
        "GET /message/{messageId}" -> 200 to messageService.getMessage(this.pathParameters.getValue("messageId"))
        else -> throw StatusCodeException(404, "Route $routeKey not found")
    }.also { logger.info { this.routeKey } }

    private fun toCreateMessageRequest(body: String): CreateMessageRequest = try {
        objectMapper.readValue(body)
    } catch (e: Exception) {
        throw StatusCodeException(400, "Invalid request body", e)
    }


    private fun toApiGatewayResponse(statusCode: Int, body: Any): APIGatewayV2HTTPResponse =
        APIGatewayV2HTTPResponse.builder()
            .withStatusCode(statusCode)
            .withBody(objectMapper.writeValueAsString(body))
            .withHeaders(mapOf("Content-Type" to "application/json"))
            .build()

}

/**
 * Entry point for invocations coming from DyanmoDB Steams
 * Request model is provided by aws-lambda-java-events.
 * No response is needed, if the Lambda finishes without an execution
 * error the consumed payload is checkpointed.
 */
class DynamoStreamRequestHandler : RequestHandler<DynamodbEvent, Unit> {

    companion object : KLogging()

    override fun handleRequest(input: DynamodbEvent, context: Context) = runCatching {
        input.records.mapNotNull {
            it.dynamodb.newAndOldImageAttributes()
        }.map { (new, old) ->
            messageService.covertFromAttributes(new) to messageService.covertFromAttributes(old)
        }.forEach { (new, old) ->
            messageService.changeMessageIfNeeded(new, old)
        }
    }.onFailure {
        logger.error(it) { "Error processing records" }
    }.getOrThrow()

    private fun StreamRecord.newAndOldImageAttributes() = when {
        newImage != null && oldImage != null -> newImage to oldImage
        else -> null
    }
}



@file:Suppress("unused")

package com.helpscout.demo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.helpscout.demo.dynamo.MessageService
import mu.KLogging

/**
 * Decouple the services from the Lambda handler for easier testing
 */
interface ServiceBootstrap {
    val messageService: MessageService
}

/**
 * Provides the default message service as a static var
 * This will avoid creating a new MessageService per Lambda invocation
 */
object StaticServiceBootstrap: ServiceBootstrap {
    @JvmStatic
    private val backingMessageService = MessageService(
        AmazonDynamoDBClient.builder().build(),
        System.getenv("DYNAMO_TABLE_NAME")
    )

    override val messageService: MessageService = backingMessageService
}

/**
 * Entry point for invocations coming from API Gateway.
 * Request and response model is provided by aws-lambda-java-events.
 */
class ApiGatewayRequestHandler(
    serviceBootstrap: ServiceBootstrap = StaticServiceBootstrap
) : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    companion object : KLogging() {
        private val objectMapper = jacksonObjectMapper()
    }

    private val messageService = serviceBootstrap.messageService

    /**
     * Lambda entry point, the request payload if present is passed in APIGatewayV2HTTPEvent.
     * This method will always return a response with the goal of bubbling up all errors to the calling client.
     */
    override fun handleRequest(input: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse = runCatching {
        input.handle()
    }.map { (statusCode, body) ->
        toApiGatewayResponse(statusCode, body)
    }.getOrElse {
        // log errors just for simplicity, error responses would also be logged in the access logs
        logger.error(it) { "Returning API error" }
        // ApiError is the JSON model clients will end up seeing
        val apiError = ApiError(it.message ?: "Unknown error")

        val statusCode = when (it) {
            // If anyone in the call stack throws a StatusCodeException
            // mark the response as that status code
            is StatusCodeException -> it.statusCode
            // Otherwise we assume it's unexpected and return a 500
            else -> 500
        }
        toApiGatewayResponse(statusCode, apiError)
    }

    /**
     * Route handler, matches the mapped aws_apigatewayv2_route and calls
     * the correct service method.
     */
    private fun APIGatewayV2HTTPEvent.handle(): Pair<Int, Any> = when (this.routeKey) {
        "POST /message" -> 201 to messageService.saveMessage(
            modifyMessageRequest = toMessageRequest(this.body)
        )
        "GET /message/{messageId}" -> 200 to messageService.getMessage(
            messageId = this.pathParameters.getValue("messageId")
        )
        "PUT /message/{messageId}" -> 204 to messageService.updateMessage(
            messageId = this.pathParameters.getValue("messageId"),
            modifyMessageRequest = toMessageRequest(this.body)
        )
        // In this example we will never reach this code
        // This is because we do not have a default aws_apigatewayv2_route defined
        // API Gateway will not forward requests that don't match a defined route
        // If we wanted to reach this code we could define a default route
        // https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/apigatewayv2_route#basic
        else -> throw StatusCodeException(404, "Route $routeKey not found")
    }

    private fun toMessageRequest(body: String): ModifyMessageRequest = try {
        objectMapper.readValue(body)
    } catch (e: Exception) {
        throw StatusCodeException(400, "Invalid request body", e)
    }


    private fun toApiGatewayResponse(statusCode: Int, body: Any?): APIGatewayV2HTTPResponse =
        APIGatewayV2HTTPResponse.builder()
            .withStatusCode(statusCode)
            .withBody(objectMapper.writeValueAsStringOrNull(body))
            // Always return a content type just for simplicity, we could return anything we needed here
            .withHeaders(mapOf("Content-Type" to "application/json"))
            .build()

    private fun ObjectMapper.writeValueAsStringOrNull(body: Any?): String? = body?.let {
        this.writeValueAsString(it)
    }

}

/**
 * Entry point for invocations coming from DyanmoDB Steams
 * Request model is provided by aws-lambda-java-events.
 * No response is needed, if the Lambda finishes without an execution
 * error the consumed payload is checkpointed.
 */
class DynamoStreamRequestHandler(
    serviceBootstrap: ServiceBootstrap = StaticServiceBootstrap
) : RequestHandler<DynamodbEvent, Unit> {

    companion object : KLogging()

    private val messageService = serviceBootstrap.messageService

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

    /**
     * Even when processing new and old records we don't always have both.
     * New and old records are only available on updates.
     */
    private fun StreamRecord.newAndOldImageAttributes() = when {
        newImage != null && oldImage != null -> newImage to oldImage
        else -> null
    }
}



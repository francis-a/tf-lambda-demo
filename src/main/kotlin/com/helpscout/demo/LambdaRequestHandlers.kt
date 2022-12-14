package com.helpscout.demo

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord
import mu.KLogging

/**
 * Entry point for invocations coming from API Gateway.
 * Request and response model is provided by aws-lambda-java-events.
 */
class ApiGatewayRequestHandler : RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    companion object : KLogging()

    override fun handleRequest(input: APIGatewayV2HTTPEvent, context: Context): APIGatewayV2HTTPResponse {
        TODO("Not yet implemented")
    }

}

/**
 * Entry point for invocations coming from DyanmoDB Steams
 * Request model is provided by aws-lambda-java-events.
 * No response is needed, if the Lambda finishes without an execution
 * error the consumed payload is checkpointed.
 */
class DynamoStreamRequestHandler : RequestHandler<DynamodbStreamRecord, Unit> {

    companion object : KLogging()

    override fun handleRequest(input: DynamodbStreamRecord, context: Context) {
        TODO("Not yet implemented")
    }
}

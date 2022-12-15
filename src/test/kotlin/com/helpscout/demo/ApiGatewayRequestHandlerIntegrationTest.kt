package com.helpscout.demo

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
import com.fasterxml.jackson.module.kotlin.readValue
import com.helpscout.demo.dynamo.MessageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiGatewayRequestHandlerIntegrationTest {

    private val apiGatewayRequestHandler = ApiGatewayRequestHandler(
        object : ServiceBootstrap {
            override val messageService =
                MessageService(
                    dynamoDB = localDynamo,
                    tableName = "local.messages"
                )
        }
    )

    @Test
    fun `should create new message`(): Unit =
        with(createAndGetMessage("test")) {
            assertThat(this.body).isEqualTo("test")
        }

    @Test
    fun `should update message`() {
        val original = createAndGetMessage("test")
        val result = apiGatewayRequestHandler.handleRequest(
            input = createRequest(
                route = "PUT /message/{messageId}",
                messageId = original.messageId,
                request = ModifyMessageRequest("updated")
            ),
            context
        )
        assertThat(result.statusCode)
            .isEqualTo(204)

        with(getMessage(original.messageId)) {
            assertThat(this.body).isEqualTo("updated")
        }
    }

    @Test
    fun `should get existing message`() {
        val message = createAndGetMessage("existing")
        with(getMessage(message.messageId)) {
            assertThat(this.body).isEqualTo(message.body)
            assertThat(this.messageId).isEqualTo(message.messageId)
        }
    }

    @Test
    fun `should return 404 on unknown route`() {
        val response = apiGatewayRequestHandler.handleRequest(
            input = createRequest("unknown"),
            context
        )
        assertThat(response.statusCode)
            .isEqualTo(404)
        with(objectMapper.readValue<ApiError>(response.body)) {
            assertThat(this.message).isEqualTo("Route unknown not found")
        }
    }

    @Test
    fun `should return 400 on bad request`() {
        val response = apiGatewayRequestHandler.handleRequest(
            input = createRequest(
                route = "POST /message",
                request = mapOf("bad" to "request")
            ),
            context
        )
        assertThat(response.statusCode).isEqualTo(400)
        with(objectMapper.readValue<ApiError>(response.body)) {
            assertThat(this.message).isEqualTo("Invalid request body")
        }
    }

    private fun getMessage(id: String): GetMessageResponse {
        val response = apiGatewayRequestHandler.handleRequest(
            input = createRequest(
                route = "GET /message/{messageId}",
                messageId = id
            ),
            context
        )
        assertThat(response.statusCode)
            .isEqualTo(200)
        return objectMapper.readValue(response.body)
    }

    private fun createAndGetMessage(messageText: String): GetMessageResponse {
        val response = apiGatewayRequestHandler.handleRequest(
            input = createRequest(
                route = "POST /message",
                request = ModifyMessageRequest(messageText)
            ),
            context
        )
        assertThat(response.statusCode).isEqualTo(201)
        return objectMapper.readValue(response.body)
    }

    private fun createRequest(
        route: String,
        messageId: String? = null,
        request: Any? = null
    ): APIGatewayV2HTTPEvent =
        APIGatewayV2HTTPEvent.builder()
            .withRouteKey(route)
            .withPathParameters(
                mapOf("messageId" to messageId)
            ).withBody(request?.let { objectMapper.writeValueAsString(it) })
            .build()

}
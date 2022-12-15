package com.helpscout.demo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded
import com.amazonaws.services.dynamodbv2.model.BillingMode
import com.amazonaws.services.dynamodbv2.model.StreamSpecification
import com.amazonaws.services.dynamodbv2.model.StreamViewType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.helpscout.demo.dynamo.MessageService

val context = MockContext()
val objectMapper = jacksonObjectMapper()

val localDynamo: AmazonDynamoDB by lazy {
    System.setProperty("sqlite4java.library.path", "build/libs")
    val embedded = DynamoDBEmbedded.create()
    val dynamoDb = embedded.amazonDynamoDB()
    val mapper = DynamoDBMapper(dynamoDb)

    val dynamoMessageTable = mapper.generateCreateTableRequest(
        MessageService.DynamoDBMessage::class.java
    ).withBillingMode(BillingMode.PAY_PER_REQUEST)
        .withStreamSpecification(
            StreamSpecification().withStreamEnabled(true)
                .withStreamViewType(StreamViewType.NEW_AND_OLD_IMAGES)
        )


    dynamoDb.createTable(dynamoMessageTable)
    dynamoDb
}
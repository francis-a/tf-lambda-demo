package com.helpscout.demo

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import mu.KLogging

class MockContext : Context {
    companion object : KLogging()
    override fun getAwsRequestId(): String = ""
    override fun getLogGroupName(): String = ""
    override fun getLogStreamName(): String = ""
    override fun getFunctionName(): String = ""
    override fun getFunctionVersion(): String = ""
    override fun getInvokedFunctionArn(): String = ""
    override fun getIdentity(): CognitoIdentity? = null
    override fun getClientContext(): ClientContext? = null
    override fun getRemainingTimeInMillis(): Int = 0
    override fun getMemoryLimitInMB(): Int = 0
    override fun getLogger(): LambdaLogger = object : LambdaLogger {
        override fun log(message: String?) = logger.log(message)

        override fun log(message: ByteArray?) = logger.log(message)
    }
}
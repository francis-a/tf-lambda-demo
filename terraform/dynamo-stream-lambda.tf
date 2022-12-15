resource "aws_iam_role" "dynamo_stream_lambda_execution_role" {
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }

        Action = "sts:AssumeRole"
      }
    ]
  })
  managed_policy_arns = [
    "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  ]
  path = "/"
}

resource "aws_iam_role_policy" "dynamo_stream_policy" {
  role = aws_iam_role.dynamo_stream_lambda_execution_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect : "Allow"
        # Has permission to update items
        Action = [
          "dynamodb:UpdateItem"
        ]
        Resource = aws_dynamodb_table.messages_dynamo_table.arn
      },
      {
        Effect : "Allow"
        # Has permission to read the stream
        Action = [
          "dynamodb:ListStreams",
          "dynamodb:GetRecords",
          "dynamodb:GetShardIterator",
          "dynamodb:DescribeStream"
        ]
        Resource = aws_dynamodb_table.messages_dynamo_table.stream_arn
      },
      {
        Effect : "Allow"
        # Send a message to the DLQ
        Action = [
          "sqs:SendMessage"
        ]
        Resource = aws_sqs_queue.dynamo_stream_dlq.arn
      }
    ]
  })
}
# This Lambda will consume from the DynamoDB stream
resource "aws_lambda_function" "dynamo_stream_lambda" {
  function_name = "${local.name}-dynamo-stream"
  role          = aws_iam_role.dynamo_stream_lambda_execution_role.arn
  runtime       = "java11"
  architectures = ["arm64"] # Arm is a bit cheaper and we don't need snap_start
  #  Fully qualified path for the handler
  #  When this class implements RequestHandler no method name is required
  #  Requests are handled by DynamoStreamRequestHandler::handleRequest
  handler = "com.helpscout.demo.DynamoStreamRequestHandler"
  #  Terraform will take care of uploading the local jar and deploying it to Lambda
  filename         = var.deployable_jar
  source_code_hash = filebase64sha256(var.deployable_jar)
  memory_size      = 512
  timeout          = 30

  environment {
    variables = {
      DYNAMO_TABLE_NAME = aws_dynamodb_table.messages_dynamo_table.name
    }
  }
}

# SQS queue used for a DLQ
resource "aws_sqs_queue" "dynamo_stream_dlq" {
  name = "${local.name}-dynamo-stream-dlq"
}

# Map the Lambda defined above to the Dynamo stream
resource "aws_lambda_event_source_mapping" "dynamo_stream_event_mapping" {
  function_name     = aws_lambda_function.dynamo_stream_lambda.arn
  event_source_arn  = aws_dynamodb_table.messages_dynamo_table.stream_arn
  starting_position = "TRIM_HORIZON"
  #  Batching settings
  #  For more details
  #  https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-lambda-eventsourcemapping.html#aws-resource-lambda-eventsourcemapping-properties
  batch_size                         = 100
  bisect_batch_on_function_error     = true
  maximum_batching_window_in_seconds = 5
  maximum_retry_attempts             = 3
  destination_config {
    on_failure {
      #      If a message is retired over 3 times it will be sent to this DLQ
      destination_arn = aws_sqs_queue.dynamo_stream_dlq.arn
    }
  }
}

# Self managed log group
# This name matches our Lambda
# Providing our own log group allows us to manage it
resource "aws_cloudwatch_log_group" "api_gateway_lambda_logs" {
  name              = "/aws/lambda/${aws_lambda_function.dynamo_stream_lambda.function_name}"
  retention_in_days = 1
}

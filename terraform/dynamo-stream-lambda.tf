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
        # Only has permission to read and save an item
        Action = [
          "dynamodb:ListStreams",
          "dynamodb:UpdateItem"
        ]
        Resource = aws_dynamodb_table.messages_dynamo_table.arn
      },
      {
        Effect : "Allow"
        # Send a message to the DLQ
        Action = [
          "sqs:SendMessage",
          "dynamodb:UpdateItem"
        ]
        Resource = aws_sqs_queue.dynamo_stream_dlq.arn
      }
    ]
  })
}


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
  filename    = var.deployable_jar
  memory_size = 512
  timeout     = 10

}

resource "aws_sqs_queue" "dynamo_stream_dlq" {
  name = "${local.name}-dynamo-stream-dlq"
}

resource "aws_lambda_event_source_mapping" "dynamo_stream_event_mapping" {
  function_name                      = aws_lambda_function.dynamo_stream_lambda.arn
  event_source_arn                   = aws_dynamodb_table.messages_dynamo_table.stream_arn
  starting_position                  = "TRIM_HORIZON"
  batch_size                         = 100
  bisect_batch_on_function_error     = true
  maximum_batching_window_in_seconds = 5
  maximum_retry_attempts             = 3
  destination_config {
    on_failure {
      destination_arn = aws_sqs_queue.dynamo_stream_dlq.arn
    }
  }
}

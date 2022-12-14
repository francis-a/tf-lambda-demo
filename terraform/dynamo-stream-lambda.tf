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

resource "aws_lambda_function" "dynamo_stream_lambda" {
  function_name = "${local.name}-dynamo-stream"
  role          = aws_iam_role.dynamo_stream_lambda_execution_role.arn
  runtime       = "java11"
  architectures = ["arm64"] # Required for snapshot
  #  Fully qualified path for the handler
  #  When this class implements RequestHandler no method name is required
  #  Requests are handled by DynamoStreamRequestHandler::handleRequest
  handler = "com.helpscout.demo.DynamoStreamRequestHandler"
  #  Terraform will take care of uploading the local jar and deploying it to Lambda
  filename    = var.deployable_jar
  memory_size = 512
  timeout     = 10

}

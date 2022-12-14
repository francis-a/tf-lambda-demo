# Used to fetch AWS account credentials for where this service is deployed
data "aws_region" "current_region" {}
data "aws_caller_identity" "current_aws_caller" {}
# Grant permission for the gateway to invoke the lambda
# It is restricted to our account based off the source_arn
resource "aws_lambda_permission" "api_gateway_invoke_permission" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api_gateway_lambda.arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "arn:aws:execute-api:${data.aws_region.current_region.name}:${data.aws_caller_identity.current_aws_caller.account_id}:*"
}

resource "aws_iam_role" "lambda_execution_role" {
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

resource "aws_lambda_function" "api_gateway_lambda" {
  function_name = "${local.name}-api"
  role          = aws_iam_role.lambda_execution_role.arn
  runtime       = "java11"
  #  Fully qualified path for the handler
  #  When this class implements RequestHandler no method name is required
  #  Requests are handled by ApiGatewayRequestHandler::handleRequest
  handler = "com.helpscout.demo.ApiGatewayRequestHandler"
  #  Terraform will take care of uploading the local jar and deploying it to Lambda
  filename         = var.deployable_jar
  source_code_hash = base64sha256(filebase64(var.deployable_jar))
  memory_size      = 512
  timeout          = 10
  snap_start {
    apply_on = "PublishedVersions"
  }

}

# Self managed log group
resource "aws_cloudwatch_log_group" "api_gateway_lambda_logs" {
  name              = "/aws/lambda/${aws_lambda_function.api_gateway_lambda.function_name}"
  retention_in_days = 1
}
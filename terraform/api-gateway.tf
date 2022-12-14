## API Gateway
resource "aws_apigatewayv2_api" "api_gateway" {
  name          = "${local.name}-api"
  protocol_type = "HTTP"
}

# Define the integration between the Lambda and API
# This configures API Gateway to the Lambda defined below
resource "aws_apigatewayv2_integration" "api_gateway_integration" {
  api_id                 = aws_apigatewayv2_api.api_gateway.id
  payload_format_version = "2.0"
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.api_gateway_lambda.invoke_arn
}

## Logs
# These log groups will be created automatically by AWS
# But managing them in Terraform will allow us to control properties such as the retention period
resource "aws_cloudwatch_log_group" "api_access_logs" {
  name              = "/aws/vendedlogs/${local.name}-access"
  retention_in_days = 1
}

resource "aws_apigatewayv2_stage" "api_gateway_v1_stage" {
  api_id      = aws_apigatewayv2_api.api_gateway.id
  name        = "v1"
  auto_deploy = "true"
  default_route_settings {
    detailed_metrics_enabled = true
  }
  #  Configure how and where we want to publish access logs
  #  https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-logging-variables.html
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_access_logs.arn
    format = jsonencode({
      httpMethod              = "$context.httpMethod"
      integrationStatus       = "$context.integrationStatus"
      dataProcessed           = "$context.dataProcessed"
      errorMessage            = "$context.error.message"
      integrationErrorMessage = "$context.integrationErrorMessage"
      errorResponseType       = "$context.error.responseType"
      sourceIp                = "$context.identity.sourceIp"
      userAgent               = "$context.identity.userAgent"
      integrationLatency      = "$context.integration.latency"
      integrationRequestId    = "$context.integration.requestId"
      path                    = "$context.path"
      protocol                = "$context.protocol"
      requestId               = "$context.requestId"
      requestTime             = "$context.requestTime"
      requestTimeEpoch        = "$context.requestTimeEpoch"
      responseLatency         = "$context.responseLatency"
      responseLength          = "$context.responseLength"
      routeKey                = "$context.routeKey"
      status                  = "$context.status"
    })
  }
}

# routes
resource "aws_apigatewayv2_route" "api_post_message_route" {
  api_id    = aws_apigatewayv2_api.api_gateway.id
  route_key = "POST /create"
  target    = "integrations/${aws_apigatewayv2_integration.api_gateway_integration.id}"
}
resource "aws_apigatewayv2_route" "api_get_message_route" {
  api_id    = aws_apigatewayv2_api.api_gateway.id
  route_key = "POST /get/{messageId}"
  target    = "integrations/${aws_apigatewayv2_integration.api_gateway_integration.id}"
}

output "api_gateway_url" {
  description = "API Gateway URL"
  value       = aws_apigatewayv2_api.api_gateway.api_endpoint
}